package com.dorm.duty;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 底部专属菜单（4 Tab）：今日值日 / 排班 / 成员 / 设置。
 * 设计语言：白卡片 + 分组列表 + 统一圆底字符图标，主色 #2E7D32。
 */
public class MainActivity extends Activity {

    private DataStore store;
    private CalendarSync calSync;

    private LinearLayout root;
    private android.widget.FrameLayout host;
    private android.widget.ImageView bgImage;
    private FrameLayout pageHost;
    private ScrollView svToday, svPlan, svMembers, svSettings, svAbout;
    private LinearLayout pageToday, pagePlan, pageMembers, pageSettings, pageAbout;
    private TextView titleMain, hintView;
    private final TextView[] tabIcons = new TextView[5];
    private final TextView[] tabLabels = new TextView[5];
    private final View[] tabDots = new View[5];
    private int currentTab;
    private int expandedIdx = -1; // 成员页当前展开 ⚙ 面板的成员序号
    private final boolean[] groupOpen = new boolean[10]; // 设置页 10 个组默认全展开

    // 翻页时钟
    private TextView clkH, clkM, clkS;
    private final android.os.Handler clockHandler = new android.os.Handler();
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            tickClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

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
        // 提醒对齐：若已开启，重新武装明天定点（应用重启后状态可能漂移）
        ReminderManager.scheduleDaily(this, store);
        // 翻页时钟：每秒 tick
        clockHandler.postDelayed(clockTick, 300);
        // 首次启动：隐私协议确认（接受才进入，拒绝立即退出）
        if (!store.isPrivacyAccepted()) showPrivacyDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacksAndMessages(null);
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

    /** 切换主题后重刷全局配色（背景、状态栏、底部菜单、顶栏标题） */
    private void refreshTheme() {
        applyBackground();
        getWindow().setStatusBarColor(t.main);
        if (tabWrap != null) {
            tabWrap.setBackgroundColor(t.cardBg);
            tabInner.setBackgroundColor(t.cardBg);
            tabLine.setBackgroundColor(t.cardStroke);
        }
        if (titleMain != null) {
            // 顶栏标题与底部菜单是常驻视图，不会随 renderAll 重建，颜色要手动刷
            titleMain.setTextColor(t.mainDark);
            hintView.setTextColor(t.textSecondary);
            selectTab(currentTab);
        }
    }

    /** 页面背景：0=主题色 1=纯色 2=图片 */
    private void applyBackground() {
        if (bgImage == null) return;
        int m = store.bgMode();
        if (m == 1) {
            bgImage.setVisibility(View.GONE);
            root.setBackgroundColor(store.bgColor());
            return;
        }
        if (m == 2) {
            android.graphics.Bitmap b = loadBgBitmap();
            if (b != null) {
                bgImage.setImageBitmap(b);
                bgImage.setVisibility(View.VISIBLE);
                root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                return;
            }
        }
        bgImage.setVisibility(View.GONE);
        root.setBackgroundColor(t.windowBg);
    }

