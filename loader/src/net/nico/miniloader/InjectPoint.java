package net.nico.miniloader;

/**
 * INJECT 的注入位置。对标 Fabric Mixin 的 @At("HEAD") / @At("RETURN") / @At("TAIL")。
 *
 * HEAD   —— 方法体开头，第一条指令之前。
 * RETURN —— 每一个 return 指令之前（多出口方法会被插多次）。
 * TAIL   —— 仅最后一个 return 指令之前（对应 Mixin 的 TAIL 语义）。
 */
public enum InjectPoint {
    HEAD,
    RETURN,
    TAIL;

    public static InjectPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HEAD;
        }
        return switch (raw.trim().toUpperCase()) {
            case "HEAD" -> HEAD;
            case "RETURN" -> RETURN;
            case "TAIL" -> TAIL;
            default -> throw new IllegalArgumentException(
                "不认识的注入点：" + raw + "（支持 HEAD / RETURN / TAIL）");
        };
    }
}
