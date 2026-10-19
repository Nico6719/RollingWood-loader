package net.nico.miniloader;

import net.nico.miniloader.api.ModInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 加载器引导入口。对标 Fabric 的 Knot / 启动器主循环。
 *
 * 完整流程：
 *  1. 按版本档案决定「逻辑名 -> 运行时名」的翻译表（适配层）。
 *  2. 扫描 mods 目录里的 jar，读 mod.json，得到元数据。
 *  3. 依赖拓扑排序，确定初始化顺序。
 *  4. 把 mixin 声明翻译成运行时名字，形成织入计划。
 *  5. 起 Knot 类加载器，反射实例化入口点，按序调用 onInitialize。
 *  6. 从游戏主类反射调 start()，让织入的代码在真实调用路径上生效。
 */
public final class MiniLoader {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        String version = args.length > 1 ? args[1] : "26.3";

        Path gameDir = root.resolve("game-classes");
        Path modsDir = root.resolve("mods");

        GameProfile profile = GameProfile.of(version);

        System.out.println("=== 滚木加载器 / RollingLog Loader 启动 ===");
        System.out.println("[滚木] 本义：垫在重物下靠滚动摩擦省力的圆木 —— 我们真能把 mod 滚起来");
        System.out.println("[滚木] 梗义：不存在 · 空白 · 无价值 —— 那是留给隔壁 Forbric 的");
        System.out.println("[loader] 目标版本：" + version
            + "（" + (profile.isDeobfuscated() ? "已去混淆，名字即真名" : "仍混淆，需映射翻译") + "）");
        System.out.println("[loader] 游戏主类运行时名：" + profile.mainClass());
        System.out.println("[loader] 黑屏启动：原版 背景/世界/UI/UI文字 四层渲染全熄，屏幕交给 mod 自己画");

        List<Path> jars = discoverJars(modsDir);
        System.out.println("[loader] 在 mods 目录发现 " + jars.size() + " 个 jar");

        List<ModMetadata> parsed = new ArrayList<>();
        for (Path jar : jars) {
            parsed.add(readMetadata(jar));
        }

        List<ModMetadata> ordered = DependencyResolver.resolve(parsed);
        System.out.println("[loader] 依赖解析完成，初始化顺序："
            + ordered.stream().map(ModMetadata::id).toList());

        Map<String, List<ResolvedMixin>> plan = buildMixinPlan(ordered, profile);
        // 黑屏启动的内置织入（不来自任何 mod jar，是加载器自带的策略）。
        blankBootPlan(profile).forEach((target, mixins) ->
            plan.computeIfAbsent(target, k -> new ArrayList<>()).addAll(mixins));
        System.out.println("[loader] 织入计划：" + plan.size() + " 个目标类");
        plan.forEach((target, mixins) -> mixins.forEach(m ->
            System.out.println("          " + target + " :: " + m.describe()
                + "  <-  " + m.handlerClass() + "#" + m.handlerMethod()
                + "  [" + m.kind() + "]" + (m.handlerClass().startsWith("net.nico.miniloader.") ? "  [内置·黑屏]" : ""))));

        KnotClassLoader loader = new KnotClassLoader(
            gameDir, jars, plan, "net.nico.fakegame.", profile);

        for (ModMetadata mod : ordered) {
            initMod(loader, mod);
        }

