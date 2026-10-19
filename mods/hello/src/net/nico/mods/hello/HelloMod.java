package net.nico.mods.hello;

import net.nico.miniloader.api.ModContext;
import net.nico.miniloader.api.ModInitializer;

public class HelloMod implements ModInitializer {

    @Override
    public void onInitialize(ModContext context) {
        context.info("初始化完成，准备在 Server.start 上织入问候语");
    }
}
