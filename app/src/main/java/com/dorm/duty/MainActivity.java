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
 * 设计语言：白卡片 + 分组列表 + 统一圆底字符图标，主色 #2E7D32。
 */
public class MainActivity extends Activity {

    private DataStore store;
    private CalendarSync calSync;

    private LinearLayout root;
    private FrameLayout pageHost;
    private ScrollView svToday, svPlan, svMembers, svSettings, svAbout;
    private LinearLayout pageToday, pagePlan, pageMembers, pageSettings, pageAbout;
    private TextView titleMain, hintView;
    private final TextView[] tabIcons = new TextView[5];
    private final TextView[] tabLabels = new TextView[5];
    private int currentTab;

    private static final String[] TAB_LABELS = {"今日值日", "排班", "成员", "设置", "关于"};
    private static final char[] TAB_CHARS = {'值', '排', '员', '设', '著'};

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("M月d日 EEE");

    private ThemeManager.T t;
    private LinearLayout tabWrap;
    private LinearLayout tabInner;
    private View tabLine;

    private static final String PRIVACY_TEXT =
            "用户隐私协议\n\n"
            + "1. 本产品只在本地运行，您的个人信息（寝室成员名单、排班安排、签到记录）"
            + "仅保存在您的设备上，不会上传到任何云端服务器。\n"
            + "2. 应用仅在您主动点击「写入系统日历」时访问系统日历，且只写入本地日历"
            + "「寝室值日表」，不会读取其他日历数据。\n"
            + "3. 应用不包含广告、追踪或任何形式的用户画像。\n"
            + "4. 卸载本应用后，本地数据将全部清除。\n\n"
            + "请您在使用前仔细阅读。点击「接受」即表示您已了解并同意以上内容；"
            + "点击「拒绝」将立即退出本应用。";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();
        store = new DataStore(this);
        calSync = new CalendarSync(this, store);
        t = ThemeManager.get(this);
        buildUi();
        // Android 15+（targetSdk35）强制边到边：按系统 insets 避让状态栏/手势栏
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getSystemWindowInsets();
            int pad = dp(16);
            v.setPadding(pad, pad + sys.top, pad, pad + sys.bottom);
            return insets;
        });
        root.requestApplyInsets();
        refreshTheme();
        selectTab(0);
        requestPermissions();
        renderAll();
        // 首次启动：隐私协议确认（接受才进入，拒绝立即退出）
        if (!store.isPrivacyAccepted()) showPrivacyDialog();
    }

    /** 隐私协议：只弹一次，点接受才继续，点拒绝立即退出 */
    private void showPrivacyDialog() {
        TextView tv = new TextView(this);
        tv.setTextSize(14);
        tv.setLineSpacing(dp(4), 1f);
        tv.setText(PRIVACY_TEXT);
        int p = dp(20);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("用户隐私协议")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("接受", (d, w) -> store.setPrivacyAccepted(true))
                .setNegativeButton("拒绝", (d, w) -> finishAffinity())
                .show();
    }

    private void dialogPrivacy() {
        TextView tv = new TextView(this);
        tv.setTextSize(14);
        tv.setLineSpacing(dp(4), 1f);
        tv.setText(PRIVACY_TEXT);
        int p = dp(20);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("用户隐私协议")
                .setView(sv)
                .setPositiveButton("我同意", null)
                .show();
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

    /** 切换主题后重刷全局配色（背景、状态栏、底部菜单） */
    private void refreshTheme() {
        root.setBackgroundColor(t.windowBg);
        getWindow().setStatusBarColor(t.main);
        if (tabWrap != null) {
            tabWrap.setBackgroundColor(t.cardBg);
            tabInner.setBackgroundColor(t.cardBg);
            tabLine.setBackgroundColor(t.cardStroke);
        }
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
        pageAbout = newInnerPage();
        svAbout = makePage(pageAbout, 4);

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
        titleMain = makeText(20, t.mainDark);
        titleMain.setTypeface(titleMain.getTypeface(), Typeface.BOLD);
        titleMain.setText("🏠 寝室值日 · " + store.roomName());
        titleMain.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(titleMain);
        hintView = makeText(12, t.textSecondary);
        hintView.setText(store.memberCount() + " 人 · 每天 " + store.perDay() + " 人值日");
        bar.addView(hintView);
        return bar;
    }

    /** 底部菜单：统一圆底字符图标，选中绿色、未选中浅灰，高度固定对齐 */
    private View buildTabBar() {
        tabWrap = new LinearLayout(this);
        tabWrap.setOrientation(LinearLayout.VERTICAL);
        tabWrap.setBackgroundColor(t.cardBg);
        tabLine = new View(this);
        tabLine.setBackgroundColor(t.cardStroke);
        tabLine.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        tabWrap.addView(tabLine);
        tabInner = new LinearLayout(this);
        tabInner.setOrientation(LinearLayout.HORIZONTAL);
        tabInner.setGravity(Gravity.CENTER_VERTICAL);
        tabInner.setPadding(0, dp(8), 0, dp(4));
        tabInner.setBackgroundColor(t.cardBg);
        tabWrap.addView(tabInner);
        for (int i = 0; i < 5; i++) {
            final int ti = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView circle = new TextView(this);
            circle.setText(String.valueOf(TAB_CHARS[i]));
            circle.setTextSize(15);
            circle.setTypeface(circle.getTypeface(), Typeface.BOLD);
            circle.setGravity(Gravity.CENTER);
            GradientDrawable cd = new GradientDrawable();
            cd.setShape(GradientDrawable.OVAL);
            cd.setColor(t.tabOffBg);
            circle.setBackground(cd);
            int cs = dp(38);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cs, cs);
            tab.addView(circle, clp);
            circle.setTextColor(t.tabOffFg);
            tabIcons[i] = circle;

            tabLabels[i] = makeText(11, t.textSecondary);
            tabLabels[i].setText(TAB_LABELS[i]);
            tabLabels[i].setPadding(0, dp(4), 0, 0);
            tab.addView(tabLabels[i]);

            tab.setOnClickListener(v -> selectTab(ti));
            tabInner.addView(tab);
        }
        return tabWrap;
    }

    private void selectTab(int i) {
        currentTab = i;
        svToday.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        svPlan.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        svMembers.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        svSettings.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
        svAbout.setVisibility(i == 4 ? View.VISIBLE : View.GONE);
        for (int k = 0; k < 5; k++) {
            boolean on = k == i;
            ((GradientDrawable) tabIcons[k].getBackground())
                    .setColor(on ? t.main : t.tabOffBg);
            tabIcons[k].setTextColor(on ? Color.WHITE : t.tabOffFg);
            tabLabels[k].setTextColor(on ? t.mainDark : t.textSecondary);
            tabLabels[k].setTypeface(tabLabels[k].getTypeface(), on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ============================ 页1：今日值日 ============================

    private void renderToday() {
        pageToday.removeAllViews();
        LocalDate today = LocalDate.now();
        List<String> names = store.dutyOf(today);
        boolean allDone = !names.isEmpty()
                && names.stream().allMatch(n -> store.isChecked(today.toString(), n));

        LinearLayout card = card();
        TextView date = makeText(13, t.textSecondary);
        date.setText("今天 · " + today.format(D_FMT));
        card.addView(date);
        TextView label = makeText(13, t.textSecondary);
        label.setText("今日值日");
        card.addView(label);
        TextView big = makeText(28, t.mainDark);
        big.setTypeface(big.getTypeface(), Typeface.BOLD);
        big.setText(names.isEmpty() ? "还没有人" : String.join("  ", names));
        card.addView(big);
        if (names.isEmpty()) {
            TextView tip = makeText(12, t.textSecondary);
            tip.setText("到「成员」页添加寝室成员后自动生成排班");
            card.addView(tip);
        }
        pageToday.addView(card);

        if (!names.isEmpty()) {
            final List<String> nn = names;
            final boolean wasAllDone = allDone;
            View btn = bigChip(allDone ? "✓ 今日值日完成 · 点我撤销" : "去签到",
                    allDone ? 0xFF4CAF50 : t.main);
            btn.setOnClickListener(v -> {
                for (String n : nn) store.toggleCheck(today.toString(), n);
                renderAll();
                ToastSafe.show(this, wasAllDone ? "已清除签到" : "已签到 ✓");
            });
            pageToday.addView(btn);
        }

        LinearLayout next = card();
        TextView nh = makeText(13, t.textSecondary);
        nh.setText("接下来几天");
        next.addView(nh);
        String[] dd = {"明天", "后天", "大后天"};
        for (int i = 1; i <= 3; i++) {
            LocalDate d = today.plusDays(i);
            List<String> n2 = store.dutyOf(d);
            next.addView(hRow(dd[i - 1], n2.isEmpty() ? "—" : String.join("、", n2)));
        }
        pageToday.addView(next);

        TextView rule = makeText(12, t.textSecondary);
        rule.setText("轮换规则：从起始日期开始，按成员顺序每天 " + store.perDay()
                + " 人值日，轮完一圈循环。");
        rule.setPadding(0, dp(4), 0, 0);
        pageToday.addView(rule);
    }

    // ============================ 页2：排班 ============================

    private void renderPlan() {
        pagePlan.removeAllViews();
        pagePlan.addView(sectionHeader("未来 7 天"));
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
            bg.setColor(t.cardBg);
            bg.setCornerRadius(dp(10) * 1f);
            bg.setStroke(dp(1), i == 0 ? t.accent : t.cardStroke);
            row.setBackground(bg);
            int p = dp(12);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rlp);

            TextView date = makeText(14, i == 0 ? t.mainDark : t.textSecondary);
            date.setTypeface(date.getTypeface(), i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            date.setText((i == 0 ? "今天 · " : "") + d.format(D_FMT));
            date.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            row.addView(date);

            TextView duty = makeText(14, t.textPrimary);
            duty.setTypeface(duty.getTypeface(), Typeface.BOLD);
            duty.setGravity(Gravity.END);
            duty.setText(names.isEmpty() ? "—" : String.join("、", names));
            duty.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(duty);

            if (i == 0 && !names.isEmpty()) {
                row.addView(softChip(allDone ? "✓ 已签" : "未签",
                        allDone ? t.accent : 0xFFFFF3E0,
                        allDone ? t.main : 0xFFE65100));
            }
            pagePlan.addView(row);
        }
        TextView note = makeText(12, t.textSecondary);
        note.setText("完整排班可在「设置」页一键同步到系统日历。");
        note.setPadding(0, dp(4), 0, 0);
        pagePlan.addView(note);
    }

    // ============================ 页3：成员 ============================

    private void renderMembers() {
        pageMembers.removeAllViews();
        pageMembers.addView(sectionHeader("值日名单"));
        List<DataStore.Member> ms = store.members();

        TextView cnt = makeText(12, t.textSecondary);
        cnt.setText("共 " + ms.size() + " 人 · 最多 12 · 每天 " + store.perDay() + " 人值日"
                + (ms.size() < 4 ? "（至少 4 人）" : ""));
        pageMembers.addView(cnt);

        for (int i = 0; i < ms.size(); i++) {
            final int idx = i;
            final DataStore.Member m = ms.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(t.cardBg);
            bg.setCornerRadius(dp(10) * 1f);
            bg.setStroke(dp(1), t.cardStroke);
            row.setBackground(bg);
            int p = dp(10);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rlp);

            // 头像圆
            TextView avatar = new TextView(this);
            avatar.setText(String.valueOf(m.name.isEmpty() ? '·' : m.name.charAt(0)));
            avatar.setTextSize(15);
            avatar.setTypeface(avatar.getTypeface(), Typeface.BOLD);
            avatar.setGravity(Gravity.CENTER);
            avatar.setTextColor(m.leader ? Color.WHITE : t.tabOffFg);
            GradientDrawable ab = new GradientDrawable();
            ab.setShape(GradientDrawable.OVAL);
            ab.setColor(m.leader ? t.main : t.tabOffBg);
            avatar.setBackground(ab);
            int as = dp(40);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(as, as);
            alp.setMargins(0, 0, dp(12), 0);
            row.addView(avatar, alp);

            // 姓名 + 状态
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView nm = makeText(15, t.textPrimary);
            nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
            nm.setText(m.name);
            info.addView(nm);
            TextView st = makeText(11, m.leader ? t.main : t.textSecondary);
            st.setText(m.leader ? "寝室长" : "成员");
            info.addView(st);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(info);

            if (!m.leader) {
                View setL = softChip("设为寝室长", 0xFFE3F2FD, 0xFF1976D2);
                setL.setOnClickListener(v -> {
                    store.setLeader(idx, true);
                    renderAll();
                    ToastSafe.show(this, m.name + " 已是寝室长");
                });
                row.addView(setL);
                View del = softChip("删除", 0xFFFFEBEE, 0xFFC62828);
                del.setOnClickListener(v -> {
                    store.removeMember(idx);
                    renderAll();
                    ToastSafe.show(this, m.name + " 已移出名单");
                });
                row.addView(del);
            }
            pageMembers.addView(row);
        }

        // 添加成员：输入框 + 按钮同一行
        LinearLayout addCard = card();
        addCard.setOrientation(LinearLayout.HORIZONTAL);
        addCard.setGravity(Gravity.CENTER_VERTICAL);
        final EditText et = new EditText(this);
        et.setHint("新成员姓名");
        et.setTextSize(14);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        et.setBackground(null);
        et.setTextColor(t.textPrimary);
        et.setHintTextColor(t.textSecondary);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(elp);
        addCard.addView(et);
        View addBtn = solidButton("＋ 添加", t.main);
        addBtn.setOnClickListener(v -> {
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
        addCard.addView(addBtn);
        pageMembers.addView(addCard);
    }

    // ============================ 页4：设置 ============================

    private void renderSettings() {
        pageSettings.removeAllViews();
        pageSettings.addView(sectionHeader("设置"));

        // —— 基本设置 ——
        pageSettings.addView(groupHeader("基本设置"));
        LinearLayout g1 = card();
        g1.addView(settingRow("寝", t.tabOffBg, t.textPrimary,
                "寝室名称", "用于顶栏与日历事件显示", store.roomName(),
                v -> {
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
                }));
        g1.addView(divider());
        g1.addView(settingRow("日", t.tabOffBg, t.textPrimary,
                "值日起始日期", "排班从此日期开始轮转", store.startDate().format(D_FMT),
                v -> DatePickDialog.show(this, store.startDate(), t, dd -> {
                    store.setStartDate(dd);
                    renderAll();
                    ToastSafe.show(this, "起始日期已设为 " + dd.format(D_FMT));
                })));
        pageSettings.addView(g1);

        // —— 外观（多主题） ——
        pageSettings.addView(groupHeader("外观 · 主题"));
        LinearLayout g6 = card();
        int curIdx = ThemeManager.index(this);
        for (int i = 0; i < ThemeManager.ALL.length; i++) {
            final int ti = i;
            ThemeManager.T tt = ThemeManager.ALL[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = dp(12);
            row.setPadding(p, p, p, p);
            // 色板
            TextView sw = new TextView(this);
            sw.setText("");
            GradientDrawable cd = new GradientDrawable();
            cd.setShape(GradientDrawable.OVAL);
            cd.setColor(tt.main);
            sw.setBackground(cd);
            int cs = dp(22);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(cs, cs);
            slp.setMargins(0, 0, dp(12), 0);
            row.addView(sw, slp);
            TextView nm = makeText(14, t.textPrimary);
            nm.setText(tt.name);
            nm.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(nm);
            if (i == curIdx) {
                TextView ck = makeText(13, t.main);
                ck.setTypeface(ck.getTypeface(), Typeface.BOLD);
                ck.setText("✓ 使用中");
                row.addView(ck);
            }
            row.setOnClickListener(v -> {
                ThemeManager.set(this, ti);
                t = ThemeManager.get(this);
                refreshTheme();
                renderAll();
            });
            g6.addView(row);
            if (i > 0) g6.addView(divider());
        }
        pageSettings.addView(g6);

        // —— 轮换 ——
        pageSettings.addView(groupHeader("轮换"));
        LinearLayout g2 = card();
        g2.addView(settingRow("重", 0xFFFFF3E0, 0xFFE65100,
                "重置轮换", "从名单第 1 位重新开始排班", "重置",
                v -> {
                    store.setStartIndex(0);
                    renderAll();
                    ToastSafe.show(this, "轮换已重置");
                }));
        pageSettings.addView(g2);

        // —— 日历同步 ——
        pageSettings.addView(groupHeader("日历同步"));
        LinearLayout g3 = card();
        g3.addView(settingRow("历", t.tabOffBg, t.textPrimary,
                "写入系统日历", "未来 7 天 · 每天 7:00 提醒", "写入",
                v -> {
                    int n = calSync.syncDuty(7);
                    if (n < 0) {
                        ToastSafe.show(this, "日历不可用：请授予日历权限后重试");
                        requestPermissions();
                    } else {
                        ToastSafe.show(this, "已写入 " + n + " 条值日事件");
                    }
                }));
        g3.addView(divider());
        g3.addView(settingRow("盾", 0xFFE3F2FD, 0xFF1976D2,
                "校验并修复", "本地日历被删改时一键还原", "校验",
                v -> {
                    int[] r = calSync.verifyAndRepair(7);
                    if (r == null) {
                        ToastSafe.show(this, "日历不可用：请授予日历权限后重试");
                        requestPermissions();
                    } else {
                        ToastSafe.show(this, String.format("校验完成：补齐 %d · 重写 %d · 删除 %d",
                                r[0], r[1], r[2]));
                    }
                }));
        pageSettings.addView(g3);

        // —— 隐私 ——
        pageSettings.addView(groupHeader("隐私"));
        LinearLayout g5 = card();
        g5.addView(settingRow("保", t.tabOffBg, t.tabOffFg,
                "用户隐私协议", "再次查看隐私保护条款", "查看",
                v -> dialogPrivacy()));
        pageSettings.addView(g5);

        // —— 诊断 ——
        pageSettings.addView(groupHeader("诊断"));
        String crashSummary = CrashLog.lastSummary(this);
        LinearLayout g4 = card();
        g4.addView(settingRow("崩", crashSummary.isEmpty() ? t.tabOffBg : 0xFFFFEBEE,
                crashSummary.isEmpty() ? t.tabOffFg : 0xFFB71C1C,
                "崩溃日志", crashSummary.isEmpty() ? "暂无记录" : "最近：" + crashSummary, "查看",
                v -> {
                    String full = CrashLog.last(this);
                    new AlertDialog.Builder(this)
                            .setTitle("最近一次崩溃日志")
                            .setMessage(full.isEmpty() ? "暂无崩溃记录" : full)
                            .setPositiveButton("知道了", null)
                            .show();
                }));
        pageSettings.addView(g4);

        TextView note = makeText(12, t.textSecondary);
        note.setText("排班数据以 App 内存储为准；系统日历仅作展示与提醒。");
        note.setPadding(0, dp(10), 0, 0);
        pageSettings.addView(note);
    }

    // ============================ 页5：关于 ============================

    private void renderAbout() {
        pageAbout.removeAllViews();
        pageAbout.addView(sectionHeader("关于"));

        pageAbout.addView(groupHeader("作者声明"));
        LinearLayout ga = card();
        ga.addView(para("本产品为免费产品，未公开售卖。如果本产品为购买的请立即退款并差评处理！"
                + "如果被骗作者不承担责任，请尊重开发者！"));
        ga.addView(infoRow("开发工具", "Android-SDK, Gradle"));
        ga.addView(infoRow("开发环境", "PyCharm"));
        ga.addView(infoRow("编译工程", "Android"));
        ga.addView(infoRow("开发者系统", "Windows11 专业工作站版"));
        ga.addView(infoRow("作者", "苏丠"));
        ga.addView(infoRow("开发者", "G_SuQiu"));
        ga.addView(para("如果存在问题或者功能缺陷欢迎联系："));
        TextView email = makeText(13, t.mainDark);
        email.setText("2376990248@qq.com");
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.setMargins(0, 0, 0, dp(6));
        email.setLayoutParams(elp);
        ga.addView(email);
        pageAbout.addView(ga);

        pageAbout.addView(groupHeader("法律声明"));
        LinearLayout gl = card();
        gl.addView(para("本软件仅供个人学习与生活使用的免费产品，未授权任何平台或个人公开售卖。"
                + "如您通过付费渠道获得本软件，请立即退款并举报。"));
        gl.addView(para("软件内所有功能数据均保存在本机，不会收集、上传您的任何个人信息，"
                + "如果对本产品存在疑惑可拒绝使用！"));
        gl.addView(para("严禁对本软件进行反向工程、破解、二次分发或去除作者信息。"
                + "对侵犯作者权益的行为，作者将会保留证据并追究法律责任的权利。"));
        pageAbout.addView(gl);

        TextView ver = makeText(12, t.textSecondary);
        ver.setText("版本 " + BuildConfig.VERSION_NAME);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(12), 0, dp(4));
        pageAbout.addView(ver);
    }

    /** 段落文本 */
    private TextView para(String s) {
        TextView tv = makeText(13, t.textPrimary);
        tv.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 信息行：灰标签 + 加粗值 */
    private View infoRow(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(3), 0, dp(3));
        TextView a = makeText(13, t.textSecondary);
        a.setText(k + "：");
        r.addView(a);
        TextView b = makeText(13, t.textPrimary);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setText(v);
        b.setGravity(Gravity.START);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        r.addView(b, blp);
        return r;
    }

    // ============================ 渲染调度 ============================

    private void renderAll() {
        titleMain.setText("🏠 寝室值日 · " + store.roomName());
        hintView.setText(store.memberCount() + " 人 · 每天 " + store.perDay() + " 人值日");
        renderToday();
        renderPlan();
        renderMembers();
        renderSettings();
        renderAbout();
    }

    // ============================ 组件 & 工具 ============================

    private View sectionHeader(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(4), 0, dp(8));
        TextView h = makeText(15, t.textPrimary);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        h.setText(text);
        wrap.addView(h);
        View line = new View(this);
        line.setBackgroundColor(t.main);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        wrap.addView(line);
        return wrap;
    }

    private View groupHeader(String text) {
        TextView h = makeText(12, t.textSecondary);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        h.setText(text);
        h.setPadding(0, dp(14), 0, dp(6));
        h.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return h;
    }

    /** 白卡片容器 */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.cardBg);
        bg.setCornerRadius(dp(12) * 1f);
        bg.setStroke(dp(1), t.cardStroke);
        c.setBackground(bg);
        int p = dp(14);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    /** 设置行：左圆图标 + 中标题副标题 + 右侧操作词 */
    private View settingRow(String icon, int iconBg, int iconFg,
                            String title, String subtitle, String right,
                            View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(12);
        row.setPadding(p, p, p, p);
        if (click != null) row.setOnClickListener(click);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(13);
        ic.setTypeface(ic.getTypeface(), Typeface.BOLD);
        ic.setTextColor(iconFg);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        cd.setColor(iconBg);
        ic.setBackground(cd);
        int cs = dp(32);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cs, cs);
        clp.setMargins(0, 0, dp(12), 0);
        row.addView(ic, clp);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView tt = makeText(14, t.textPrimary);
        tt.setText(title);
        mid.addView(tt);
        TextView s = makeText(11, t.textSecondary);
        s.setText(subtitle);
        mid.addView(s);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(mid);

        if (right != null) {
            TextView r = makeText(13, t.textSecondary);
            r.setText(right);
            row.addView(r);
        }
        return row;
    }

    /** 卡片内分组分隔线（左缩进对齐正文） */
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(t.cardStroke);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(56), 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** 横向小行：左灰标签 + 右加粗值 */
    private View hRow(String left, String right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView a = makeText(13, t.textSecondary);
        a.setText(left);
        r.addView(a);
        TextView b = makeText(13, t.textPrimary);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setText(right);
        b.setGravity(Gravity.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        r.addView(b, blp);
        return r;
    }

    /** 浅色底 + 彩色文字的小胶囊（统一风格的操作钮） */
    private View softChip(String text, int bg, int fg) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setTextColor(fg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable b = new GradientDrawable();
        b.setColor(bg);
        b.setCornerRadius(dp(12) * 1f);
        tv.setBackground(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 实心大按钮（页面主操作） */
    private View bigChip(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14) * 1f);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 实心小按钮（行内操作） */
    private View solidButton(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10) * 1f);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, 0, 0);
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