    private android.graphics.Bitmap loadBgBitmap() {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "bg.jpg");
            if (!f.exists()) return null;
            return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    /** 相册选图 → 压缩 1080 宽 → 存 bg.jpg 并设为页面背景 */
    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        if (req == 201 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                android.graphics.Bitmap src = android.graphics.BitmapFactory
                        .decodeStream(getContentResolver().openInputStream(data.getData()));
                if (src == null) {
                    ToastSafe.show(this, "图片读取失败");
                    return;
                }
                int w = 1080;
                int h = Math.max(1, Math.round(1080f * src.getHeight() / src.getWidth()));
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(src, w, h, true);
                java.io.File f = new java.io.File(getExternalFilesDir(null), "bg.jpg");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                }
                if (src != scaled) src.recycle();
                store.setBgMode(2);
                applyBackground();
                ToastSafe.show(this, "背景图片已设置");
            } catch (Exception e) {
                ToastSafe.show(this, "图片处理失败，请换一张试试");
            }
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
        // host：底层背景 ImageView + 上层内容 root（图片背景时让 root 透明）
        host = new android.widget.FrameLayout(this);
        bgImage = new android.widget.ImageView(this);
        bgImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        bgImage.setVisibility(View.GONE);
        host.addView(bgImage, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        host.addView(root, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(host);
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
        updateTitleText();
        titleMain.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(titleMain);
        hintView = makeText(12, t.textSecondary);
        updateHintText();
        bar.addView(hintView);
        return bar;
    }

    /** 顶栏标题：随模式切换 */
    private void updateTitleText() {
        titleMain.setText((store.isClassMode() ? "🏫 班级值日 · " : "🏠 寝室值日 · ")
                + store.roomName());
    }

    /** 顶栏副标题：人数与值班说明 */
    private void updateHintText() {
        int cnt = store.memberCount();
        if (store.isClassMode()) {
            int groups = store.groupsOf().size();
            String rot = store.rotate() ? "轮班中" : "不轮班";
            hintView.setText(cnt + " 人 · " + groups + " 组 · 每天 1 组 · " + rot);
        } else {
            hintView.setText(cnt + " 人 · 每天 " + store.perDay() + " 人值日");
        }
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

            // 选中指示条
            int dw = dp(22);
            tabDots[i] = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(t.main);
            gd.setCornerRadius(dp(2) * 1f);
            tabDots[i].setBackground(gd);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dw, dp(3));
            dlp.setMargins(0, dp(3), 0, 0);
            tabDots[i].setLayoutParams(dlp);
            tabDots[i].setVisibility(View.GONE);
            tab.addView(tabDots[i]);

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
            if (tabDots[k] != null) tabDots[k].setVisibility(on ? View.VISIBLE : View.GONE);
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
        if (store.isClassMode()) {
            label.setText("今日值日组 · " + store.groupLabelOf(today));
        } else {
            label.setText("今日值日");
        }
        card.addView(label);
        TextView big = makeText(24, t.mainDark);
        big.setTypeface(big.getTypeface(), Typeface.BOLD);
        if (store.isClassMode()) {
            big.setText(names.isEmpty() ? "还没有人" : store.groupLabelOf(today));
            card.addView(big);
            if (!names.isEmpty()) {
                TextView grpNames = makeText(14, t.textPrimary);
                grpNames.setText(String.join("  ", store.dutyDisplayOf(today)));
                grpNames.setPadding(0, dp(4), 0, 0);
                card.addView(grpNames);
            }
        } else {
            big.setText(names.isEmpty() ? "还没有人" : String.join("  ", store.dutyDisplayOf(today)));
            card.addView(big);
        }
        if (names.isEmpty()) {
            TextView tip = makeText(12, t.textSecondary);
            tip.setText("到「成员」页添加成员后自动生成排班");
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
            next.addView(hRow(dd[i - 1], dutyLine(d)));
        }
        pageToday.addView(next);

        // 翻页时钟（番茄钟风格实时时间，数字变化时 3D 翻转）
        LinearLayout ck = card();
        TextView ckLabel = makeText(12, t.textSecondary);
        ckLabel.setText("当前时间 · 番茄时钟");
        ck.addView(ckLabel);
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.CENTER);
        clockRow.setPadding(0, dp(8), 0, 0);
        clkH = digitView();
        clkM = digitView();
        clkS = digitView();
        clockRow.addView(clkH);
        clockRow.addView(clockColon());
        clockRow.addView(clkM);
        clockRow.addView(clockColon());
        clockRow.addView(clkS);
        ck.addView(clockRow);
        pageToday.addView(ck);
        tickClock();

        TextView rule = makeText(12, t.textSecondary);
        if (store.isClassMode()) {
            int gcnt = store.groupsOf().size();
            if (!store.rotate()) {
                rule.setText("轮换规则：不轮班 · 固定第 " + (Math.min(store.manualGroup(), Math.max(0, gcnt - 1)) + 1)
                        + " 组每天值日（可在设置改默认组，或手动换班）。");
            } else {
                String cyc = store.weeklyCycle() ? "按周循环（对齐周一 · 组对应星期几）" : "顺序轮完（从起始日逐天轮）";
                rule.setText("轮换规则：全班分 " + gcnt + " 组，每天 1 组值日，" + cyc + "。");
            }
        } else {
            rule.setText("轮换规则：从起始日期开始，按成员顺序每天 " + store.perDay()
                    + " 人值日，轮完一圈循环。");
        }
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

            // 日期列（含"换班/请假"小标记）
            LinearLayout dateBox = new LinearLayout(this);
            dateBox.setOrientation(LinearLayout.VERTICAL);
            dateBox.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            TextView date = makeText(14, i == 0 ? t.mainDark : t.textSecondary);
            date.setTypeface(date.getTypeface(), i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            date.setText((i == 0 ? "今天 · " : "") + d.format(D_FMT));
            dateBox.addView(date);
            boolean hasOvr = store.getOverride(d.toString()) != null;
            java.util.List<String> lv = store.getLeaves(d.toString());
            if (hasOvr || !lv.isEmpty()) {
                TextView mark = makeText(11, hasOvr ? 0xFFE65100 : t.textSecondary);
                StringBuilder mb = new StringBuilder();
                if (hasOvr) mb.append("换班");
                if (!lv.isEmpty()) {
                    if (mb.length() > 0) mb.append(" · ");
                    mb.append(String.join("、", lv) + " 请假");
                }
                mark.setText(mb.toString());
                dateBox.addView(mark);
            }
            row.addView(dateBox);

            TextView duty = makeText(14, t.textPrimary);
            duty.setTypeface(duty.getTypeface(), Typeface.BOLD);
            duty.setGravity(Gravity.END);
            if (names.isEmpty()) {
                duty.setText("—");
            } else if (store.isClassMode()) {
                duty.setText(store.groupLabelOf(d) + " · " + String.join("、", store.dutyDisplayOf(d)));
            } else {
                duty.setText(String.join("、", store.dutyDisplayOf(d)));
            }
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
        // 换班 / 请假 入口
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setGravity(Gravity.CENTER_VERTICAL);
        View swap = solidButton("🔁 换班", t.main);
        swap.setOnClickListener(v -> swapFlow());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        slp.setMargins(0, 0, dp(8), 0);
        swap.setLayoutParams(slp);
        acts.addView(swap);
        View leave = solidButton("📴 请假 / 休息", 0xFF00838F);
        leave.setOnClickListener(v -> leaveFlow());
        LinearLayout.LayoutParams llp2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        leave.setLayoutParams(llp2);
        acts.addView(leave);
        pagePlan.addView(acts);

        // 过去 7 天值班历史
        LinearLayout hist = card();
        TextView hh = makeText(13, t.textSecondary);
        hh.setText("过去 7 天值班");
        hist.addView(hh);
        for (int i = 6; i >= 1; i--) {
            LocalDate hd = today.minusDays(i);
            List<String> hs = store.dutyOf(hd);
            boolean hDone = !hs.isEmpty()
                    && hs.stream().allMatch(n -> store.isChecked(hd.toString(), n));
            LinearLayout hr = new LinearLayout(this);
            hr.setOrientation(LinearLayout.HORIZONTAL);
            hr.setGravity(Gravity.CENTER_VERTICAL);
            hr.setPadding(0, dp(5), 0, dp(5));
            TextView hdate = makeText(12, t.textSecondary);
            hdate.setText(hd.getMonthValue() + "月" + hd.getDayOfMonth() + "日");
            hdate.setLayoutParams(new LinearLayout.LayoutParams(dp(54),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            hr.addView(hdate);
            TextView hname = makeText(13, t.textPrimary);
            hname.setTypeface(hname.getTypeface(), Typeface.BOLD);
            hname.setText(dutyLine(hd));
            hname.setGravity(Gravity.END);
            hname.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            hr.addView(hname);
            TextView hstat = makeText(12,
                    hDone ? t.main : (hs.isEmpty() ? t.textSecondary : 0xFFE65100));
            hstat.setText(hDone ? "✓ 已签" : (hs.isEmpty() ? "—" : "未签"));
            hr.addView(hstat);
            hist.addView(hr);
        }
        pagePlan.addView(hist);

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
        int cap = store.memberLimit();
        String capText = store.isClassMode()
                ? ("共 " + ms.size() + " 人 · 最多 " + cap + " · 全班分 " + store.groupsOf().size() + " 组")
                : ("共 " + ms.size() + " 人 · 最多 " + cap + " · 每天 " + store.perDay() + " 人值日"
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

            // 姓名 + 床位 + 状态
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView nm = makeText(15, t.textPrimary);
            nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
            nm.setText(m.name);
            info.addView(nm);
            if (m.bed != null && !m.bed.isEmpty()) {
                TextView bd = makeText(11, t.textSecondary);
                bd.setText(m.bed);
                info.addView(bd);
            }
            TextView st = makeText(11, m.leader ? t.main : t.textSecondary);
            st.setText(m.leader ? "寝室长" : "成员");
            info.addView(st);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(info);

            // ⚙ 设置按钮：改名/设寝室长/删除 收进展开面板，行保持干净
            TextView gear = new TextView(this);
            gear.setText("⚙");
            gear.setTextSize(16);
            gear.setGravity(Gravity.CENTER);
            boolean open = expandedIdx == idx;
            gear.setTextColor(open ? t.main : t.tabOffFg);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(open ? t.accent : t.tabOffBg);
            gd.setStroke(dp(1), open ? t.main : t.cardStroke);
            gear.setBackground(gd);
            int gs = dp(34);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(gs, gs);
            glp.setMargins(dp(8), 0, 0, 0);
            gear.setLayoutParams(glp);
            gear.setOnClickListener(v -> {
                expandedIdx = open ? -1 : idx;
                renderAll();
            });
            row.addView(gear);
            pageMembers.addView(row);

            // 展开的操作面板
            if (open) {
                LinearLayout ex = card();
                ex.addView(settingRow("改", t.tabOffBg, t.tabOffFg,
                        "编辑资料", "姓名 · " + bedLabel(), "编辑",
                        v -> showEditDialog(idx, m)));
                ex.addView(divider());
                if (!m.leader) {
                    ex.addView(settingRow("长", 0xFFE3F2FD, 0xFF1976D2,
                            "设为寝室长", "一个寝室只有一位寝室长", "设置",
                            v -> {
                                store.setLeader(idx, true);
                                expandedIdx = -1;
                                renderAll();
                                ToastSafe.show(this, m.name + " 已是寝室长");
                            }));
                    ex.addView(divider());
                }
                ex.addView(settingRow("假", 0xFFE0F2F1, 0xFF00695C,
                        "请假 / 休息", "选日子标记，值日自动由下位顶上；再点取消", "标记",
                        v -> leaveFlowFor(m)));
                ex.addView(divider());
                ex.addView(settingRow("统", t.tabOffBg, t.tabOffFg,
                        "值日统计", "近 14 天",
                        "值日 " + store.dutyCountRecent(m.name, 14) + " · 请假 " + store.leaveCountRecent(m.name, 14),
                        null));
                ex.addView(divider());
                ex.addView(settingRow("删", 0xFFFFEBEE, 0xFFC62828,
                        "删除成员", "将移出值日名单", "删除",
                        v -> new AlertDialog.Builder(this)
                                .setTitle("删除成员")
                                .setMessage("确认将 " + m.name + " 移出值日名单？排班会重新计算，且无法撤销。")
                                .setPositiveButton("删除", (d, w) -> {
                                    store.removeMember(idx);
                                    expandedIdx = -1;
                                    renderAll();
                                    ToastSafe.show(this, m.name + " 已移出名单");
                                })
                                .setNegativeButton("取消", null)
                                .show()));
                pageMembers.addView(ex);
            }
        }

        // 添加成员：姓名 + 床位 输入框 + 按钮
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
        final EditText etB = new EditText(this);
        etB.setHint(bedLabel() + "（可空）");
        etB.setTextSize(13);
        etB.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        etB.setBackground(null);
        etB.setTextColor(t.textPrimary);
        etB.setHintTextColor(t.textSecondary);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(84),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        etB.setLayoutParams(blp);
        addCard.addView(etB);
        View addBtn = solidButton("＋ 添加", t.main);
        addBtn.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            String bed = etB.getText().toString().trim();
            if (name.isEmpty()) return;
            if (store.memberCount() >= store.memberLimit()) {
                ToastSafe.show(this, "最多 " + store.memberLimit() + " 人");
                return;
            }
            if (store.members().stream().anyMatch(x -> x.name.equals(name))) {
                ToastSafe.show(this, "该成员已在名单中");
                return;
            }
            store.addMember(name, bed);
            et.setText("");
            etB.setText("");
            renderAll();
            ToastSafe.show(this, name + " 已加入");
        });
        addCard.addView(addBtn);
        pageMembers.addView(addCard);

        if (store.isClassMode()) {
            pageMembers.addView(groupCard());
        }
    }

    /** 先把自动切组固化成显式分组（手动操作前调用） */
    private void materializeGroups() {
        if (!store.hasExplicitGroups()) store.autoGroupAll(store.groupSize());
    }

    /** 班级分组管理卡：自动分组 + N+1 组（每组≥2人、大小可不等） */
    private View groupCard() {
        materializeGroups(); // 固化成显式分组，后续编辑/删除索引对得上
        LinearLayout c = card();
        TextView h = makeText(13, t.textSecondary);
        h.setText("班级分组 · 每天 1 组值日（每组至少 2 人）");
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        c.addView(h);

        // 自动分组（组大小 + 生成）
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(0, dp(8), 0, 0);
        TextView aL = makeText(12, t.textPrimary);
        aL.setText("自动分组 · 每组");
        aL.setPadding(0, 0, dp(6), 0);
        autoRow.addView(aL);
        int[] sizes = {2, 3, 4, 5, 6};
        for (int s : sizes) {
            final int sz = s;
            TextView ch = makeText(12, s == store.groupSize() ? Color.WHITE : t.textPrimary);
            ch.setTypeface(ch.getTypeface(), s == store.groupSize() ? Typeface.BOLD : Typeface.NORMAL);
            ch.setText(sz + "");
            ch.setGravity(Gravity.CENTER);
            ch.setPadding(dp(8), dp(4), dp(8), dp(4));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(s == store.groupSize() ? t.main : t.tabOffBg);
            cbg.setCornerRadius(dp(7) * 1f);
            cbg.setStroke(dp(1), t.cardStroke);
            ch.setBackground(cbg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, dp(5), 0);
            ch.setLayoutParams(clp);
            ch.setOnClickListener(v -> store.setGroupSize(sz));
            autoRow.addView(ch);
        }
        View gen = solidButton("生成", t.main);
        gen.setOnClickListener(v -> { store.autoGroupAll(store.groupSize()); renderAll(); ToastSafe.show(this, "已按每组 " + store.groupSize() + " 人生成分组"); });
        autoRow.addView(gen);
        c.addView(autoRow);

        c.addView(divider());

        // 组列表（按 raw 索引，直接可编辑/删除）
        List<List<String>> raw = store.rawGroups();
        for (int gi = 0; gi < raw.size(); gi++) {
            final int gidx = gi;
            List<String> names = raw.get(gi);
            LinearLayout gr = new LinearLayout(this);
            gr.setOrientation(LinearLayout.HORIZONTAL);
            gr.setGravity(Gravity.CENTER_VERTICAL);
            gr.setPadding(0, dp(6), 0, 0);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView gt = makeText(13, t.textPrimary);
            gt.setTypeface(gt.getTypeface(), Typeface.BOLD);
            gt.setText("第 " + (gi + 1) + " 组 · " + names.size() + " 人");
            if (names.size() < 2) gt.setTextColor(0xFFC62828);
            info.addView(gt);
            TextView gn = makeText(11, names.size() < 2 ? 0xFFC62828 : t.textSecondary);
            gn.setText(names.isEmpty() ? "（空 · 点编辑加人）" : String.join("、", names));
            info.addView(gn);
            gr.addView(info);

            TextView edit = makeText(12, t.main);
            edit.setText("编辑");
            edit.setPadding(dp(8), 0, dp(8), 0);
            edit.setOnClickListener(v -> showGroupEdit(gidx));
            gr.addView(edit);
            TextView del = makeText(12, 0xFFC62828);
            del.setText("删除");
            del.setPadding(dp(8), 0, 0, 0);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除第 " + (gi + 1) + " 组")
                    .setMessage("该组 " + names.size() + " 人将被移出分组（仍留在名单）。确定删除？")
                    .setPositiveButton("删除", (d, w) -> { store.deleteGroup(gidx); renderAll(); })
                    .setNegativeButton("取消", null).show());
            gr.addView(del);
            c.addView(gr);
        }

        // 新增组
        View addG = solidButton("＋ 新增一组（第 " + (raw.size() + 1) + " 组）", 0xFF00838F);
        addG.setOnClickListener(v -> { store.addGroup(); renderAll(); ToastSafe.show(this, "已新增第 " + store.rawGroups().size() + " 组，点「编辑」加人"); });
        LinearLayout.LayoutParams aglp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        aglp.setMargins(0, dp(6), 0, 0);
        addG.setLayoutParams(aglp);
        c.addView(addG);
        return c;
    }

    /** 组编辑：多选成员弹窗 */
    private void showGroupEdit(int gidx) {
        List<List<String>> raw = store.rawGroups();
        if (gidx < 0 || gidx >= raw.size()) {
            ToastSafe.show(this, "请先「生成」分组再编辑");
            return;
        }
        java.util.Set<String> in = new java.util.HashSet<>(raw.get(gidx));
        List<DataStore.Member> all = store.members();
        String[] labels = new String[all.size()];
        boolean[] sel = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = DataStore.displayText(all.get(i));
            sel[i] = in.contains(all.get(i).name);
        }
        new AlertDialog.Builder(this)
                .setTitle("第 " + (gidx + 1) + " 组 · 选成员")
                .setMultiChoiceItems(labels, sel, (d, which, checked) -> sel[which] = checked)
                .setPositiveButton("保存", (d, w) -> {
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < all.size(); i++) if (sel[i]) names.add(all.get(i).name);
                    store.setGroupMembers(gidx, names);
                    renderAll();
                    if (names.size() < 2) ToastSafe.show(this, "第 " + (gidx + 1) + " 组不足 2 人，请补人");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 编辑成员：姓名 + 床位 */
    private void showEditDialog(final int idx, final DataStore.Member m) {
        final EditText etN = new EditText(this);
        etN.setText(m.name);
        etN.setHint("姓名");
        etN.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        etN.setTextColor(t.textPrimary);
        etN.setHintTextColor(t.textSecondary);
        final EditText etB = new EditText(this);
        etB.setText(m.bed == null ? "" : m.bed);
        etB.setHint(bedLabel() + "（可空）");
        etB.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        etB.setTextColor(t.textPrimary);
        etB.setHintTextColor(t.textSecondary);
        LinearLayout dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.VERTICAL);
        int p2 = dp(16);
        dl.setPadding(p2, dp(8), p2, 0);
        dl.addView(etN);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp2.setMargins(0, dp(10), 0, 0);
        etB.setLayoutParams(blp2);
        dl.addView(etB);
        new AlertDialog.Builder(this)
                .setTitle("编辑 " + m.name)
                .setView(dl)
                .setPositiveButton("保存", (d, w) -> {
                    String nn = etN.getText().toString().trim();
                    String bb = etB.getText().toString().trim();
                    if (nn.isEmpty()) { ToastSafe.show(this, "姓名不能为空"); return; }
                    if (!nn.equals(m.name) && store.members().stream()
                            .filter(x -> !x.name.equals(m.name))
                            .anyMatch(x -> x.name.equals(nn))) {
                        ToastSafe.show(this, "该姓名已在名单中");
                        return;
                    }
                    store.editMember(idx, nn, bb);
                    expandedIdx = -1;
                    renderAll();
                    ToastSafe.show(this, "已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 换班流程：选日子 → 选当天值日的人 → 选换成谁 */
    private void swapFlow() {
        if (store.members().isEmpty()) {
            ToastSafe.show(this, "请先添加成员");
            return;
        }
        DatePickDialog.show(this, LocalDate.now(), t, d0 -> {
            List<String> cur = store.dutyOf(d0);
            if (cur.isEmpty()) {
                ToastSafe.show(this, "这天没有值日，无需换班");
                return;
            }
            String[] opts = cur.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle(d0.format(D_FMT) + " · 选要换下的人")
                    .setMessage("当天值日：" + String.join("、", cur))
                    .setSingleChoiceItems(opts, 0, (dd, wi) -> {
                        final String outP = opts[wi];
                        List<DataStore.Member> ms = store.members();
                        String[] mp = new String[ms.size()];
                        for (int i = 0; i < ms.size(); i++) mp[i] = DataStore.displayText(ms.get(i));
                        new AlertDialog.Builder(this)
                                .setTitle("换成谁？")
                                .setItems(mp, (dd2, wi2) -> {
                                    String inP = ms.get(wi2).name;
                                    if (inP.equals(outP)) {
                                        ToastSafe.show(this, "请选不同的人");
                                        return;
                                    }
                                    List<String> L = new ArrayList<>(cur);
                                    if (L.contains(inP)) {
                                        int i1 = L.indexOf(outP), i2 = L.indexOf(inP);
                                        String tmp = L.get(i1);
                                        L.set(i1, L.get(i2));
                                        L.set(i2, tmp);
                                    } else {
                                        L.set(L.indexOf(outP), inP);
                                    }
                                    store.setOverride(d0.toString(), L);
                                    renderAll();
                                    ToastSafe.show(this, "已换班：" + outP + " ↔ " + inP);
                                })
                                .show();
                    })
                    .show();
        });
    }

    /** 请假/休息流程（排班页入口：先选成员再选日子） */
    private void leaveFlow() {
        List<DataStore.Member> ms = store.members();
        if (ms.isEmpty()) {
            ToastSafe.show(this, "请先添加成员");
            return;
        }
        String[] mp = new String[ms.size()];
        for (int i = 0; i < ms.size(); i++) mp[i] = DataStore.displayText(ms.get(i));
        new AlertDialog.Builder(this)
                .setTitle("请假 / 休息 · 选成员")
                .setItems(mp, (dd, wi) -> leaveFlowFor(ms.get(wi)))
                .show();
    }

    /** 请假/休息：给指定成员选日子，标记或取消（幂等） */
    private void leaveFlowFor(DataStore.Member m) {
        DatePickDialog.show(this, LocalDate.now(), t, d0 -> {
            boolean wasOn = store.getLeaves(d0.toString()).contains(m.name);
            store.setLeave(d0.toString(), m.name, !wasOn);
            renderAll();
            if (wasOn) ToastSafe.show(this, "已取消 " + m.name + " " + d0.format(D_FMT) + " 的请假");
            else ToastSafe.show(this, m.name + " " + d0.format(D_FMT) + " 请假，值日由下位顶上");
            expandedIdx = -1;
        });
    }

    // ============================ 页4：设置 ============================

    private void renderSettings() {
        pageSettings.removeAllViews();
        pageSettings.addView(sectionHeader("设置"));

        // —— 模式（寝室 / 班级） ——
        pageSettings.addView(groupHeader(0, "值日模式"));
        LinearLayout gMode = card();
        boolean isCls = store.isClassMode();
        gMode.addView(settingRow("模", isCls ? 0xFFE8F5E9 : 0xFFE3F2FD, isCls ? t.mainDark : 0xFF1976D2,
                "值日模式",
                isCls ? "班级 · 全班分组，每天 1 组值日" : "寝室 · 成员逐个轮值",
                isCls ? "班级" : "寝室",
                v -> {
                    boolean next = !store.isClassMode();
                    store.setClassMode(next);
                    renderAll();
                    if (next && store.memberCount() < 4)
                        ToastSafe.show(this, "已切到班级模式 · 请先到成员页录入全班名单");
                    else
                        ToastSafe.show(this, next ? "已切到班级模式" : "已切到寝室模式");
                }));
        if (groupOpen[0]) pageSettings.addView(gMode);

        // —— 基本设置 ——
        pageSettings.addView(groupHeader(1, "基本设置"));
        LinearLayout g1 = card();
        boolean clsMode = store.isClassMode();
        g1.addView(settingRow(clsMode ? "班" : "寝", t.tabOffBg, t.textPrimary,
                clsMode ? "班级名称" : "寝室名称", "用于顶栏与日历事件显示", store.roomName(),
                v -> {
                    final EditText et = new EditText(this);
                    et.setText(store.roomName());
                    et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
                    new AlertDialog.Builder(this)
                            .setTitle(clsMode ? "班级名称" : "寝室名称")
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
        if (groupOpen[1]) pageSettings.addView(g1);

        // —— 外观（多主题） ——
        pageSettings.addView(groupHeader(2, "外观 · 主题"));
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
        g6.addView(divider());
        g6.addView(settingRow("背", t.tabOffBg, t.tabOffFg,
                "自定义背景", currentBgDesc(), "设置",
                v -> showBgDialog()));
        if (groupOpen[2]) pageSettings.addView(g6);

        // —— 值日提醒 ——
        pageSettings.addView(groupHeader(3, "值日提醒"));
        LinearLayout gRem = card();
        gRem.addView(settingRow("铃", t.tabOffBg, t.textPrimary,
                "值日提醒", store.isReminderEnabled() ? ("已开 · 每天 " + fmtTime(store)) : "已关闭",
                store.isReminderEnabled() ? "开" : "关",
                v -> {
                    boolean en = !store.isReminderEnabled();
                    if (en) {
                        // Android 13+ 需通知权限，没给就先申请，给了再开
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 33
                                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
                                ToastSafe.show(this, "请先允许通知权限，再开提醒");
                                renderAll();
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    store.setReminderEnabled(en);
                    ReminderManager.scheduleDaily(this, store);
                    renderAll();
                    ToastSafe.show(this, en ? ("提醒已开 · 每天 " + fmtTime(store) + " 通知") : "提醒已关闭");
                }));
        gRem.addView(divider());
        gRem.addView(settingRow("时", t.tabOffBg, t.textPrimary,
                "提醒时间", "每天几点发出通知", fmtTime(store),
                v -> new TimePickerDialog(this, (tv, h, m) -> {
                    store.setReminderTime(h, m);
                    if (store.isReminderEnabled()) ReminderManager.scheduleDaily(this, store);
                    renderAll();
                }, store.reminderHour(), store.reminderMinute(), true).show()));
        if (groupOpen[3]) pageSettings.addView(gRem);

        // —— 轮换 ——
        pageSettings.addView(groupHeader(4, "轮换"));
        LinearLayout g2 = card();
        g2.addView(settingRow("重", 0xFFFFF3E0, 0xFFE65100,
                "重置轮换", "从名单第 1 位重新开始排班", "重置",
                v -> {
                    store.setStartIndex(0);
                    renderAll();
                    ToastSafe.show(this, "轮换已重置");
                }));
        g2.addView(divider());
        g2.addView(settingRow("换", t.tabOffBg, t.tabOffFg,
                "清除换班记录", "把换班覆盖的日期恢复成自动轮换", "清除",
                v -> {
                    store.clearOverrides();
                    renderAll();
                    ToastSafe.show(this, "换班记录已清除");
                }));
        g2.addView(divider());
        g2.addView(settingRow("假", t.tabOffBg, t.tabOffFg,
                "清除请假记录", "取消所有成员的请假/休息标记", "清除",
                v -> {
                    store.clearLeaves();
                    renderAll();
                    ToastSafe.show(this, "请假记录已清除");
                }));
        if (store.isClassMode()) {
            g2.addView(divider());
            // 轮班 开关（开=自动轮流；关=不轮班，固定默认组）
            boolean rot = store.rotate();
            g2.addView(settingRow("轮", rot ? 0xFFE8F5E9 : 0xFFFFF3E0, rot ? t.mainDark : 0xFFE65100,
                    "轮班", rot ? "开 · 各组自动轮流值日" : "关 · 不轮班（固定一组）",
                    rot ? "开" : "关",
                    v -> {
                        store.setRotate(!rot);
                        renderAll();
                        ToastSafe.show(this, !rot ? "已关轮班：不轮班，固定默认组" : "已开轮班：各组轮流");
                    }));
            if (rot) {
                g2.addView(divider());
                // 轮班方式（按周 / 顺序）
                boolean cyc = store.weeklyCycle();
                g2.addView(settingRow("周", cyc ? 0xFFE8F5E9 : t.tabOffBg, cyc ? t.mainDark : t.tabOffFg,
                        "轮班方式", cyc ? "按周循环 · 对齐周一（组对应星期几）" : "顺序轮完 · 从起始日逐天轮",
                        cyc ? "按周" : "顺序",
                        v -> { store.setWeeklyCycle(!cyc); renderAll(); }));
                g2.addView(divider());
                // 自动分组 · 每组人数
                int[] sizes = {2, 3, 4, 5, 6};
                LinearLayout gsz = new LinearLayout(this);
                gsz.setOrientation(LinearLayout.HORIZONTAL);
                gsz.setGravity(Gravity.CENTER_VERTICAL);
                gsz.setPadding(0, dp(2), 0, 0);
                TextView gszL = makeText(13, t.textPrimary);
                gszL.setText("自动分组 · 每组");
                gszL.setPadding(0, 0, dp(8), 0);
                gsz.addView(gszL);
                for (int s : sizes) {
                    final int sz = s;
                    TextView b = makeText(12, s == store.groupSize() ? Color.WHITE : t.textPrimary);
                    b.setTypeface(b.getTypeface(), s == store.groupSize() ? Typeface.BOLD : Typeface.NORMAL);
                    b.setText(sz + "");
                    b.setGravity(Gravity.CENTER);
                    b.setPadding(dp(10), dp(5), dp(10), dp(5));
                    GradientDrawable bbg = new GradientDrawable();
                    bbg.setColor(s == store.groupSize() ? t.main : t.tabOffBg);
                    bbg.setCornerRadius(dp(8) * 1f);
                    bbg.setStroke(dp(1), t.cardStroke);
                    b.setBackground(bbg);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    blp.setMargins(0, 0, dp(6), 0);
                    b.setLayoutParams(blp);
                    b.setOnClickListener(v2 -> { store.setGroupSize(sz); renderAll(); });
                    gsz.addView(b);
                }
                g2.addView(gsz);
            } else {
                g2.addView(divider());
                // 默认值日组（不轮班时固定）
                int mg = store.manualGroup();
                int gcnt = store.groupsOf().size();
                String mLabel = gcnt == 0 ? "（无组）" : "第 " + (Math.min(mg, gcnt - 1) + 1) + " 组";
                g2.addView(settingRow("默", 0xFFE3F2FD, 0xFF1976D2,
                        "默认值日组", "不轮班时每天固定该组值日", mLabel,
                        v -> {
                            if (gcnt == 0) { ToastSafe.show(this, "请先在成员页生成分组"); return; }
                            List<String> opts = new ArrayList<>();
                            for (int i = 0; i < gcnt; i++) opts.add("第 " + (i + 1) + " 组");
                            new AlertDialog.Builder(this)
                                    .setTitle("选择默认值日组")
                                    .setItems(opts.toArray(new String[0]),
                                            (d, w) -> { store.setManualGroup(w); renderAll(); })
                                    .show();
                        }));
            }
        }
        if (groupOpen[4]) pageSettings.addView(g2);

        // —— 日历同步 ——
        pageSettings.addView(groupHeader(5, "日历同步"));
        LinearLayout g3 = card();
        g3.addView(settingRow("历", t.tabOffBg, t.textPrimary,
                "写入系统日历", "未来 7 天 · 每天 7:00 提醒", "写入",
                v -> {
                    if (!calPermitted()) return;
                    int n = calSync.syncDuty(7);
                    if (n < 0) {
                        ToastSafe.show(this, "找不到可写的日历：请用下方「写入目标日历」手动指定，或检查系统日历是否可用");
                    } else {
                        ToastSafe.show(this, "已写入 " + n + " 条值日事件");
                    }
                }));
        g3.addView(divider());
        g3.addView(settingRow("盾", 0xFFE3F2FD, 0xFF1976D2,
                "校验并修复", "单向回写：用 App 排班数据修复系统日历；你在日历里的改动不会被 App 读取", "校验",
                v -> {
                    if (!calPermitted()) return;
                    int[] r = calSync.verifyAndRepair(7);
                    if (r == null) {
                        ToastSafe.show(this, "找不到可写的日历：请用「写入目标日历」手动指定");
                    } else {
                        ToastSafe.show(this, String.format("校验完成：补齐 %d · 重写 %d · 删除 %d",
                                r[0], r[1], r[2]));
                    }
                }));
        g3.addView(divider());
        g3.addView(settingRow("选", t.tabOffBg, t.textPrimary,
                "写入目标日历", calTargetDesc(), "选择",
                v -> {
                    java.util.List<CalendarSync.CalInfo> cals = calSync.listWritable();
                    if (cals.isEmpty() && store.selectedCalendarId() <= 0) {
                        ToastSafe.show(this, "设备上没有可写日历：请确认已授予日历权限且系统装有日历应用");
                        return;
                    }
                    int sel = store.selectedCalendarId();
                    String[] items = new String[cals.size() + 1];
                    items[0] = autoCalLabel();
                    int checked = 0;
                    for (int i = 0; i < cals.size(); i++) {
                        items[i + 1] = cals.get(i).name;
                        if (cals.get(i).id == sel) checked = i + 1;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("选择写入目标日历")
                            .setSingleChoiceItems(items, checked, (d, w) -> {
                                store.setSelectedCalendarId(w == 0 ? 0 : (int) cals.get(w - 1).id);
                                renderAll();
                                ToastSafe.show(this, "目标已更新");
                            })
                            .show();
                }));
        if (groupOpen[5]) pageSettings.addView(g3);

        // —— 数据备份（SQ 密钥，加密不外泄） ——
        pageSettings.addView(groupHeader(6, "数据备份 · SQ 密钥"));
        LinearLayout gData = card();
        gData.addView(settingRow("备", t.tabOffBg, t.textPrimary,
                "备份数据（导出密钥）", "复制 SQ- 密钥到剪贴板，内容已加密，不含个人信息", "复制",
                v -> {
                    String key = store.encodeBackup();
                    if (key == null) { ToastSafe.show(this, "备份失败"); return; }
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dorm-duty-key", key));
                    ToastSafe.show(this, "密钥已复制（" + key.length() + " 字符），换机后粘贴到「恢复数据」即可");
                }));
        gData.addView(divider());
        gData.addView(settingRow("恢", t.tabOffBg, t.textPrimary,
                "恢复数据（识别密钥）", "粘贴 SQ- 密钥自动识别恢复，兼容旧版明文备份", "恢复",
                v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = (cm != null) ? cm.getPrimaryClip() : null;
                    if (clip == null || clip.getItemCount() == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("恢复数据")
                                .setMessage("剪贴板是空的。请先把旧手机复制的 SQ- 密钥粘贴到剪贴板，再点此恢复。")
                                .setPositiveButton("知道了", null).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("恢复数据")
                            .setMessage("将用剪贴板中的密钥/备份覆盖当前成员与排班，确定继续？")
                            .setPositiveButton("恢复", (d, w) -> {
                                String text = clip.getItemAt(0).getText().toString();
                                if (store.decodeBackup(text)) {
                                    t = ThemeManager.get(this);
                                    refreshTheme();
                                    renderAll();
                                    ReminderManager.scheduleDaily(this, store);
                                    ToastSafe.show(this, "密钥已识别，数据恢复完成");
                                } else {
                                    ToastSafe.show(this, "识别失败：不是有效的 SQ- 密钥或备份");
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }));
        if (groupOpen[6]) pageSettings.addView(gData);

        // —— 联网同步（服务器权威源） ——
        pageSettings.addView(groupHeader(7, "联网同步 · 服务器权威"));
        LinearLayout gNet = card();
        gNet.addView(settingRow("网", t.tabOffBg, t.textPrimary,
                "服务器地址", serverUrlDesc(), "设置",
                v -> {
                    final EditText etU = new EditText(this);
                    etU.setText(store.serverUrl());
                    etU.setHint("https://…/schedule.json");
                    etU.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
                    etU.setTextColor(t.textPrimary);
                    etU.setHintTextColor(t.textSecondary);
                    new AlertDialog.Builder(this)
                            .setTitle("服务器地址")
                            .setMessage("放排班 JSON（格式同「备份数据」）的服务端 URL。留空=只用本地。")
                            .setView(etU)
                            .setPositiveButton("保存", (d, w) -> {
                                store.setServerUrl(etU.getText().toString().trim());
                                renderAll();
                                ToastSafe.show(this, "已保存服务器地址");
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }));
        gNet.addView(divider());
        gNet.addView(settingRow("同", t.tabOffBg, t.textPrimary,
                "立即同步", "拉服务器数据覆盖本地排班，并回写系统日历", "同步",
                v -> onlineSyncNow()));
        gNet.addView(divider());
        TextView noteNet = makeText(11, t.textSecondary);
        noteNet.setText("权威优先级：服务器 > 本机 App > 系统日历。你手动改系统日历不会影响 App；App 只把排班单向写入日历。");
        gNet.addView(noteNet);
        if (groupOpen[7]) pageSettings.addView(gNet);

        // —— 隐私 ——
        pageSettings.addView(groupHeader(8, "隐私"));
        LinearLayout g5 = card();
        g5.addView(settingRow("保", t.tabOffBg, t.tabOffFg,
                "用户隐私协议", "再次查看隐私保护条款", "查看",
                v -> dialogPrivacy()));
        if (groupOpen[8]) pageSettings.addView(g5);

        // —— 诊断 ——
        pageSettings.addView(groupHeader(9, "诊断"));
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
        if (groupOpen[9]) pageSettings.addView(g4);

        TextView note = makeText(12, t.textSecondary);
        note.setText("排班数据以 App 内存储为准；系统日历仅作展示与提醒。");
        note.setPadding(0, dp(10), 0, 0);
        pageSettings.addView(note);
    }

    // ============================ 页5：关于 ============================

    private void renderAbout() {
        pageAbout.removeAllViews();
        pageAbout.addView(sectionHeader("关于"));

        pageAbout.addView(plainGroupHeader("作者声明"));
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

        pageAbout.addView(plainGroupHeader("法律声明"));
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
        updateTitleText();
        updateHintText();
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
        wrap.setPadding(0, dp(4), 0, dp(10));
        TextView h = makeText(16, t.textPrimary);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        h.setLetterSpacing(0.03f);
        h.setText(text);
        wrap.addView(h);
        View line = new View(this);
        line.setBackgroundColor(t.main);
        wrap.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));
        return wrap;
    }

    /** 可折叠组标题：点一下收/放该组内容，带 ▾/▸ 指示；折叠态紧凑 */
    private View groupHeader(int idx, String text) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, groupOpen[idx] ? dp(14) : dp(8), 0, dp(4));
        h.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tv = makeText(12, t.textSecondary);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setText((groupOpen[idx] ? "▾ " : "▸ ") + text);
        h.addView(tv);
        h.setOnClickListener(v -> {
            groupOpen[idx] = !groupOpen[idx];
            renderAll();
        });
        return h;
    }

    /** 普通组标题（关于页等不可折叠处用） */
    private View plainGroupHeader(String text) {
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
        bg.setCornerRadius(dp(14) * 1f);
        bg.setStroke(dp(1), t.cardStroke);
        c.setBackground(bg);
        int p = dp(16);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
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

    /** 数字方块（时钟用）：卡片底 + 等宽大数字 */
    private TextView digitView() {
        TextView v = new TextView(this);
        v.setTextSize(30);
        v.setTypeface(Typeface.MONOSPACE);
        v.setTextColor(t.textPrimary);
        v.setGravity(Gravity.CENTER);
        v.setText("--");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.cardBg);
        bg.setCornerRadius(dp(10) * 1f);
        bg.setStroke(dp(1), t.cardStroke);
        v.setBackground(bg);
        int w = dp(64);
        int h = dp(72);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(dp(4), 0, dp(4), 0);
        v.setLayoutParams(lp);
        v.setPivotY(v.getHeight() == 0 ? dp(36) : v.getHeight() / 2f);
        return v;
    }

    private View clockColon() {
        TextView c = makeText(26, t.mainDark);
        c.setTypeface(c.getTypeface(), Typeface.BOLD);
        c.setText(":");
        c.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /** 秒针跳动：刷新三块数字，变化的翻一页（rotationX -90 → 0） */
    private void tickClock() {
        if (clkH == null) return;
        java.util.Calendar c = java.util.Calendar.getInstance();
        flipTo(clkH, String.format("%02d", c.get(java.util.Calendar.HOUR_OF_DAY)));
        flipTo(clkM, String.format("%02d", c.get(java.util.Calendar.MINUTE)));
        flipTo(clkS, String.format("%02d", c.get(java.util.Calendar.SECOND)));
    }

    private void flipTo(TextView v, String text) {
        if (v.getText().toString().equals(text)) return;
        v.setRotationX(-90f);
        v.setText(text);
        v.animate()
                .rotationX(0f)
                .setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .start();
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

    /** 格式化提醒时间 07:00 */
    private String fmtTime(DataStore s) {
        return String.format("%02d:%02d", s.reminderHour(), s.reminderMinute());
    }

    /** 日历读+写权限是否齐全；不齐全则弹出系统授权并返回 false */
    private boolean calPermitted() {
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR}, 101);
                ToastSafe.show(this, "请先授予日历权限");
                return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    /** 「写入目标日历」当前指向的描述 */
    private String calTargetDesc() {
        int sel = store.selectedCalendarId();
        if (sel <= 0) return autoCalLabel();
        for (CalendarSync.CalInfo ci : calSync.listWritable())
            if (ci.id == sel) return ci.name;
        return "已指定 #" + sel + "（可能已失效，将自动回退）";
    }

    /** 床位字段标签（随模式）：寝室=床位，班级=学号/座位 */
    private String bedLabel() {
        return store.isClassMode() ? "学号/座位" : "床位";
    }

    /** 自动目标日历的展示文案（随模式） */
    private String autoCalLabel() {
        return store.isClassMode() ? "自动（优先自建「班级值日表」）" : "自动（优先自建「寝室值日表」）";
    }

    /** 某天值日显示行：班级=「第 N 组 · 成员…」；寝室=「成员…」；无人=「—」 */
    private String dutyLine(LocalDate d) {
        List<String> ns = store.dutyOf(d);
        if (ns.isEmpty()) return "—";
        if (store.isClassMode())
            return store.groupLabelOf(d) + " · " + String.join("、", store.dutyDisplayOf(d));
        return String.join("、", store.dutyDisplayOf(d));
    }

    /** 服务器地址当前描述 */
    private String serverUrlDesc() {
        String u = store.serverUrl();
        if (u == null || u.isEmpty()) return "未设置（仅本地）";
        return u.length() > 40 ? (u.substring(0, 37) + "…") : u;
    }

    private static final int[] BG_COLORS = {
            0xFF111827, 0xFFF5F5F5, 0xFF455A64, 0xFFEFEBE9,
            0xFF3E2723, 0xFFC62828, 0xFFF48FB1, 0xFFFB8C00,
            0xFFFFF176, 0xFF4CAF50, 0xFF00897B, 0xFF1E88E5,
            0xFF7B1FA2, 0xFF303F9F, 0xFF0B1020, 0xFF263238
    };

    private String currentBgDesc() {
        switch (store.bgMode()) {
            case 1: return "纯色";
            case 2: return "图片";
            default: return "主题默认";
        }
    }

    /** 自定义背景：16 色网格 + 相册选图 + 恢复主题默认 */
    private void showBgDialog() {
        int p = dp(16);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, dp(8), p, 0);

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(4);
        body.addView(grid);
        for (int i = 0; i < BG_COLORS.length; i++) {
            final int ci = i;
            final int col = i % 4;
            TextView sw = new TextView(this);
            sw.setText("");
            sw.setBackgroundColor(BG_COLORS[i]);
            int sz = dp(40);
            android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
            glp.width = sz;
            glp.height = sz;
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            if (col == 0) glp.width = sz + dp(4);
            grid.addView(sw, glp);
            sw.setOnClickListener(v -> {
                store.setBgMode(1);
                store.setBgColor(BG_COLORS[ci]);
                refreshTheme();
                renderAll();
                dismissBodyDialog(body);
            });
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(12), 0, 0);
        TextView pickImg = makeText(14, t.main);
        pickImg.setTypeface(pickImg.getTypeface(), Typeface.BOLD);
        pickImg.setText("📷 相册选图");
        pickImg.setGravity(Gravity.CENTER);
        pickImg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        pickImg.setOnClickListener(v -> {
            android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pick, 201);
            dismissBodyDialog(body);
        });
        TextView reset = makeText(14, t.textSecondary);
        reset.setText("恢复主题默认");
        reset.setGravity(Gravity.CENTER);
        reset.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        reset.setOnClickListener(v -> {
            store.setBgMode(0);
            refreshTheme();
            renderAll();
            dismissBodyDialog(body);
        });
        btns.addView(pickImg);
        btns.addView(reset);
        body.addView(btns);

        AlertDialog dl = new AlertDialog.Builder(this)
                .setTitle("自定义背景")
                .setView(body)
                .show();
        body.setTag(dl);
    }

    private void dismissBodyDialog(View body) {
        Object tag = body.getTag();
        if (tag instanceof android.app.AlertDialog) ((android.app.AlertDialog) tag).dismiss();
    }

    /** 立即联网同步：拉服务器 JSON 覆盖本地排班（服务器权威），再单向回写系统日历 */
    private void onlineSyncNow() {
        final String url = store.serverUrl();
        if (url == null || url.isEmpty()) {
            ToastSafe.show(this, "先设置「服务器地址」再同步");
            return;
        }
        ToastSafe.show(this, "正在同步…");
        new Thread(() -> {
            final String json = httpGet(url);
            runOnUiThread(() -> {
                if (json == null) {
                    ToastSafe.show(this, "同步失败：服务器不可达或网络错误");
                    return;
                }
                if (!store.applyServerData(json)) {
                    ToastSafe.show(this, "同步失败：服务器数据格式无效");
                    return;
                }
                int n = calSync.syncDuty(7); // 单向回写系统日历（不影响本地权威数据）
                renderAll();
                ToastSafe.show(this, "已联网同步" + (n >= 0 ? "，并回写 " + n + " 条日历" : "（日历回写跳过）"));
            });
        }).start();
    }

    /** 简单 HTTP GET，失败返回 null（8s 超时） */
    private String httpGet(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) { c.disconnect(); return null; }
            java.io.InputStream is = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            c.disconnect();
            return new String(bos.toByteArray());
        } catch (Exception e) {
            return null;
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
