# 滚木加载器 / RollingWood Loader

一个真能改字节码的 Minecraft mod 加载器。整活作品，但验证是真的。

名字是双关。本义上，滚木是垫在重物底下、靠滚动摩擦省力的圆木，这个加载器真能把 mod 滚起来；梗义上，「滚木」在电竞梗里表示「不存在、空白、无价值」（谐音链：电棍 → 棍母 → 滚木），这顶帽子留给隔壁 Forbric。

机制上对标 Fabric / Forge：Knot 式类加载器、Mixin 织入、版本适配层，一个不少。但全部手写，字节码改写直接用 JDK 25 自带的 Class-File API（JEP 484），不用 ASM，也不用 gradle / maven。整个仓库没有一个第三方 jar，有 JDK 24+ 就能编能跑。

玩具一个，不是工业品。仓库里不含任何 Mojang 资产，「游戏」是 `game/` 下自带的假服务端。

## 能干什么

游戏类全部经过加载器，原版字节码到不了 JVM，加载前先织入。织入分两种：

- `INJECT`：HEAD / RETURN / TAIL 三个注入点
- `REDIRECT`：把方法里的某次调用整个换掉，实例调用的栈平衡有处理

版本适配是重头。同一套 mod jar，`1.20.1`（混淆版，类名 `class_1132`、方法名 `m_9001`）和 `26.3`（Mojang 去混淆后的真名世界）都能跑。适配层叫 `GameProfile`，负责把逻辑名翻译成运行时名；`ModRemapper` 负责把 mod 自己的字节码也翻过去，对标 Fabric 生态的 tiny-remapper。

其余几件事：黑屏启动默认熄掉原版的背景 / 世界 / UI / UI 文字四层渲染，连呈现都劫持，mod 不注册 `onRender` 用户看到的就是纯黑，想显示画面自己写 mod 画去；依赖解析走 Kahn 拓扑排序，缺依赖和循环依赖直接报错；织入结果数字节码，RETURN 承诺「每个出口都插」，运行时走不到所有出口，只有字节码计数能证明。

## 怎么跑

JDK 24+，纯 javac + jar：

```bash
./build.sh          # 编译 loader / api / game / mods，打包 mod jar
./run.sh 26.3       # 跑去混淆版
./run.sh 1.20.1     # 跑混淆版
```

`game/` 只参与编译，不进运行时 classpath，否则类加载器拦不住它，织入就不会发生。

## 目录

```
api/     mod 可见的接口：ModInitializer / ModContext / FrameBuffer
loader/  内核：KnotClassLoader / MixinApplier / GameProfile /
          ModRemapper / BlankBoot / DependencyResolver / MiniLoader
game/    假服务端，两个版本形态：Server ↔ class_1132
mods/    hello（INJECT 三连）、burst（REDIRECT）、painter（黑屏下从零画界面）
demo/    两个版本的完整运行记录
```

## 黑屏启动

没有 mod 动笔时，第一帧输出长这样，纯黑：

```
【第 1 帧 · 原版 背景/世界/UI/UI文字 四层已熄灭，屏幕上只有 mod 自己画的】
+----------------------------------------------------------+
|                                                          |
+----------------------------------------------------------+
```

`mods/painter` 演示了「一大坨 mod 显示出来」：标题、山脉、地面、血条、聊天框，全部由 mod 在 `onRender` 回调里自己画，原版一个像素都不参与。两个版本各三帧的完整输出在 `demo/` 里。

## 几个坑

详细的在代码注释里，挑要紧的说：

- 游戏的绘制层必须是 `invokestatic` 且只吃版本无关的 `FrameBuffer`。黑屏 handler 住在 loader 里，不走 `ModRemapper`，签名带游戏类型就是 1.20.1 上的 `NoSuchMethodError`
- REDIRECT 实例调用时，接收者 `this` 已经在操作数栈上，handler 第一个形参必须接走它，否则栈失衡 `VerifyError`
- TAIL 注入要等方法体遍历完再发射。流式遍历不知道哪个 return 是最后一个，边看边插会插到倒数第二个出口
- CJK 字符在等宽终端占两格，`FrameBuffer.text()` 给宽字符垫了右半格占位符，不然边框全歪
- `Map.of` 最多 10 对，1.20.1 映射表有 12 对，得用 `Map.ofEntries`

## 许可

AGPL-3.0，见 `LICENSE`。
