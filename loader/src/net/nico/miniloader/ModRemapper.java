package net.nico.miniloader;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.FieldElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * mod 字节码翻译：逻辑名 -> 运行时名。对标 Fabric 生态里 tiny-remapper 干的活。
 *
 * 为什么必须有这一层（1.20.1 实测踩坑，第一版就死在这）：
 *   mod 是对着**逻辑名**编译的 —— BurstMixin.onDescribe(Server self) 编译出来是
 *   (Lnet/nico/fakegame/Server;)Ljava/lang/String;，体内 self.ticks() 是
 *   invokevirtual Server.ticks ()I。而 1.20.1 运行时那个类叫 class_1132、方法叫 m_9005。
 *   光翻译织入计划不够：织入点生成的 invokestatic 拿运行时类当参数（栈上是 class_1132），
 *   mod 里那个方法却还是逻辑签名，链接期直接
 *   NoSuchMethodError: BurstMixin.onDescribe(class_1132)。
 *   26.3 没事纯粹是因为逻辑名 == 运行时名，翻译是恒等。
 *
 * 翻译规则（映射表只有一张 —— GameProfile，和织入计划共用）：
 *   - 描述符里的类引用：Lnet/nico/fakegame/Server; -> Lnet/nico/fakegame/class_1132;
 *   - 调用点方法名：Server#ticks -> class_1132#m_9005（映射表没登记的原样保留）
 *
 * 覆盖范围按 .probe 实测的 javac 输出定，不瞎扩：
 *   方法/字段描述符、invoke*、invokedynamic、字段访问、checkcast/instanceof、new、ldc。
 * 不翻译的：字符串常量（含 indy 的 concat 配方）、泛型 Signature、
 *   LocalVariableTable（javac 默认不带 -g:vars，根本没有）、字段名（GameProfile 没有字段表）。
 */
public final class ModRemapper {

    private static final ClassFile CLASS_FILE = ClassFile.of();

    private final GameProfile profile;

    private ModRemapper(GameProfile profile) {
        this.profile = profile;
    }

    /** 把一个 mod class 从逻辑命名空间翻译到当前版本的运行时命名空间。 */
    public static byte[] apply(byte[] original, GameProfile profile) {
        return new ModRemapper(profile).transform(original);
    }

    private byte[] transform(byte[] original) {
        ClassModel model = CLASS_FILE.parse(original);
        return CLASS_FILE.transformClass(model, (ClassBuilder builder, ClassElement element) -> {
            if (element instanceof MethodModel method) {
                // 描述符在方法头里，MethodTransform 改不动它，只能整条重建再搬元素。
                builder.withMethod(
                    method.methodName().stringValue(),
                    mapMethodType(method.methodTypeSymbol()),
                    method.flags().flagsMask(),
                    mb -> copyMethod(mb, method));
            } else if (element instanceof FieldModel field) {
                builder.withField(
                    field.fieldName().stringValue(),
                    mapClass(field.fieldTypeSymbol()),
                    fb -> copyField(fb, field));
            } else {
                // 类头（superclass/interfaces）走这里。本玩具的 mod 只继承 Object、实现 api 接口，
                // 没有游戏类型；真出现「mod 继承游戏类」时这里也得翻译。
                builder.with(element);
            }
        });
    }

    private void copyMethod(MethodBuilder builder, MethodModel method) {
        method.code().ifPresent(code -> builder.transformCode(code, codeTransform()));
        for (MethodElement element : method) {
            // CodeModel 由 transformCode 改写；AccessFlags 已经随 withMethod 的参数带上了。
            if (element instanceof CodeModel || element instanceof AccessFlags) {
                continue;
            }
            builder.with(element);
        }
    }

    private void copyField(FieldBuilder builder, FieldModel field) {
        builder.withFlags(field.flags().flagsMask());
        for (FieldElement element : field) {
            if (element instanceof AccessFlags) {
                continue;
            }
            builder.with(element);
        }
    }

    private CodeTransform codeTransform() {
        return (CodeBuilder builder, CodeElement element) -> {
            if (element instanceof InvokeInstruction invoke) {
                // 方法名要按**原 owner**（逻辑名）查表；发射出去的 owner 是翻译后的。
                String name = profile.runtimeMethod(
                    dotted(invoke.owner().name().stringValue()), invoke.name().stringValue());
                builder.invoke(invoke.opcode(),
                    mapClass(invoke.owner().asSymbol()),
                    name,
                    mapMethodType(invoke.typeSymbol()),
                    invoke.isInterface());
            } else if (element instanceof FieldInstruction field) {
                builder.fieldAccess(field.opcode(),
                    mapClass(field.owner().asSymbol()),
                    field.name().stringValue(),
                    mapClass(field.typeSymbol()));
            } else if (element instanceof InvokeDynamicInstruction indy) {
                MethodTypeDesc mapped = mapMethodType(indy.typeSymbol());
                if (mapped.equals(indy.typeSymbol())) {
                    builder.with(element); // 描述符里没有游戏类型（字符串拼接就是这种），直接过
                } else {
                    // indy 的 name 是 bootstrap 约定的名字（makeConcatWithConstants 之类），不翻译。
                    // bootstrap 参数里只有字符串配方/常量，原样传。
                    builder.invokedynamic(DynamicCallSiteDesc.of(
                        indy.bootstrapMethod(), indy.name().stringValue(), mapped,
                        indy.bootstrapArgs().toArray(new ConstantDesc[0])));
                }
            } else if (element instanceof TypeCheckInstruction check) {
                builder.with(TypeCheckInstruction.of(check.opcode(), mapClass(check.type().asSymbol())));
            } else if (element instanceof NewObjectInstruction create) {
                builder.new_(mapClass(create.className().asSymbol()));
            } else if (element instanceof ConstantInstruction constant) {
                ConstantDesc mapped = mapConstant(constant.constantValue());
                if (mapped != null && mapped != constant.constantValue()) {
                    builder.ldc(mapped);
                } else {
                    builder.with(element);
                }
            } else {
                builder.with(element);
            }
        };
    }

    /** ldc 里可能揣着 Class 常量（Server.class 这种），只翻译它和方法类型常量。 */
    private ConstantDesc mapConstant(ConstantDesc value) {
        if (value instanceof ClassDesc desc) {
            return mapClass(desc);
        }
        if (value instanceof MethodTypeDesc type) {
            return mapMethodType(type);
        }
        return value;
    }

    private ClassDesc mapClass(ClassDesc desc) {
        String mapped = mapDescriptor(desc.descriptorString());
        return mapped.equals(desc.descriptorString()) ? desc : ClassDesc.ofDescriptor(mapped);
    }

    private MethodTypeDesc mapMethodType(MethodTypeDesc type) {
        String mapped = mapDescriptor(type.descriptorString());
        return mapped.equals(type.descriptorString()) ? type : MethodTypeDesc.ofDescriptor(mapped);
    }

    /**
     * 描述符里的类引用长这样：L包/类;。逐个替换成运行时名。
     * 只认 L...; 段，字符串常量压根不经过这里，所以不会误伤。
     */
    private String mapDescriptor(String descriptor) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                String internal = descriptor.substring(i + 1, end);
                out.append('L').append(profile.runtimeClass(dotted(internal)).replace('.', '/')).append(';');
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String dotted(String internalName) {
        return internalName.replace('/', '.');
    }
}