        runGame(loader, profile);
        System.out.println("[滚木] 一圈滚完，没散架。");
        System.out.println("=== 滚木加载器 结束 ===");
    }

    private static List<Path> discoverJars(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(modsDir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        }
    }

    private static ModMetadata readMetadata(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry("mod.json");
            if (entry == null) {
                throw new IllegalStateException(jar.getFileName() + " 里没有 mod.json，不是合法 mod");
            }
            String text;
            try (InputStream in = jarFile.getInputStream(entry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return ModMetadata.from(Json.parseObject(text), jar);
        }
    }

    private static Map<String, List<ResolvedMixin>> buildMixinPlan(
        List<ModMetadata> mods, GameProfile profile) {
        Map<String, List<ResolvedMixin>> plan = new HashMap<>();
        for (ModMetadata mod : mods) {
            for (MixinInfo mixin : mod.mixins()) {
                String targetClass = profile.runtimeClass(mixin.target());
                String method = profile.runtimeMethod(mixin.target(), mixin.method());
                ResolvedMixin resolved = switch (mixin.kind()) {
                    case INJECT -> ResolvedMixin.inject(
                        method, mixin.at(), mixin.handlerClass(), mixin.handlerMethod());
                    case REDIRECT -> ResolvedMixin.redirect(
                        method,
                        // 被替换的调用也要过一遍映射，否则 1.20.1 上找不到目标。
                        profile.runtimeClass(mixin.callOwner()),
                        profile.runtimeMethod(mixin.callOwner(), mixin.callName()),
                        mixin.handlerClass(), mixin.handlerMethod());
                };
                plan.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(resolved);
            }
        }
        return plan;
    }

    /**
     * 黑屏启动的织入计划：把游戏自带的四层绘制 + 呈现整条熄灭。
     *
     * 五个目标（逻辑名，实际打字节码前会按 GameProfile 翻译成运行时名）：
     *   render 内的 drawBackground / drawWorld / drawUi / drawText -> dropXxx 空实现
     *   render 内的 present -> BlankBoot.present（跑 mod 重绘回调，再上屏）
     *
     * 这些 handler 只吃版本无关的 FrameBuffer，所以两个版本共用同一套，
     * 不需要 ModRemapper 参与 —— 这是它们能当内置织入的前提。
     */
    private static Map<String, List<ResolvedMixin>> blankBootPlan(GameProfile profile) {
        String logical = GameProfile.LOGICAL_SERVER;
        String runtimeTarget = profile.runtimeClass(logical);
        String runtimeRender = profile.runtimeMethod(logical, "render");
        String[] layers = {"drawBackground", "drawWorld", "drawUi", "drawText"};

        List<ResolvedMixin> mixins = new ArrayList<>();
        for (String layer : layers) {
            mixins.add(ResolvedMixin.redirect(
                runtimeRender,
                runtimeTarget,
                profile.runtimeMethod(logical, layer),
                "net.nico.miniloader.BlankBoot",
                "drop" + layer.substring(4)));
        }
        mixins.add(ResolvedMixin.redirect(
            runtimeRender,
            runtimeTarget,
            profile.runtimeMethod(logical, "present"),
            "net.nico.miniloader.BlankBoot",
            "present"));
        return Map.of(runtimeTarget, mixins);
    }

    @SuppressWarnings("unchecked")
    private static void initMod(KnotClassLoader loader, ModMetadata mod) throws Exception {
        Class<?> entryClass = Class.forName(mod.entrypoint(), true, loader);
        Object instance = entryClass.getDeclaredConstructor().newInstance();
        if (!(instance instanceof ModInitializer initializer)) {
            throw new IllegalStateException(mod.entrypoint() + " 没有实现 ModInitializer");
        }
        initializer.onInitialize(new SimpleModContext(mod.id()));
    }

    private static void runGame(KnotClassLoader loader, GameProfile profile) throws Exception {
        String logical = GameProfile.LOGICAL_SERVER;
        Class<?> serverClass = Class.forName(profile.mainClass(), true, loader);
        Object server = serverClass.getConstructor(String.class)
            .newInstance("MiniLoader 演示服务端");
        System.out.println("[loader] 游戏类由 " + serverClass.getClassLoader().getClass().getSimpleName() + " 加载");

        Method start = serverClass.getMethod(profile.runtimeMethod(logical, "start"));
        Method tick = serverClass.getMethod(profile.runtimeMethod(logical, "tick"));
        Method describe = serverClass.getMethod(profile.runtimeMethod(logical, "describe"));
        Method status = serverClass.getMethod(profile.runtimeMethod(logical, "status"));
        Method shutdown = serverClass.getMethod(profile.runtimeMethod(logical, "shutdown"));
        Method render = serverClass.getMethod(profile.runtimeMethod(logical, "render"));

        start.invoke(server);
        // 前三 tick 每 tick 渲染一帧：第 1 帧没有 mod 动笔 = 纯黑对照，
        // 后两帧是 mod 通过 onRender 自己画出来的画面。
        for (int i = 0; i < 3; i++) {
            tick.invoke(server);
            render.invoke(server);
        }

        // 在每个 tick 之后立刻调 describe()，tick 数不同 -> 命中不同出口 -> RETURN 钩子次数应当累加。
        // 这是 RETURN（每个出口都插）的严格验证：如果只插了最后一个出口，这里只会打 3 次而不是 6 次。
        System.out.println("  [loader] 交错调用 describe()，验证 RETURN 是真的挂在每个出口上：");
        for (int i = 0; i < 3; i++) {
            tick.invoke(server);
            System.out.println("    tick#" + (i + 1) + " 后 describe() = " + describe.invoke(server));
        }

        // status() 只有一个出口 —— 验证 TAIL 钩子。
        System.out.println("  [loader] 调用 status()，看 TAIL 钩子：");
        System.out.println("    status() 返回：" + status.invoke(server));

        shutdown.invoke(server);
    }
}
