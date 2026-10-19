package net.nico.miniloader.api;

import java.util.function.Consumer;

/**
 * 传给插件的上下文。真实加载器里这里会挂上事件总线、注册表、配置目录等。
 *
 * onRender 就是滚木的「渲染事件总线」入口：游戏自带的四层绘制
 * （背景/世界/UI/UI 文字）已被黑屏启动熄灭，屏幕默认全黑。
 * mod 想让用户看见任何东西，必须在这里注册回调，自己往 FrameBuffer 里画。
 */
public interface ModContext {
    String modId();
    void info(String message);

    /**
     * 注册每帧渲染回调。加载器在把帧上屏前会依次调用所有注册者的回调，
     * 后注册的画在上层（后画覆盖先画）。
     */
    void onRender(Consumer<FrameBuffer> drawer);
}
