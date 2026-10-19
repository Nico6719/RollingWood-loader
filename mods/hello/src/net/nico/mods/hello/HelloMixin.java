package net.nico.mods.hello;

/**
 * 织入 Handler。注意：这是**普通静态方法**，不是 Mixin 类。
 * 加载器把它的 invokestatic 插到目标位置。
 *
 * 约定：handler 类必须在**游戏类加载之后**才被解析。
 * 因为 Knot 只在加载游戏包时织入，handler 属于 mod 包，走 mod 加载路径，
 * 不会自己触发递归织入。
 */
public class HelloMixin {

    public static void onServerStart() {
        System.out.println("[hello] ★ HEAD 注入：这条是织进 Server.start() 开头的代码 ★");
    }

    /** 挂在 describe() 的 RETURN 上：每个出口都会打一次。 */
    public static void onDescribeReturn() {
        System.out.println("      [hello] RETURN 钩子：describe() 走了一个出口");
    }

    /** 挂在 status() 的 TAIL 上：只在最后一个出口打一次。 */
    public static void onStatusTail() {
        System.out.println("      [hello] TAIL 钩子：status() 即将返回");
    }
}
