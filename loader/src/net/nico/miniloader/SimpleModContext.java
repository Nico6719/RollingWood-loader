package net.nico.miniloader;

import net.nico.miniloader.api.FrameBuffer;
import net.nico.miniloader.api.ModContext;

import java.util.function.Consumer;

/** ModContext 的最小实现。真实加载器这里会挂事件总线、注册表、mod 配置目录。 */
final class SimpleModContext implements ModContext {

    private final String modId;

    SimpleModContext(String modId) {
        this.modId = modId;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public void info(String message) {
        System.out.println("[" + modId + "] " + message);
    }

    @Override
    public void onRender(Consumer<FrameBuffer> drawer) {
        BlankBoot.register(drawer);
    }
}
