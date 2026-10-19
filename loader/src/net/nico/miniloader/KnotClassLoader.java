package net.nico.miniloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 对标 Fabric 的 KnotClassLoader。
 * 职责：所有游戏类都必须经过我这里加载，加载前先把 mixin 织进去。
 *
 * 加载委派策略（这是加载器的常见坑，值得写清楚）：
 *  1. 游戏包（net.nico.fakegame.*）—— 自己加载，并织入。父加载器看不到它，
 *     因为 game 目录不在应用的 classpath 上，这样才拦得住。
 *  2. mod 包（net.nico.mods.*）—— 先从各自 jar 里找，定义前把逻辑名翻译成运行时名
 *     （ModRemapper，对标 tiny-remapper），否则 1.20.1 上 handler 签名对不上。
 *  3. 其余（JDK、api）—— 交给父加载器。
 */
public final class KnotClassLoader extends ClassLoader {

    private final Path gameDir;
    private final List<Path> modJars;
    private final Map<String, List<ResolvedMixin>> mixinPlan;
    private final String gamePackagePrefix;
    private final GameProfile profile;

    /**
     * 父加载器就是应用加载器，它带着 loader + api 两类，但不带 game 类。
     * game 类不在运行期 classpath 上，是我们能拦住它的前提。
     */
    public KnotClassLoader(Path gameDir, List<Path> modJars,
                           Map<String, List<ResolvedMixin>> mixinPlan,
                           String gamePackagePrefix, GameProfile profile) {
        this(GameProfile.class.getClassLoader(), gameDir, modJars, mixinPlan, gamePackagePrefix, profile);
    }

    private KnotClassLoader(ClassLoader parent, Path gameDir, List<Path> modJars,
                            Map<String, List<ResolvedMixin>> mixinPlan,
                            String gamePackagePrefix, GameProfile profile) {
        super(parent);
        this.gameDir = gameDir;
        this.modJars = modJars;
        this.mixinPlan = mixinPlan;
        this.gamePackagePrefix = gamePackagePrefix;
        this.profile = profile;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> already = findLoadedClass(name);
            if (already != null) {
                return already;
            }

            if (name.startsWith(gamePackagePrefix)) {
                byte[] bytes = readGameClass(name);
                bytes = weave(name, bytes);
                Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                if (resolve) {
                    resolveClass(defined);
                }
                return defined;
            }

            if (name.startsWith("net.nico.mods.")) {
                byte[] bytes = readModClass(name);
                if (bytes != null) {
                    // mod 对着逻辑名编译，运行时得说 1.20.1/26.3 的方言。26.3 下这是恒等翻译。
                    bytes = ModRemapper.apply(bytes, profile);
                    Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) {
                        resolveClass(defined);
                    }
                    return defined;
                }
            }

            return super.loadClass(name, resolve);
        }
    }

    private byte[] weave(String name, byte[] bytes) {
        List<ResolvedMixin> mixins = mixinPlan.get(name);
        if (mixins == null || mixins.isEmpty()) {
            return bytes;
        }
        byte[] result = MixinApplier.apply(bytes, mixins);
        System.out.println("[loader] 已织入 " + name + "（" + mixins.size() + " 条 mixin）");

        // 字节码级验收：数一数每个 handler 到底被插了几次。
        // 这是证明 RETURN「每个出口都插」的唯一硬证据 —— 运行时走不到所有出口。
        for (ResolvedMixin mixin : mixins) {
            java.util.Map<String, Integer> counts = MixinApplier.countInvocations(
                result, mixin.handlerClass(), mixin.handlerMethod());
            counts.forEach((method, n) -> System.out.println(
                "          [字节码实测] " + method + "() 里 " + mixin.handlerMethod() + " 出现 " + n + " 次"));
        }
        return result;
    }

    private byte[] readGameClass(String name) throws ClassNotFoundException {
        return readClassFromDir(gameDir, name, "游戏类");
    }

    private byte[] readModClass(String name) {
        for (Path jar : modJars) {
            byte[] bytes = readClassFromJar(jar, name);
            if (bytes != null) {
                return bytes;
            }
        }
        return null;
    }

    private static byte[] readClassFromDir(Path dir, String name, String what) throws ClassNotFoundException {
        Path file = dir.resolve(name.replace('.', '/') + ".class");
        try {
            if (Files.exists(file)) {
                return Files.readAllBytes(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取" + what + "失败：" + file, e);
        }
        throw new ClassNotFoundException(what + " " + name + " 不存在（预期路径：" + file + "）");
    }

    private static byte[] readClassFromJar(Path jar, String name) {
        String entry = name.replace('.', '/') + ".class";
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.JarEntry found = jarFile.getJarEntry(entry);
            if (found == null) {
                return null;
            }
            try (InputStream in = jarFile.getInputStream(found)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 jar 失败：" + jar, e);
        }
    }
}
