#!/usr/bin/env bash
# 运行 MiniLoader。
# 用法：./run.sh [1.20.1|26.3]
# 注意 classpath 里**没有** game-classes —— 这是故意的。
set -euo pipefail

cd "$(dirname "$0")"

VERSION="${1:-26.3}"

# 把 out/ 组装成运行时目录结构
rm -rf run
mkdir -p run/mods
cp -r out/game-classes run/game-classes
cp out/mods/*.jar run/mods/

echo "[run] 滚木加载器 / RollingLog Loader"
echo "[run] 目标版本：$VERSION"
echo "[run] 运行时 classpath = loader-classes:api-classes（不含 game-classes）"
echo

# -Dstdout/-Dstderr.encoding：Windows 控制台默认 GBK，不改会中文乱码。
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
     -cp "out/loader-classes;out/api-classes" \
     net.nico.miniloader.MiniLoader "$(pwd)/run" "$VERSION"
