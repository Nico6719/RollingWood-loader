package net.nico.miniloader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mod.json 解析后的元数据。对标 Fabric 的 fabric.mod.json。
 *
 * @param id         唯一标识
 * @param name       展示名
 * @param version    版本
 * @param entrypoint 入口点类名（实现 ModInitializer）
 * @param depends    依赖：modId -> 版本区间
 * @param mixins     字节码织入声明
 * @param jar        来源 jar
 */
public record ModMetadata(
    String id,
    String name,
    String version,
    String entrypoint,
    Map<String, String> depends,
    List<MixinInfo> mixins,
    Path jar
) {

    static ModMetadata from(Map<String, Object> json, Path jar) {
        String id = requireString(json, "id", jar);
        String name = optionalString(json, "name", id);
        String version = optionalString(json, "version", "0.0.0");
        String entrypoint = requireString(json, "entrypoint", jar);

        Map<String, String> depends = new LinkedHashMap<>();
        if (json.get("depends") instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> depends.put(String.valueOf(k), String.valueOf(v)));
        }

        List<MixinInfo> mixins = new ArrayList<>();
        if (json.get("mixins") instanceof List<?> raw) {
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?> mixinMap)) {
                    throw new IllegalArgumentException(jar.getFileName() + " 的 mixins 元素不是对象");
                }
                // 不用 getOrDefault：Map<?,?> 的 wildcard capture 会让默认值类型对不上。
                String kindRaw = opt(mixinMap, "kind", "INJECT");
                MixinKind kind = switch (kindRaw.trim().toUpperCase()) {
                    case "INJECT" -> MixinKind.INJECT;
                    case "REDIRECT" -> MixinKind.REDIRECT;
                    default -> throw new IllegalArgumentException(
                        jar.getFileName() + " 的 mixin 有不认识的 kind：" + kindRaw);
                };
                mixins.add(new MixinInfo(
                    String.valueOf(mixinMap.get("target")),
                    String.valueOf(mixinMap.get("method")),
                    kind,
                    InjectPoint.parse(opt(mixinMap, "at", "HEAD")),
                    mixinMap.get("call") == null ? null : String.valueOf(mixinMap.get("call")),
                    String.valueOf(mixinMap.get("handler"))
                ));
            }
        }

        return new ModMetadata(id, name, version, entrypoint, depends, List.copyOf(mixins), jar);
    }

    private static String opt(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String requireString(Map<String, Object> json, String key, Path jar) {
        Object value = json.get(key);
        if (value == null) {
            throw new IllegalArgumentException(jar.getFileName() + " 的 mod.json 缺少必填字段 " + key);
        }
        return String.valueOf(value);
    }

    private static String optionalString(Map<String, Object> json, String key, String fallback) {
        Object value = json.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
