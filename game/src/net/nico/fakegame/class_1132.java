package net.nico.fakegame;

import net.nico.miniloader.api.FrameBuffer;

/**
 * 模拟 1.20.1 时代的**混淆**游戏类。
 *
 * 为什么名字长这样：2026-03 之前 Minecraft 是混淆发布的，
 * net.minecraft.server.MinecraftServer 在运行时叫 class_1132（Yarn/Intermediary 里），
 * 方法 m_9001 之类同理。所以 1.20.1 的 mod 必须靠映射表把逻辑名翻译成这些符号。
 *
 * 这个类和 Server 是同一份逻辑的两个「版本形态」，
 * 26.3 去混淆后直接用 Server，1.20.1 得用 class_1132 —— GameProfile 负责选哪个。
 */
public class class_1132 {

    private final String f_1001;
    private int f_1002;

    public class_1132(String p_1001) {
        this.f_1001 = p_1001;
    }

    public void m_9001() {
        System.out.println("[game] [1.20.1] class_1132.m_9001() 启动完成，motd=" + f_1001);
    }

    public void m_9002() {
        f_1002++;
        System.out.println("[game] [1.20.1] tick " + f_1002 + "，状态：" + m_9006());
    }

    public void m_9003() {
        System.out.println("[game] [1.20.1] class_1132.m_9003() 关闭，总共运行 " + f_1002 + " tick");
    }

    public String m_9004() {
        return "motd=" + f_1001 + ", ticks=" + f_1002;
    }

    public int m_9005() {
        return f_1002;
    }

    public String m_9006() {
        if (f_1002 <= 0) {
            return "尚未启动";
        }
        if (f_1002 < 3) {
            return "运行中(" + f_1002 + ")";
        }
        return "已运行 " + f_1002 + " 次";
    }

    // ===== 渲染流水线（26.3 形态叫 render/drawBackground/...，这里 m_9007~m_9012）=====
    // 绘制层同样是 static + 只吃 FrameBuffer：黑屏 handler 在 loader 里、不走 ModRemapper，
    // 签名带游戏类型的话 1.20.1 上就是 NoSuchMethodError。
    public void m_9007() {
        FrameBuffer frame = new FrameBuffer(58, 14);
        m_9008(frame);
        m_9009(frame);
        m_9010(frame);
        m_9011(frame);
        m_9012(frame);
    }

    public static void m_9008(FrameBuffer frame) {
        frame.fill('.');
    }

    public static void m_9009(FrameBuffer frame) {
        frame.text(0, 10, "▓".repeat(58));
        frame.text(2, 9, "[1.20.1] 原版世界：树木、地形、天空（反正黑屏启动后你见不到）");
    }

    public static void m_9010(FrameBuffer frame) {
        frame.box(1, 1, 26, 5);
        frame.text(3, 3, "[1.20.1] 原版 UI 面板");
    }

    public static void m_9011(FrameBuffer frame) {
        frame.text(3, 7, "HP:100  Score:0  [1.20.1 原版 UI 文字]");
    }

    public static void m_9012(FrameBuffer frame) {
        System.out.println(frame.render());
    }
}
