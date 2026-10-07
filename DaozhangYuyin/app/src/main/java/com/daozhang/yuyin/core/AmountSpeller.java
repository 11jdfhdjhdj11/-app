package com.daozhang.yuyin.core;

/**
 * 金额（单位：分）转中文读法。纯 Java、无 Android 依赖，便于单元测试。
 *
 * 口语模式：19.9 → 十九块九；19.95 → 十九块九毛五；19.05 → 十九块零五分；0.5 → 五毛
 * 标准模式：19.9 → 十九点九元；19.05 → 十九点零五元；0.01 → 零点零一元
 */
public final class AmountSpeller {

    public static final long MIN_FEN = 1L;
    public static final long MAX_FEN = 9_999_999L; // 99,999.99 元

    private static final String[] DIG = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final int[] UNIT_VAL = {1000, 100, 10, 1};
    private static final String[] UNIT_STR = {"千", "百", "十", ""};

    public enum Style { COLLOQUIAL, STANDARD }

    public static final class Options {
        public Style style = Style.COLLOQUIAL;
        /** 口语模式下 2 读作“两”：两块、两百、两千、两万 */
        public boolean useLiang = true;
        /** 整数金额末尾加“整”，如“二十块整” */
        public boolean appendZheng = false;

        public Options() {}

        public Options(Style style, boolean useLiang, boolean appendZheng) {
            this.style = style;
            this.useLiang = useLiang;
            this.appendZheng = appendZheng;
        }
    }

    private AmountSpeller() {}

    /** 解析用户输入的金额文本为“分”。非法返回 -1。 */
    public static long parseFen(String text) {
        if (text == null) return -1;
        String s = text.trim();
        if (!s.matches("\\d{1,5}(\\.\\d{1,2})?")) return -1;
        int dot = s.indexOf('.');
        long yuan = Long.parseLong(dot < 0 ? s : s.substring(0, dot));
        long fen = 0;
        if (dot >= 0) {
            String dec = s.substring(dot + 1);
            if (dec.length() == 1) dec = dec + "0";
            fen = Long.parseLong(dec);
        }
        long total = yuan * 100 + fen;
        return (total < MIN_FEN || total > MAX_FEN) ? -1 : total;
    }

    /** 分 → “19.90” 形式的显示文本 */
    public static String formatYuan(long fen) {
        return (fen / 100) + "." + String.format(java.util.Locale.ROOT, "%02d", fen % 100);
    }

    public static String spell(long fen, Options opt) {
        if (fen < MIN_FEN || fen > MAX_FEN) {
            throw new IllegalArgumentException("金额超出范围: " + fen);
        }
        int yuan = (int) (fen / 100);
        int jiao = (int) (fen / 10 % 10);
        int cent = (int) (fen % 10);
        return opt.style == Style.STANDARD
                ? spellStandard(yuan, jiao, cent, opt)
                : spellColloquial(yuan, jiao, cent, opt);
    }

    private static String spellStandard(int yuan, int jiao, int cent, Options opt) {
        StringBuilder sb = new StringBuilder(integerToCn(yuan));
        if (jiao > 0 || cent > 0) {
            sb.append("点").append(DIG[jiao]);
            if (cent > 0) sb.append(DIG[cent]);
            sb.append("元");
        } else {
            sb.append("元");
            if (opt.appendZheng) sb.append("整");
        }
        return sb.toString();
    }

    private static String spellColloquial(int yuan, int jiao, int cent, Options opt) {
        StringBuilder sb = new StringBuilder();
        if (yuan > 0) {
            String y = integerToCn(yuan);
            if (opt.useLiang) y = applyLiang(y);
            sb.append(y).append("块");
            if (jiao == 0 && cent == 0) {
                if (opt.appendZheng) sb.append("整");
            } else if (cent == 0) {
                sb.append(digit(jiao, opt));                       // 十九块九
            } else if (jiao == 0) {
                sb.append("零").append(DIG[cent]).append("分");     // 十九块零五分
            } else {
                sb.append(digit(jiao, opt)).append("毛").append(DIG[cent]); // 十九块九毛五
            }
        } else {
            if (jiao > 0) {
                sb.append(digit(jiao, opt)).append("毛");          // 五毛 / 两毛
                if (cent > 0) sb.append(DIG[cent]);                 // 五毛五
            } else {
                sb.append(DIG[cent]).append("分");                  // 一分
            }
        }
        return sb.toString();
    }

    private static String digit(int d, Options opt) {
        return (d == 2 && opt.useLiang) ? "两" : DIG[d];
    }

    /** 0..99999 的整数读法，如 1005 → 一千零五，10050 → 一万零五十，15 → 十五 */
    static String integerToCn(int n) {
        if (n == 0) return "零";
        String s;
        if (n < 10000) {
            s = below10000(n);
        } else {
            int hi = n / 10000;
            int lo = n % 10000;
            StringBuilder sb = new StringBuilder(below10000(hi)).append("万");
            if (lo > 0) {
                if (lo < 1000) sb.append("零");
                sb.append(below10000(lo));
            }
            s = sb.toString();
        }
        if (s.startsWith("一十")) s = s.substring(1);
        return s;
    }

    private static String below10000(int n) {
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        boolean pendingZero = false;
        for (int i = 0; i < UNIT_VAL.length; i++) {
            int d = n / UNIT_VAL[i] % 10;
            if (d == 0) {
                if (started) pendingZero = true;
                continue;
            }
            if (pendingZero) {
                sb.append("零");
                pendingZero = false;
            }
            sb.append(DIG[d]).append(UNIT_STR[i]);
            started = true;
        }
        return sb.toString();
    }

    /** 二百/二千/二万 → 两百/两千/两万；单独的“二”（2 块）→ 两 */
    private static String applyLiang(String s) {
        if (s.equals("二")) return "两";
        return s.replace("二百", "两百").replace("二千", "两千").replace("二万", "两万");
    }

    /** 套用话术模板：前缀 + 金额读法 + 后缀 */
    public static String compose(String prefix, long fen, String suffix, Options opt) {
        return (prefix == null ? "" : prefix.trim()) + spell(fen, opt) + (suffix == null ? "" : suffix.trim());
    }
}
