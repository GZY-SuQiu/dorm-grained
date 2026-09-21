package com.dorm.duty;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 主题管理：5 套完整主题（整套配色，不只是单一颜色）。
 * 选择持久化在 SharedPreferences（与 DataStore 同文件）。
 */
public class ThemeManager {

    public static class T {
        public final String name;
        public final int main;        // 主色（按钮 / 选中 / 强调）
        public final int mainDark;    // 标题 / 深色强调文字
        public final int accent;      // 浅色强调（今天行边框等）
        public final int windowBg;    // 页面背景
        public final int cardBg;      // 卡片 / 菜单背景
        public final int cardStroke;  // 卡片描边 / 分隔线
        public final int textPrimary; // 主文字
        public final int textSecondary;// 次要文字
        public final int tabOffBg;    // 菜单未选中圆底
        public final int tabOffFg;    // 菜单未选中文字
        public final boolean dark;

        T(String name, int main, int mainDark, int accent, int windowBg, int cardBg,
          int cardStroke, int textPrimary, int textSecondary, int tabOffBg, int tabOffFg, boolean dark) {
            this.name = name;
            this.main = main;
            this.mainDark = mainDark;
            this.accent = accent;
            this.windowBg = windowBg;
            this.cardBg = cardBg;
            this.cardStroke = cardStroke;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.tabOffBg = tabOffBg;
            this.tabOffFg = tabOffFg;
            this.dark = dark;
        }
    }

    public static final T[] ALL = {
            new T("绿野（默认）", 0xFF2E7D32, 0xFF1B5E20, 0xFFA5D6A7,
                    0xFFF5F5F5, 0xFFFFFFFF, 0xFFE8F0E4, 0xFF212121, 0xFF9E9E9E,
                    0xFFECEFF1, 0xFF546E7A, false),
            new T("海洋蓝", 0xFF1976D2, 0xFF0D47A1, 0xFF90CAF9,
                    0xFFF3F6FB, 0xFFFFFFFF, 0xFFE1EAF6, 0xFF212121, 0xFF90A4AE,
                    0xFFE3F2FD, 0xFF455A64, false),
            new T("琥珀橙", 0xFFE65100, 0xFFBF360C, 0xFFFFAB91,
                    0xFFFFF8F5, 0xFFFFFFFF, 0xFFFBE9E0, 0xFF212121, 0xFF8D6E63,
                    0xFFEFEBE9, 0xFF5D4037, false),
            new T("紫罗兰", 0xFF6A1B9A, 0xFF4A148C, 0xFFCE93D8,
                    0xFFF7F3FA, 0xFFFFFFFF, 0xFFEDE7F6, 0xFF212121, 0xFF9575CD,
                    0xFFEDE7F6, 0xFF4527A8, false),
            new T("深色夜", 0xFF4FC3F7, 0xFFB3E5FC, 0xFF0288D9,
                    0xFF121212, 0xFF1E1E1E, 0xFF2F2F2F, 0xFFFAFAFA, 0xFF9E9E9E,
                    0xFF2C2C2C, 0xFF90A4AE, true),
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(DataStore.TAG, Context.MODE_PRIVATE);
    }

    public static int index(Context c) {
        int i = sp(c).getInt("theme_index", 0);
        if (i < 0 || i >= ALL.length) i = 0;
        return i;
    }

    public static T get(Context c) {
        return ALL[index(c)];
    }

    public static void set(Context c, int i) {
        if (i < 0 || i >= ALL.length) return;
        sp(c).edit().putInt("theme_index", i).apply();
    }
}
