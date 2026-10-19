package net.nico.mods.painter;

import net.nico.miniloader.api.FrameBuffer;
import net.nico.miniloader.api.ModContext;
import net.nico.miniloader.api.ModInitializer;

/**
 * 画师 mod —— 黑屏启动下，屏幕上的一切都得 mod 自己画，这就是样板。
 *
 * 背景、世界、UI、UI 文字四层都被加载器熄灭了，
 * 这个 mod 从零开始画：标题、山脉、地面、血条 HUD、计分、聊天框、帧号……
 * 第 1 帧故意什么都不画 —— 那就是「没有 mod 注册 onRender」时你会看到的样子：纯黑。
 */
public class PainterMod implements ModInitializer {

    private final int[] frame = {0};

    @Override
    public void onInitialize(ModContext context) {
        context.onRender(this::paint);
        context.info("已注册重绘回调：原版不画，那就都由我来画");
    }

    private void paint(FrameBuffer f) {
        frame[0]++;
        if (frame[0] == 1) {
            return; // 第 1 帧留白：证明黑屏启动默认真·全黑
        }

        // 标题
        f.text(6, 0, "◆ 滚木画坊 —— 屏幕上每个像素都是本 mod 画的 ◆");

        // 天空与山脉（自己搭的「世界」）
        f.text(3, 2, "☁        ^^^      ^^^^^^        ☁");
        f.text(2, 4, "▲▲  原版世界早被熄了，这是 mod 重新搭的");
        f.text(0, 3, "▓".repeat(58));

        // HUD：血条 + 计分
        f.box(1, 6, 28, 4);
        f.text(3, 7, "HP");
        f.bar(6, 7, 12, 8, 12);
        f.text(20, 7, "80/120");
        f.text(3, 8, "Score 1337 · 帧 " + frame[0] + "/3");

        // 聊天框
        f.box(29, 6, 28, 4);
        f.text(31, 7, "[聊天] 凛：哼，全是我画的");
        f.text(31, 8, "[聊天] 不动笔你只能盯黑屏");

        // 状态行
        f.text(2, 11, "[原版四层] 背景✗ 世界✗ UI✗ 文字✗    [mod 重绘] ✓");
        f.text(2, 12, "想看别的画面？再写个 mod 来抢画笔啊");
    }
}
