package net.nico.miniloader;

/**
 * 一条字节码织入声明，对标 Fabric 的 mixin 配置。
 * 告诉加载器「往哪个类的哪个方法里，做哪种织入」。
 *
 * INJECT 写法：
 *   { "target": "...", "method": "start", "at": "HEAD", "handler": "包.类#方法" }
 * REDIRECT 写法（替换目标方法内的一次调用）：
 *   { "target": "...", "method": "tick", "kind": "REDIRECT",
 *     "call": "net.nico.fakegame.Server#status", "handler": "包.类#方法" }
 *
 * @param target  目标类全限定名（逻辑名，跨版本稳定）
 * @param method  目标方法名（逻辑名）
 * @param kind    INJECT 或 REDIRECT
 * @param at      INJECT 的注入点 HEAD / RETURN / TAIL
 * @param call    REDIRECT 要替换的调用，「宿主类#方法名」形式
 * @param handler 织入的静态方法，形如 net.nico.mods.hello.HelloMixin#onServerStart
 */
public record MixinInfo(String target, String method, MixinKind kind,
                        InjectPoint at, String call, String handler) {

    public MixinInfo {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("mixin 缺少 target");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("mixin 缺少 method");
        }
        if (handler == null || !handler.contains("#")) {
            throw new IllegalArgumentException("mixin 的 handler 必须是 '类#方法' 形式，实际是 " + handler);
        }
        if (kind == MixinKind.REDIRECT && (call == null || !call.contains("#"))) {
            throw new IllegalArgumentException(
                "REDIRECT 型 mixin 必须写 call，格式 '宿主类#方法名'，实际是 " + call);
        }
    }

    public String handlerClass() {
        return handler.substring(0, handler.indexOf('#'));
    }

    public String handlerMethod() {
        return handler.substring(handler.indexOf('#') + 1);
    }

    /** REDIRECT 目标的宿主类（点分名）。 */
    public String callOwner() {
        return call.substring(0, call.indexOf('#'));
    }

    /** REDIRECT 目标的方法名。 */
    public String callName() {
        return call.substring(call.indexOf('#') + 1);
    }
}
