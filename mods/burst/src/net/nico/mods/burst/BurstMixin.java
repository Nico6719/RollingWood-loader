package net.nico.mods.burst;

import net.nico.fakegame.Server;

/**
 * REDIRECT Handler。
 *
 * 关键：被替换的是 Server.describe() —— 一个**实例方法**，原调用是 invokevirtual。
 * 调用时 this 已经在栈上，所以 handler 的**第一个参数必须是接收者类型**，
 * 描述符才能对上。这是第一版没处理、会直接 VerifyError 的地方。
 */
public class BurstMixin {

    public static void onShutdown() {
        System.out.println("[burst] HEAD 注入：shutdown() 要来了");
    }

    /** 原签名：Server.describe()Ljava/lang/String; */
    public static String onDescribe(Server self) {
        return "[burst] 我把 describe() 整个换掉了（当时已跑 " + self.ticks() + " tick）";
    }
}
