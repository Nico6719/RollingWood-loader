package net.nico.mods.burst;

import net.nico.miniloader.api.ModContext;
import net.nico.miniloader.api.ModInitializer;

public class BurstMod implements ModInitializer {

    @Override
    public void onInitialize(ModContext context) {
        context.info("初始化完成（我声明了依赖 hello，所以一定排在它之后初始化）");
    }
}
