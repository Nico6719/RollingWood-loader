package net.nico.miniloader.api;

/**
 * 插件入口点。加载器解析完 mod.json、做完依赖排序后，
 * 会反射实例化 entrypoint 并调用本方法一次。
 */
public interface ModInitializer {
    void onInitialize(ModContext context);
}
