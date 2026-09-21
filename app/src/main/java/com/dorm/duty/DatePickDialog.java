package com.dorm.duty;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 月历网格日期选择弹窗（参考常见日期选择器）：
 * 顶部大日期展示 + 上下月导航 + 周一为首 7 列网格 + 取消/确定。
 * 支持主题配色。
 */
public class DatePickDialog {

    public interface OnPicked { void onPicked(LocalDate d); }

    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("M月d日 EEE");

    private final Context ctx;
    private final OnPicked cb;
    private final ThemeManager.T t;
    private final Dialog dlg;

    private int year, month;
    private LocalDate selected;

    private TextView topYear, topBig, monthTitle;
    private GridLayout grid;

    public static void show(Context ctx, LocalDate initial, ThemeManager.T theme, OnPicked cb) {
        new DatePickDialog(ctx, initial, theme, cb).show();
    }

    private DatePickDialog(Context ctx, LocalDate initial, ThemeManager.T theme, OnPicked cb) {
        this.ctx = ctx;
        this.cb = cb;
        this.t = theme;
        this.selected = initial;
        YearMonth ym = YearMonth.from(initial);
        this.year = ym.getYear();
        this.month = ym.getMonthValue();
        this.dlg = new Dialog(ctx);
    }

    public void show() {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(buildView());
        dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dlg.show();
    }

    private LinearLayout buildView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rd = new GradientDrawable();
        rd.setColor(t.cardBg);
        rd.setCornerRadius(d(16) * 1f);
        root.setBackground(rd);

        // 顶部大日期面板
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(t.main);
        top.setPadding(d(20), d(16), d(20), d(14));
        topYear = new TextView(ctx);
        topYear.setTextSize(14);
        topYear.setTextColor(Color.WHITE);
        topYear.setAlpha(0.85f);
        topBig = new TextView(ctx);
        topBig.setTextSize(30);
        topBig.setTypeface(topBig.getTypeface(), Typeface.BOLD);
        topBig.setTextColor(Color.WHITE);
        top.addView(topYear);
        top.addView(topBig);
        root.addView(top);

        // 月导航行
        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(d(14), d(12), d(14), 0);
        TextView prev = arrowBtn("‹");
        prev.setOnClickListener(v -> shiftMonth(-1));
        monthTitle = new TextView(ctx);
        monthTitle.setTextSize(15);
        monthTitle.setTypeface(monthTitle.getTypeface(), Typeface.BOLD);
        monthTitle.setTextColor(t.textPrimary);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView next = arrowBtn("›");
        next.setOnClickListener(v -> shiftMonth(1));
        nav.addView(prev);
        nav.addView(monthTitle);
        nav.addView(next);
        root.addView(nav);

        // 星期表头（周一为首）
        LinearLayout week = new LinearLayout(ctx);
        week.setOrientation(LinearLayout.HORIZONTAL);
        week.setPadding(d(8), d(10), d(8), 0);
        for (String w : WEEK) {
            TextView tv = new TextView(ctx);
            tv.setText(w);
            tv.setTextSize(11);
            tv.setTextColor(t.textSecondary);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            week.addView(tv);
        }
        root.addView(week);

        // 7 列日期网格
        grid = new GridLayout(ctx);
        grid.setColumnCount(7);
        grid.setPadding(d(8), d(6), d(8), d(6));
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 取消 / 确定
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(d(16), d(10), d(16), d(16));
        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setTextColor(t.textSecondary);
        cancel.setGravity(Gravity.CENTER);
        cancel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        cancel.setOnClickListener(v -> dlg.dismiss());
        TextView ok = new TextView(ctx);
        ok.setText("确定");
        ok.setTextSize(15);
        ok.setTypeface(ok.getTypeface(), Typeface.BOLD);
        ok.setTextColor(t.main);
        ok.setGravity(Gravity.CENTER);
        ok.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ok.setOnClickListener(v -> {
            if (cb != null) cb.onPicked(selected);
            dlg.dismiss();
        });
        btns.addView(cancel);
        btns.addView(ok);
        root.addView(btns);

        renderGrid();
        return root;
    }

    private TextView arrowBtn(String glyph) {
        TextView tv = new TextView(ctx);
        tv.setText(glyph);
        tv.setTextSize(22);
        tv.setTextColor(t.textSecondary);
        tv.setGravity(Gravity.CENTER);
        int sz = d(32);
        tv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        return tv;
    }

    private void shiftMonth(int delta) {
        int m = month + delta;
        int y = year;
        if (m < 1) { m = 12; y--; }
        if (m > 12) { m = 1; y++; }
        this.month = m;
        this.year = y;
        renderGrid();
    }

    private void renderGrid() {
        topYear.setText(selected.getYear() + "年");
        topBig.setText(selected.format(D_FMT));
        monthTitle.setText(year + "年" + month + "月");
        grid.removeAllViews();

        LocalDate first = YearMonth.of(year, month).atDay(1);
        int lead = first.getDayOfWeek().getValue() - 1; // 周一为首
        LocalDate cur = first.minusDays(lead);

        for (int i = 0; i < 42; i++) {
            final LocalDate d0 = cur;
            boolean other = d0.getYear() != year || d0.getMonthValue() != month;
            boolean isSel = d0.equals(selected);
            boolean isToday = d0.equals(LocalDate.now());

            LinearLayout cell = new LinearLayout(ctx);
            cell.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = d(44);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cell.setLayoutParams(glp);

            TextView tv = new TextView(ctx);
            tv.setText(String.valueOf(d0.getDayOfMonth()));
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            int tsz = d(36);
            tv.setLayoutParams(new LinearLayout.LayoutParams(tsz, tsz));

            if (isSel) {
                GradientDrawable cd = new GradientDrawable();
                cd.setShape(GradientDrawable.OVAL);
                cd.setColor(t.main);
                tv.setBackground(cd);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            } else {
                tv.setTextColor(other ? t.textSecondary : t.textPrimary);
                if (isToday) {
                    GradientDrawable ring = new GradientDrawable();
                    ring.setShape(GradientDrawable.OVAL);
                    ring.setColor(Color.TRANSPARENT);
                    ring.setStroke(d(1), t.main);
                    tv.setBackground(ring);
                }
                if (other) tv.setAlpha(0.4f);
            }
            cell.addView(tv);
            cell.setOnClickListener(v -> {
                selected = d0;
                renderGrid();
            });
            grid.addView(cell);
            cur = cur.plusDays(1);
        }
    }

    private int d(int v) {
        return (int) Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
