package net.nico.miniloader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 依赖解析：Kahn 拓扑排序 + 缺失依赖 / 循环依赖检测。
 * 排序保证被依赖的 mod 先初始化。队列按 id 字母序出队，让结果可复现（不被文件系统顺序影响）。
 */
public final class DependencyResolver {

    private DependencyResolver() {
    }

    public static List<ModMetadata> resolve(Collection<ModMetadata> mods) {
        Map<String, ModMetadata> byId = new LinkedHashMap<>();
        for (ModMetadata mod : mods) {
            ModMetadata previous = byId.put(mod.id(), mod);
            if (previous != null) {
                throw new IllegalStateException("mod id 冲突：" + mod.id()
                    + "（" + previous.jar().getFileName() + " 与 " + mod.jar().getFileName() + "）");
            }
        }

        for (ModMetadata mod : mods) {
            for (String dep : mod.depends().keySet()) {
                if (!byId.containsKey(dep)) {
                    throw new IllegalStateException("mod " + mod.id() + " 依赖 " + dep + "，但没有找到该 mod");
                }
                if (dep.equals(mod.id())) {
                    throw new IllegalStateException("mod " + mod.id() + " 依赖了自己");
                }
            }
        }

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (ModMetadata mod : mods) {
            inDegree.putIfAbsent(mod.id(), 0);
            dependents.putIfAbsent(mod.id(), new ArrayList<>());
        }
        for (ModMetadata mod : mods) {
            for (String dep : mod.depends().keySet()) {
                dependents.get(dep).add(mod.id());
                inDegree.merge(mod.id(), 1, Integer::sum);
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<ModMetadata> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            ordered.add(byId.get(id));
            for (String dependent : dependents.get(id)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != mods.size()) {
            List<String> stuck = new ArrayList<>();
            for (ModMetadata mod : mods) {
                if (inDegree.get(mod.id()) > 0) {
                    stuck.add(mod.id());
                }
            }
            throw new IllegalStateException("检测到循环依赖，涉及：" + stuck);
        }
        return ordered;
    }
}
