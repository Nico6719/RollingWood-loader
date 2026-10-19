package net.nico.miniloader;

import net.nico.miniloader.api.FrameBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 黑屏启动（Blank Boot）的执行端 —— 织入计划里那几条内置 mixin 的 handler 都住在这。
 *
 * 滚木的默认姿态：
 *  - 游戏自带的四层绘制（背景/世界/UI/UI 文字）在织入期被整条换成 dropXxx 空实现；
 *  - 连最后的「呈现」都换成 present()：先跑 mod 注册的重绘回调，再上屏。
 *  所以 mod 不注册 onRender，用户看到的就是一块纯黑 —— 这就是「默认不渲染」。
 *  想看见画面？自己写 mod 画去。
 *
 * 关键坑（为什么这些 handler 只吃 FrameBuffer、一个游戏类型都不带）：
 * 它们属于 loader，不走 ModRemapper 的逻辑名翻译。签名里但凡出现
 * net.nico.fakegame.*，1.20.1 上就是下一个 NoSuchMethodError。
 * 游戏的绘制层因此必须设计成 invokestatic + 只收版本无关的 FrameBuffer，
 * 这样两个版本共用同一套 handler（见 Server/class_1132 的注释）。
 */
public final class BlankBoot {

    private static final List<Consumer<FrameBuffer>> DRAWERS = new ArrayList<>();
    private static int frames;

    private BlankBoot() {
    }

    /** mod 通过 ModContext#onRender 登记画笔。 */
    public static void register(Consumer<FrameBuffer> drawer) {
        DRAWERS.add(drawer);
    }

    // 四个绝热层：原版渲染被织入计划整条替换成这些空实现。
    public static void dropBackground(FrameBuffer frame) {
    }

    public static void dropWorld(FrameBuffer frame) {
    }

    public static void dropUi(FrameBuffer frame) {
    }

    public static void dropText(FrameBuffer frame) {
    }

    /** 原版呈现被替换成这个：先让 mod 画，再上屏。 */
    public static void present(FrameBuffer frame) {
        frames++;
        System.out.println("【第 " + frames + " 帧 · 原版 背景/世界/UI/UI文字 四层已熄灭，屏幕上只有 mod 自己画的】");
        for (Consumer<FrameBuffer> drawer : DRAWERS) {
            drawer.accept(frame);
        }
        System.out.println(frame.render());
    }
}