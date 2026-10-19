package net.nico.miniloader.api;

/**
 * 加载器提供的「帧」—— 一块字符网格。mod 想让用户看见什么，就往这里画什么。
 *
 * 为什么由加载器提供、不放游戏里：这块画布是版本无关的。
 * 游戏自带的绘制会被黑屏启动在织入期整条熄灭（见 loader 的 BlankBoot），
 * 真正上屏的内容只来自 mod 在 onRender 里的笔。
 *
 * 踩过的坑：中日韩字符在等宽终端占 2 格。text() 把宽字符写进一格、
 * 右边垫一格 PAD（渲染时跳过），否则右边框全歪。
 * 够用级别，不搞完整 Unicode 宽度表。
 */
public final class FrameBuffer {

    /** 宽字符的右半格占位，渲染时跳过。 */
    private static final char PAD = '';

    private final int width;
    private final int height;
    private final char[][] cells;

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new char[height][width];
        clear();
    }

    public void clear() {
        fill(' ');
    }

    public void fill(char c) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = c;
            }
        }
    }

    /** 从 (x,y) 起写一行字。越界直接忽略/截断，不抛异常 —— mod 画飞了也不该把游戏带崩。 */
    public void text(int x, int y, String s) {
        if (y < 0 || y >= height || x < 0) {
            return;
        }
        int cursor = x;
        for (int i = 0; i < s.length() && cursor < width; i++) {
            char c = s.charAt(i);
            if (isWide(c)) {
                if (cursor + 1 >= width) {
                    break; // 右边放不下整个宽字符，宁可不画
                }
                cells[y][cursor] = c;
                cells[y][cursor + 1] = PAD;
                cursor += 2;
            } else {
                cells[y][cursor] = c;
                cursor++;
            }
        }
    }

    /** 画个框。 (x,y) 是左上角，w×h 含边框。 */
    public void box(int x, int y, int w, int h) {
        if (w < 2 || h < 2) {
            return;
        }
        for (int i = 0; i < w; i++) {
            put(x + i, y, '-');
            put(x + i, y + h - 1, '-');
        }
        for (int j = 0; j < h; j++) {
            put(x, y + j, '|');
            put(x + w - 1, y + j, '|');
        }
        put(x, y, '+');
        put(x + w - 1, y, '+');
        put(x, y + h - 1, '+');
        put(x + w - 1, y + h - 1, '+');
    }

    /** 进度条：[████░░]。value/max 决定填充长度。 */
    public void bar(int x, int y, int width, int value, int max) {
        if (width < 2) {
            return;
        }
        put(x, y, '[');
        int inner = width - 2;
        int filled = max <= 0 ? 0 : (int) Math.round((double) value / max * inner);
        for (int i = 0; i < inner; i++) {
            put(x + 1 + i, y, i < filled ? '█' : '░');
        }
        put(x + width - 1, y, ']');
    }

    private void put(int x, int y, char c) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        cells[y][x] = c;
    }

    private static boolean isWide(char c) {
        return c >= 0x2E80;
    }

    /** 拼成带边框的整帧字符串，直接 System.out.println 就是「上屏」。 */
    public String render() {
        StringBuilder out = new StringBuilder();
        out.append('+').append("-".repeat(width)).append("+\n");
        for (int y = 0; y < height; y++) {
            out.append('|');
            StringBuilder line = new StringBuilder();
            int displayWidth = 0;
            for (int x = 0; x < width; x++) {
                char c = cells[y][x];
                if (c == PAD) {
                    continue; // 宽字符的右半格，显示宽度已经算过了
                }
                line.append(c);
                displayWidth += isWide(c) ? 2 : 1;
            }
            while (displayWidth < width) {
                line.append(' ');
                displayWidth++;
            }
            out.append(line).append("|\n");
        }
        return out.append('+').append("-".repeat(width)).append("+").toString();
    }
}