package net.nico.fakegame;

import net.nico.miniloader.api.FrameBuffer;

/**
 * 假装这是 Minecraft 的服务端主类。
 * 关键点：这个类**不在**应用 classpath 上，只能被 Knot 类加载器加载，
 * 所以它的字节码一定会经过织入流水线。
 */
public class Server {

    private final String motd;
    private int ticks;
    private boolean hardcore;

    public Server(String motd) {
        this.motd = motd;
    }

    public void start() {
        System.out.println("[game] Server 启动完成，motd=" + motd);
    }

    public void tick() {
        ticks++;
        // 这里的 describe() 调用会被 burst 的 REDIRECT 换掉。
        System.out.println("[game] tick 第 " + ticks + " 次，状态：" + describe());
    }

    /**
     * 多个出口的方法：用来测 RETURN（每次 return 都插）和 TAIL（只在最后一个 return 插）。
     */
    public String describe() {
        if (ticks <= 0) {
            return "尚未启动";
        }
        if (ticks < 3) {
            return "运行中(" + ticks + ")";
        }
        return "已运行 " + ticks + " 次";
    }

    public void setHardcore(boolean hardcore) {
        this.hardcore = hardcore;
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public void shutdown() {
        System.out.println("[game] Server 关闭，总共运行 " + ticks + " tick");
    }

    public String status() {
        return "motd=" + motd + ", ticks=" + ticks;
    }

    public int ticks() {
        return ticks;
    }

    /**
     * 渲染一帧：四层绘制 + 呈现。黑屏启动会在织入期把这里的四个 draw* 调用
     * 整条换成 BlankBoot 的空实现，连 present 都换成先跑 mod 回调再上屏。
     *
     * 为什么四个绘制层是 static 且只收 FrameBuffer：
     *   黑屏 handler 住在 loader 里，不走 ModRemapper 翻译。如果它们是实例方法，
     *   handler 就得带接收者类型（Server / class_1132），1.20.1 上直接 NoSuchMethodError。
     *   改成 invokestatic + 只吃版本无关的 FrameBuffer，两个版本共用同一套 handler。
     */
    public void render() {
        FrameBuffer frame = new FrameBuffer(58, 14);
        drawBackground(frame);
        drawWorld(frame);
        drawUi(frame);
        drawText(frame);
        present(frame);
    }

    public static void drawBackground(FrameBuffer frame) {
        frame.fill('.');
    }

    public static void drawWorld(FrameBuffer frame) {
        frame.text(0, 10, "▓".repeat(58));
        frame.text(2, 9, "原版世界：树木、地形、天空（反正黑屏启动后你见不到）");
    }

    public static void drawUi(FrameBuffer frame) {
        frame.box(1, 1, 26, 5);
        frame.text(3, 3, "原版 UI 面板");
    }

    public static void drawText(FrameBuffer frame) {
        frame.text(3, 7, "HP:100  Score:0  [原版 UI 文字]");
    }

    public static void present(FrameBuffer frame) {
        System.out.println(frame.render());
    }
}
