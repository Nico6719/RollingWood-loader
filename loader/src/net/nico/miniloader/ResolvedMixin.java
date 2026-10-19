package net.nico.miniloader;

/**
 * 织入计划里的一条，已把「逻辑目标」翻译成「运行时名字」。
 *
 * 为什么和 MixinInfo 分开：MixinInfo 是 mod 作者写的、版本无关的声明；
 * ResolvedMixin 是加载器按当前游戏版本翻译后、真正要打字节码的东西。
 *
 * @param kind          INJECT 还是 REDIRECT
 * @param point         INJECT 的注入位置（REDIRECT 时无意义，见 NONE）
 * @param methodName    目标方法名（运行时名）
 * @param callOwner     REDIRECT：被替换调用的宿主类，点分名；INJECT 时为 null
 * @param callName      REDIRECT：被替换调用的方法名；INJECT 时为 null
 * @param handlerClass  织入的静态方法所在类
 * @param handlerMethod 织入的静态方法名
 */
public record ResolvedMixin(
    MixinKind kind,
    InjectPoint point,
    String methodName,
    String callOwner,
    String callName,
    String handlerClass,
    String handlerMethod
) {

    public static ResolvedMixin inject(String methodName, InjectPoint point,
                                       String handlerClass, String handlerMethod) {
        return new ResolvedMixin(MixinKind.INJECT, point, methodName,
            null, null, handlerClass, handlerMethod);
    }

    public static ResolvedMixin redirect(String methodName, String callOwner, String callName,
                                         String handlerClass, String handlerMethod) {
        return new ResolvedMixin(MixinKind.REDIRECT, InjectPoint.HEAD, methodName,
            callOwner, callName, handlerClass, handlerMethod);
    }

    public String describe() {
        if (kind == MixinKind.REDIRECT) {
            return methodName + " 内的 " + callOwner + "#" + callName;
        }
        return methodName + "@" + point;
    }
}
