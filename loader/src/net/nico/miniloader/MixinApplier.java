package net.nico.miniloader;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

/**
 * 字节码织入，用 JDK 自带的 Class-File API（JEP 484，Java 24 转正）。
 * 对标 Fabric 的 Mixin + ASM。
 *
 * 支持两种织入：
 *  - INJECT  ：在 HEAD / RETURN / TAIL 处插入一次静态调用（对标 @Inject）。
 *  - REDIRECT：把目标方法里的某一次方法调用整体替换成我们的静态调用（对标 @Redirect）。
 *
 * 为什么不用 ASM：这台机器没 gradle/maven，而 Java 25 自带 Class-File API，
 * 零第三方依赖就能改字节码，编译和验证都干净。
 */
public final class MixinApplier {

    private static final ClassFile CLASS_FILE = ClassFile.of();

    private MixinApplier() {
    }

    public static byte[] apply(byte[] original, List<ResolvedMixin> mixins) {
        ClassModel model = CLASS_FILE.parse(original);
        ClassTransform combined = ClassTransform.ACCEPT_ALL;
        for (ResolvedMixin mixin : mixins) {
            CodeTransform codeTransform = mixin.kind() == MixinKind.REDIRECT
                ? redirector(mixin)
                : injector(mixin);
            combined = combined.andThen(ClassTransform.transformingMethodBodies(
                method -> method.methodName().stringValue().equals(mixin.methodName()),
                codeTransform));
        }
        return CLASS_FILE.transformClass(model, combined);
    }

    /**
     * INJECT：在指定位置插入 invokestatic。
     *
     * 为什么先缓冲再发射：HEAD 只需要看第一条元素，但 TAIL 需要知道「哪个 return 是最后一个」——
     * 流式遍历里先到的 return 未必是最后一个，边看边插会把 TAIL 插到倒数第二个出口上。
     * 所以 accept 阶段只收集元素，到 atEnd 拿到完整列表后再决定插点。
     *
     * 踩过的坑：必须用 ofStateful 每次拿全新实例。复用同一个实例时，缓冲列表
     * 会跨方法串味，第二个方法就会拿到第一个的元素。
     */
    private static CodeTransform injector(ResolvedMixin mixin) {
        ClassDesc owner = ClassDesc.of(mixin.handlerClass());
        String name = mixin.handlerMethod();
        MethodTypeDesc type = MethodTypeDesc.of(ConstantDescs.CD_void);

        return CodeTransform.ofStateful(() -> new CodeTransform() {
            private final List<CodeElement> buffered = new java.util.ArrayList<>();

            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                buffered.add(element);
            }

            @Override
            public void atEnd(CodeBuilder builder) {
                int lastReturn = -1;
                if (mixin.point() == InjectPoint.TAIL) {
                    for (int i = 0; i < buffered.size(); i++) {
                        if (isReturn(buffered.get(i))) {
                            lastReturn = i;
                        }
                    }
                }

                for (int i = 0; i < buffered.size(); i++) {
                    CodeElement element = buffered.get(i);
                    boolean injectHere = switch (mixin.point()) {
                        case HEAD -> i == 0;
                        case RETURN -> isReturn(element);
                        case TAIL -> i == lastReturn;
                    };
                    if (injectHere) {
                        builder.invokestatic(owner, name, type);
                    }
                    builder.with(element);
                }
            }
        });
    }

    /**
     * REDIRECT：找到匹配 owner#name 的那次调用，用 handler 替换掉它。
     *
     * 关键坑（第一版就踩了）：invokevirtual / invokeinterface / invokespecial 调用时，
     * 接收者（this）**已经在操作数栈上**。如果直接换成无参的 invokestatic，
     * this 会残留在栈上，栈失衡 → VerifyError。所以实例调用的 handler 必须
     * 把「接收者类型」作为第一个形参接走；只有 invokestatic 才和原签名一一对应。
     */
    private static CodeTransform redirector(ResolvedMixin mixin) {
        ClassDesc handlerOwner = ClassDesc.of(mixin.handlerClass());
        String handlerName = mixin.handlerMethod();
        String wantedOwner = mixin.callOwner().replace('.', '/');
        String wantedName = mixin.callName();

        return CodeTransform.ofStateful(() -> new CodeTransform() {
            private boolean replaced;

            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                if (!replaced && element instanceof InvokeInstruction invoke) {
                    boolean ownerMatches = invoke.owner().name().stringValue().equals(wantedOwner);
                    boolean nameMatches = invoke.name().stringValue().equals(wantedName);
                    if (ownerMatches && nameMatches) {
                        builder.invokestatic(handlerOwner, handlerName, handlerType(invoke));
                        replaced = true;
                        return;
                    }
                }
                builder.with(element);
            }
        });
    }

    /** 按被替换调用的形态，算出 handler 该有的描述符（实例调用要前置接收者参数）。 */
    private static MethodTypeDesc handlerType(InvokeInstruction invoke) {
        MethodTypeDesc original = invoke.typeSymbol();
        if (invoke.opcode() == Opcode.INVOKESTATIC) {
            return original;
        }
        List<ClassDesc> params = new java.util.ArrayList<>();
        params.add(invoke.owner().asSymbol());
        params.addAll(original.parameterList());
        return MethodTypeDesc.of(original.returnType(), params);
    }

    private static boolean isReturn(CodeElement element) {
        return element instanceof java.lang.classfile.instruction.ReturnInstruction;
    }

    /**
     * 统计织入后字节码里某 handler 被插了几次，按方法分组。
     *
     * 为什么需要它：RETURN 的语义是「每个出口都插」，但运行时一次调用只会走一个出口 ——
     * 光看日志永远只能看到 1 次，证明不了多出口。数一数字节码里静态调用的条数才是硬证据。
     *
     * @return 方法名 -> 该 handler 在这个方法体里出现的次数（只含次数 > 0 的方法）
     */
    public static java.util.Map<String, Integer> countInvocations(
        byte[] woven, String handlerClass, String handlerMethod) {
        ClassModel model = CLASS_FILE.parse(woven);
        String owner = handlerClass.replace('.', '/');
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (MethodModel method : model.methods()) {
            int count = 0;
            CodeModel code = method.code().orElse(null);
            if (code != null) {
                for (CodeElement element : code) {
                    if (element instanceof InvokeInstruction invoke
                        && invoke.owner().name().stringValue().equals(owner)
                        && invoke.name().stringValue().equals(handlerMethod)) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                counts.put(method.methodName().stringValue(), count);
            }
        }
        return counts;
    }
}
