package com.dorm.duty;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 底部专属菜单（4 Tab）：今日值日 / 排班 / 成员 / 设置。
 * 每个 Tab 独立页面，卡片式布局，纯原生 View 无 AndroidX。
 */
public class MainActivity extends Activity {

    private DataStore store;
    private CalendarSync calSync;

    private LinearLayout root;
    private FrameLayout pageHost;
    private ScrollView svToday, svPlan, svMembers, svSettings;
    private LinearLayout pageToday, pagePlan, pageMembers, pageSettings;
    private TextView titleMain, hintView;
    private final TextView[] tabIcons = new TextView[4];
    private final TextView[] tabLabels = new TextView[4];

    private static final String[] TAB_ICONS = {"📋", "🗓", "👥", "⚙"};
    private static final String[] TAB_LABELS = {"今日值日", "排班", "成员", "设置"};

    private static final int C_MAIN = Color.parseColor("#1B5E20");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("M月d日 EEE");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();
        store = new DataStore(this);
        calSync = new CalendarSync(this, store);
        buildUi();
        selectTab(0);
        requestPermissions();
        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAll();
    }

    private void installCrashHandler() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            CrashLog.append(this, e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    // ============================ UI 骨架 ============================

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);

        root.addView(titleBar());

        pageHost = new FrameLayout(this);
        pageHost.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(pageHost);

        pageToday = newInnerPage();
        svToday = makePage(pageToday, 0);
        pagePlan = newInnerPage();
        svPlan = makePage(pagePlan, 1);
        pageMembers = newInnerPage();
        svMembers = makePage(pageMembers, 2);
        pageSettings = newInnerPage();
        svSettings = makePage(pageSettings, 3);

        root.addView(buildTabBar());
        setContentView(root);
    }

    private LinearLayout newInnerPage() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(6), 0, dp(12));
        return l;
    }

    private ScrollView makePage(LinearLayout inner, int index) {
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        sv.setFillViewport(true);
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sv.addView(inner);
        sv.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageHost.addView(sv);
        return sv;
    }

    private View titleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(12));
        titleMain = makeText(20, C_MAIN);
        titleMain.setTypeface(titleMain.getTypeface(), Typeface.BOLD);
        titleMain.setText("🏠 寝室值日 · " + store.roomName());
        titleMain.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(titleMain);
        hintView = makeText(12, Color.GRAY);
        hintView.setText(store.memberCount() + " 人寝室 · 每天 " + store.perDay() + " 人");
        bar.addView(hintView);
        return bar;
    }

    private View buildTabBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Color.WHITE);
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#E0E0E0"));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(line);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(6), 0, dp(8));
        bar.setBackgroundColor(Color.WHITE);
        wrap.addView(bar);
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tabIcons[i] = makeText(18, Color.parseColor("#9E9E9E"));
            tabIcons[i].setText(TAB_ICONS[i]);
            tabLabels[i] = makeText(11, Color.parseColor("#9E9E9E"));
            tabLabels[i].setText(TAB_LABELS[i]);
            tab.addView(tabIcons[i]);
            tab.addView(tabLabels[i]);
            tab.setOnClickListener(v -> selectTab(ti));
            bar.addView(tab);
        }
        return wrap;
    }

    /** 切换页面 + 刷新菜单高亮 */
    private void selectTab(int i) {
        currentTab = i;
        svToday.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        svPlan.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        svMembers.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        svSettings.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
        for (int k = 0; k < 4; k++) {
            boolean on = k == i;
            int c = on ? C_MAIN : Color.parseColor("#9E9E9E");
            tabIcons[k].setTextColor(c);
            tabLabels[k].setTextColor(c);
            tabIcons[k].setTypeface(tabIcons[k].getTypeface(), on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private int currentTab;

    // ============================ 页1：今日值日 ============================

    private void renderToday() {
        pageToday.removeAllViews();
        LocalDate today = LocalDate.now();
        List<String> names = store.dutyOf(today);
        boolean allDone = !names.isEmpty()
                && names.stream().allMatch(n -> store.isChecked(today.toString(), n));

        LinearLayout card = card();
        TextView date = makeText(13, Color.GRAY);
        date.setText("今天 · " + today.format(D_FMT));
        card.addView(date);
        TextView label = makeText(13, Color.GRAY);
        label.setText("今日值日");
        card.addView(label);
        TextView big = makeText(26, C_MAIN);
        big.setTypeface(big.getTypeface(), Typeface.BOLD);
        big.setText(names.isEmpty() ? "还没有人" : String.join("  ", names));
        card.addView(big);
        if (names.isEmpty()) {
            TextView tip = makeText(12, Color.GRAY);
            tip.setText("到「成员」页添加寝室成员后自动生成排班");
            card.addView(tip);
        }
        pageToday.addView(card);

        if (!names.isEmpty()) {
            final List<String> nn = names;
            final boolean wasAllDone = allDone;
            View btn = bigChip(allDone ? "✓ 今日值日完成 · 点我撤销" : "去签到",
                    allDone ? "#4CAF50" : "#2E7D32");
            btn.setOnClickListener(v -> {
                for (String n : nn) store.toggleCheck(today.toString(), n);
                renderAll();
                ToastSafe.show(this, wasAllDone ? "已清除签到" : "已签到 ✓");
            });
            pageToday.addView(btn);
        }

        LinearLayout next = card();
        TextView nh = makeText(13, Color.GRAY);
        nh.setText("接下来几天");
        next.addView(nh);
        String[] dd = {"明天", "后天", "大后天"};
        for (int i = 1; i <= 3; i++) {
            LocalDate d = today.plusDays(i);
            List<String> n2 = store.dutyOf(d);
            next.addView(hRow(dd[i - 1], n2.isEmpty() ? "—" : String.join("、", n2)));
        }
        pageToday.addView(next);

        TextView rule = makeText(12, Color.GRAY);
        rule.setText("轮换规则：从起始日期开始，按成员顺序每天 " + store.perDay()
                + " 人值日，轮完一圈循环。");
        rule.setPadding(0, dp(4), 0, 0);
        pageToday.addView(rule);
    }

    // ============================ 页2：排班 ============================

    private void renderPlan() {
        pagePlan.removeAllViews();
        pagePlan.addView(sectionHeader("未来 7 天排班"));
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            List<String> names = store.dutyOf(d);
            boolean allDone = !names.isEmpty()
                    && names.stream().allMatch(n -> store.isChecked(d.toString(), n));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(10) * 1f);
            bg.setStroke(dp(1), i == 0 ? Color.parseColor("#A5D6A7") : Color.parseColor("#E8F0E4"));
            row.setBackground(bg);
            int p = dp(12);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rlp);

            TextView date = makeText(14, i == 0 ? C_MAIN : Color.DKGRAY);
            date.setTypeface(date.getTypeface(), i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            date.setText((i == 0 ? "今天 · " : "") + d.format(D_FMT));
            date.setGravity(Gravity.START);
            date.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            row.addView(date);

            TextView duty = makeText(14, Color.parseColor("#212121"));
            duty.setTypeface(duty.getTypeface(), Typeface.BOLD);
            duty.setGravity(Gravity.END);
            duty.setText(names.isEmpty() ? "—" : String.join("、", names));
            duty.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(duty);

            if (i == 0 && !names.isEmpty()) {
                row.addView(chip(allDone ? "✓ 已签" : "未签", allDone ? "#4CAF50" : "#E65100"));
            }
            pagePlan.addView(row);
        }
        TextView note = makeText(12, Color.GRAY);
        note.setText("完整排班在「设置」页可一键同步到系统日历。");
        note.setPadding(0, dp(4), 0, 0);
        pagePlan.addView(note);
    }

    // ============================ 页3：成员 ============================

    private void renderMembers() {
        pageMembers.removeAllViews();
        pageMembers.addView(sectionHeader("成员 · 值日名单"));
        List<DataStore.Member> ms = store.members();
        for (int i = 0; i < ms.size(); i++) {
            final int idx = i;
            final DataStore.Member m = ms.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(10) * 1f);
            bg.setStroke(dp(1), Color.parseColor("#E8F0E4"));
            row.setBackground(bg);
            int p = dp(10);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rlp);

            // 姓名首字圆形头像
            TextView avatar = makeText(15, Color.WHITE);
            avatar.setTypeface(avatar.getTypeface(), Typeface.BOLD);
            avatar.setGravity(Gravity.CENTER);
            avatar.setText(String.valueOf(m.name.isEmpty() ? '·' : m.name.charAt(0)));
            GradientDrawable ab = new GradientDrawable();
            ab.setShape(GradientDrawable.OVAL);
            ab.setColor(Color.parseColor(m.leader ? "#1B5E20" : "#90A4AE"));
            avatar.setBackground(ab);
            int as = dp(38);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(as, as);
            alp.setMargins(0, 0, dp(10), 0);
            row.addView(avatar, alp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView nm = makeText(15, Color.parseColor("#212121"));
            nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
            nm.setText(m.name);
            info.addView(nm);
            if (m.leader) {
                TextView l = makeText(11, C_MAIN);
                l.setText("寝室长 👑");
                info.addView(l);
            }
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(info);

            if (!m.leader) {
                View setL = chip("设为寝室长", "#1976D2");
                setL.setOnClickListener(v -> {
                    store.setLeader(idx, true);
                    renderAll();
                    ToastSafe.show(this, m.name + " 已是寝室长");
                });
                row.addView(setL);
            }
            View del = chip("删除", "#C62828");
            del.setOnClickListener(v -> {
                store.removeMember(idx);
                renderAll();
                ToastSafe.show(this, m.name + " 已移出名单");
            });
            row.addView(del);

            pageMembers.addView(row);
        }

        if (ms.size() < 4) {
            TextView warn = makeText(12, Color.parseColor("#C62828"));
            warn.setText("⚠ 寝室至少 4 人（4~12 人间）");
            warn.setPadding(0, dp(4), 0, dp(4));
            pageMembers.addView(warn);
        }

        final EditText et = new EditText(this);
        et.setHint("新成员姓名（最多 12 字）");
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        et.setBackgroundColor(Color.WHITE);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        pageMembers.addView(et);

        View add = bigChip("＋ 添加成员", "#2E7D32");
        add.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) return;
            if (store.memberCount() >= 12) {
                ToastSafe.show(this, "最多 12 人");
                return;
            }
            if (store.members().stream().anyMatch(x -> x.name.equals(name))) {
                ToastSafe.show(this, "该成员已在名单中");
                return;
            }
            store.addMember(name);
            et.setText("");
            renderAll();
            ToastSafe.show(this, name + " 已加入");
        });
        pageMembers.addView(add);

        TextView tip = makeText(12, Color.GRAY);
        tip.setText("值日按此名单顺序轮转，寝室长仅作为标识。");
        tip.setPadding(0, dp(8), 0, 0);
        pageMembers.addView(tip);
    }

    // ============================ 页4：设置 ============================

    private void renderSettings() {
        pageSettings.removeAllViews();
        pageSettings.addView(sectionHeader("设置 · 日历同步"));

        View setName = chip("重命名寝室 · 当前 [" + store.roomName() + "]", "#1976D2");
        setName.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setText(store.roomName());
            et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
            new AlertDialog.Builder(this)
                    .setTitle("寝室名称")
                    .setView(et)
                    .setPositiveButton("保存", (d, w) -> {
                        String n = et.getText().toString().trim();
                        if (!n.isEmpty()) {
                            store.setRoomName(n);
                            renderAll();
                        }
                    })
                    .show();
        });
        pageSettings.addView(setName);

        View setDate = chip("值日起始日期：" + store.startDate().format(D_FMT), "#1976D2");
        setDate.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setHint("如 2026-09-21 或 今天/明天");
            new AlertDialog.Builder(this)
                    .setTitle("排班起始日期")
                    .setView(et)
                    .setPositiveButton("保存", (d, w) -> {
                        String s = et.getText().toString().trim();
                        LocalDate dd = parseDate(s);
                        if (dd != null) {
                            store.setStartDate(dd);
                            renderAll();
                        }
                    })
                    .show();
        });
        pageSettings.addView(setDate);

        View startOver = chip("重置轮换（从第 1 位重新开始）", "#E65100");
        startOver.setOnClickListener(v -> {
            store.setStartIndex(0);
            renderAll();
            ToastSafe.show(this, "轮换已重置");
        });
        pageSettings.addView(startOver);

        View sync = chip("📅 写入系统日历（未来 7 天）", "#2E7D32");
        sync.setOnClickListener(v -> {
            int n = calSync.syncDuty(7);
            if (n < 0) {
                ToastSafe.show(this, "日历不可用：请授予日历权限后重试");
                requestPermissions();
            } else {
                ToastSafe.show(this, "已写入 " + n + " 条值日事件");
            }
        });
        pageSettings.addView(sync);

        View verify = chip("🛡 校验并修复本地日历（防篡改）", "#2E7D32");
        verify.setOnClickListener(v -> {
            int[] r = calSync.verifyAndRepair(7);
            if (r == null) {
                ToastSafe.show(this, "日历不可用：请授予日历权限后重试");
                requestPermissions();
            } else {
                ToastSafe.show(this, String.format("校验完成：补齐 %d · 重写 %d · 删除 %d",
                        r[0], r[1], r[2]));
            }
        });
        pageSettings.addView(verify);

        String crashSummary = CrashLog.lastSummary(this);
        View crashRow = chip(crashSummary.isEmpty()
                ? "💾 崩溃日志（无记录）"
                : "💾 最近崩溃：" + crashSummary,
                crashSummary.isEmpty() ? "#607D8B" : "#B71C1C");
        crashRow.setOnClickListener(v -> {
            String full = CrashLog.last(this);
            new AlertDialog.Builder(this)
                    .setTitle("最近一次崩溃日志")
                    .setMessage(full.isEmpty() ? "暂无崩溃记录" : full)
                    .setPositiveButton("知道了", null)
                    .show();
        });
        pageSettings.addView(crashRow);

        TextView note = makeText(12, Color.GRAY);
        note.setText("排班数据以 App 内存储为准；系统日历仅作展示与提醒，"
                + "被手动删改时点「校验并修复」一键还原。");
        note.setPadding(0, dp(10), 0, 0);
        pageSettings.addView(note);
    }

    // ============================ 渲染调度 ============================

    private void renderAll() {
        titleMain.setText("🏠 寝室值日 · " + store.roomName());
        hintView.setText(store.memberCount() + " 人寝室 · 每天 " + store.perDay() + " 人");
        renderToday();
        renderPlan();
        renderMembers();
        renderSettings();
    }

    // ============================ 组件 & 工具 ============================

    private View sectionHeader(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(4), 0, dp(8));
        TextView h = makeText(15, Color.parseColor("#37474F"));
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        h.setText(text);
        wrap.addView(h);
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#2E7D32"));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2));
        line.setLayoutParams(llp);
        wrap.addView(line);
        return wrap;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12) * 1f);
        bg.setStroke(dp(1), Color.parseColor("#E8F0E4"));
        c.setBackground(bg);
        int p = dp(14);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    /** 横向小行：左灰标签 + 右加粗值 */
    private View hRow(String left, String right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView a = makeText(13, Color.GRAY);
        a.setText(left);
        r.addView(a);
        TextView b = makeText(13, Color.parseColor("#212121"));
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setText(right);
        b.setGravity(Gravity.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        r.addView(b, blp);
        return r;
    }

    private View chip(String text, String color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(16) * 1f);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private View bigChip(String text, String color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(14) * 1f);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeText(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private int dp(int v) {
        return (int) Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LocalDate parseDate(String s) {
        try {
            if (s.equals("今天")) return LocalDate.now();
            if (s.equals("明天")) return LocalDate.now().plusDays(1);
            return LocalDate.parse(s);
        } catch (Exception e) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception e2) {
                ToastSafe.show(this, "日期格式无效，请用 2026-09-21 / 今天 / 明天");
                return null;
            }
        }
    }

    private void requestPermissions() {
        try {
            requestPermissions(new String[]{
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
                    Manifest.permission.POST_NOTIFICATIONS}, 100);
        } catch (Exception ignored) {}
    }

    private static class ToastSafe {
        static void show(Activity a, String msg) {
            android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
