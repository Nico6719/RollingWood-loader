package net.nico.miniloader;

import java.util.Map;

/**
 * 版本档案。这就是「适配」的关键层。
 *
 * 事实背景：
 *  - 1.20.1（2023-06）走老版本号，游戏是**混淆**的，类名/方法名是无意义符号（要靠 Yarn/Intermediary 翻译）。
 *  - 26.3 走新年份版本号，Mojang 从 26.1（2026-03）起**彻底去混淆**，名字就是真名，映射体系（Yarn/Intermediary）已关停。
 *  - 所以同一句 mixin 声明 «往 net.nico.fakegame.Server#start 注入»，在两个版本里要落到**完全不同的运行时名字**上。
 *
 * 加载器内核（类加载拦截 + 织入 + 依赖排序 + 入口调度）是版本无关的；
 * 需要逐版本维护的就是这张映射表 —— 这也是 mod 作者真正的活儿。
 *
 * 这张表有两处消费，漏任何一处都是 NoSuchMethodError：
 *  1. 织入计划的定位（buildMixinPlan：target / method / call 名字翻译）。
 *  2. mod 字节码自身的类型和调用名翻译（ModRemapper）。
 */
public final class GameProfile {

    /** mod 作者写的、跨版本稳定的逻辑名。 */
    public static final String LOGICAL_SERVER = "net.nico.fakegame.Server";

    private final String version;
    private final String mainClass;
    private final Map<String, String> classAliases;
    private final Map<String, String> methodAliases;

    private GameProfile(String version, String mainClass,
                        Map<String, String> classAliases, Map<String, String> methodAliases) {
        this.version = version;
        this.mainClass = mainClass;
        this.classAliases = classAliases;
        this.methodAliases = methodAliases;
    }

    public static GameProfile of(String version) {
        return switch (version) {
            case "26.3" -> new GameProfile(version, LOGICAL_SERVER, Map.of(), Map.of());
            case "1.20.1" -> new GameProfile(
                version,
                "net.nico.fakegame.class_1132",
                Map.of(LOGICAL_SERVER, "net.nico.fakegame.class_1132"),
                // Map.of 最多 10 对，这里 12 对必须用 ofEntries。
                Map.ofEntries(
                    Map.entry(key("start"), "m_9001"),
                    Map.entry(key("tick"), "m_9002"),
                    Map.entry(key("shutdown"), "m_9003"),
                    Map.entry(key("status"), "m_9004"),
                    Map.entry(key("ticks"), "m_9005"),
                    Map.entry(key("describe"), "m_9006"),
                    // 渲染流水线。黑屏启动要把这几个调用整条熄灭，所以它们必须逐个登记。
                    Map.entry(key("render"), "m_9007"),
                    Map.entry(key("drawBackground"), "m_9008"),
                    Map.entry(key("drawWorld"), "m_9009"),
                    Map.entry(key("drawUi"), "m_9010"),
                    Map.entry(key("drawText"), "m_9011"),
                    Map.entry(key("present"), "m_9012")));
            default -> throw new IllegalArgumentException(
                "暂不支持的版本：" + version + "（这个演示只带了 26.3 和 1.20.1 两个档案，正好卡在去混淆断层的两边）");
        };
    }

    private static String key(String method) {
        return LOGICAL_SERVER + "#" + method;
    }

    public String version() {
        return version;
    }

    public String mainClass() {
        return mainClass;
    }

    public String runtimeClass(String logicalName) {
        return classAliases.getOrDefault(logicalName, logicalName);
    }

    public String runtimeMethod(String logicalClass, String logicalMethod) {
        return methodAliases.getOrDefault(logicalClass + "#" + logicalMethod, logicalMethod);
    }

    public boolean isDeobfuscated() {
        return classAliases.isEmpty();
    }
}
