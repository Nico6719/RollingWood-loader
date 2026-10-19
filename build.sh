#!/usr/bin/env bash
# MiniLoader 构建脚本。纯 javac + jar，零第三方依赖。
# 设计要点：game 类编译进 game-classes/，但**绝不**进运行时 classpath，
# 否则 KnotClassLoader 拦不住它，织入就不会发生。
set -euo pipefail

cd "$(dirname "$0")"

JAVAC="${JAVAC:-javac}"
JAR="${JAR:-jar}"

# classpath 分隔符：Windows 是 ';'，Linux/macOS 是 ':'。
# Git Bash 里跑的是 Windows 版 java，所以 MINGW/MSYS/CYGWIN 仍按 ';' 算。
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac

echo "[build] 清理旧产物"
rm -rf out
mkdir -p out/api-classes out/game-classes out/loader-classes out/mods

echo "[build] 编译 api"
"$JAVAC" -d out/api-classes $(find api/src -name '*.java')

echo "[build] 编译 loader（依赖 api）"
"$JAVAC" -cp out/api-classes -d out/loader-classes $(find loader/src -name '*.java')

echo "[build] 编译 game（只编译，不进运行时 classpath）"
# game 依赖 api（FrameBuffer），api 在 api-classes 里；game-classes 依旧不进运行时 classpath。
"$JAVAC" -cp out/api-classes -d out/game-classes $(find game/src -name '*.java')

for mod in hello burst painter; do
  echo "[build] 编译并打包 mod: $mod"
  mkdir -p out/mods/$mod-classes
  # game-classes 只进编译期 classpath（等价 Fabric 里拿游戏 jar 当 compileOnly）：
  # mod 的 REDIRECT handler 需要引用目标类类型。运行期 classpath 依旧不含 game-classes。
  "$JAVAC" -cp "out/api-classes${SEP}out/game-classes" -d out/mods/$mod-classes $(find mods/$mod/src -name '*.java')
  cp mods/$mod/src/mod.json out/mods/$mod-classes/mod.json
  "$JAR" --create --file out/mods/$mod.jar -C out/mods/$mod-classes .
done

echo "[build] 完成。产物在 out/"
