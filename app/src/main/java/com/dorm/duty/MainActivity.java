package com.dorm.duty;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 单页滚动布局：今日值日 → 7天排班表 → 成员管理 → 设置与日历同步。
 * 纯原生 View，无 AndroidX。
 */
public class MainActivity extends Activity {

    private DataStore store;
    private CalendarSync calSync;

    private LinearLayout root;
    private View dayListSection, memberSection, settingsSection;
    private TextView statusView;

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("M月d日 EEE");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new DataStore(this);
        calSync = new CalendarSync(this, store);
        buildUi();
        requestPermissions();
        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAll();
    }

    // ============================ UI 骨架 ============================

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(titleBar());
        root.addView(sectionHeader("今日值日"));
        dayListSection = new LinearLayout(this);
        dayListSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(dayListSection);

        root.addView(sectionHeader("成员管理"));
        memberSection = new LinearLayout(this);
        memberSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(memberSection);

        root.addView(sectionHeader("设置 · 日历同步"));
        settingsSection = new LinearLayout(this);
        settingsSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(settingsSection);
        statusView = makeText(13, Color.GRAY);
        settingsSection.addView(statusView);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        sv.setFillViewport(true);
        setContentView(sv);
    }

    private View titleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = makeText(22, Color.parseColor("#1B5E20"));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setText("🏠 寝室值日 · " + store.roomName());
        bar.addView(t, 0, barHeight());
        TextView hint = makeText(12, Color.GRAY);
        hint.setText(store.size() + " 人间 · 每天 " + store.perDay() + " 人");
        bar.addView(hint, 1, barHeight());
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bar.setLayoutParams(lpl);
        return bar;
    }

    private LinearLayout.LayoutParams barHeight() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private View sectionHeader(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(16), 0, dp(4));
        TextView h = makeText(16, Color.parseColor("#37474F"));
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

    // ============================ 排班表 ============================

    private void renderDays() {
        dayListSection.removeAllViews();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            List<String> names = store.dutyOf(d);
            boolean allDone = !names.isEmpty() && names.stream()
                    .allMatch(n -> store.isChecked(d.toString(), n));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(i % 2 == 0 ? Color.WHITE : Color.parseColor("#F1F8E9"));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(4));
            row.setLayoutParams(rlp);

            TextView date = makeText(14, i == 0 ? Color.parseColor("#1B5E20") : Color.DKGRAY);
            date.setText((i == 0 ? "今天 " : "") + d.format(D_FMT));
            date.setPadding(0, dp(8), dp(8), dp(8));
            row.addView(date);

            TextView duty = makeText(14, Color.parseColor("#212121"));
            duty.setText(names.isEmpty() ? "—" : String.join("、", names));
            duty.setPadding(0, dp(8), dp(8), dp(8));
            duty.setTypeface(duty.getTypeface(), Typeface.BOLD);
            row.addView(duty, 1, rowHeight());

            View check = null;
            if (i == 0 && !names.isEmpty()) {
                check = chip(allDone ? "✓ 已值完" : "去签到", allDone ? "#4CAF50" : "#E65100");
                final List<String> nn = names;
                check.setOnClickListener(v -> {
                    for (String n : nn) store.toggleCheck(d.toString(), n);
                    renderAll();
                    if (allDone && !nn.stream().allMatch(x -> store.isChecked(d.toString(), x)))
                        ToastSafe.show(this, "已签到 ✓");
                });
                row.addView(check);
            }
            dayListSection.addView(row);
        }
    }

    private LinearLayout.LayoutParams rowHeight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    // ============================ 成员管理 ============================

    private void renderMembers() {
        memberSection.removeAllViews();
        List<DataStore.Member> ms = store.members();
        for (int i = 0; i < ms.size(); i++) {
            final int idx = i;
            final DataStore.Member m = ms.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(6));
            row.setLayoutParams(rlp);

            TextView nm = makeText(15, Color.parseColor("#212121"));
            nm.setText((m.leader ? "👑 " : "") + m.name);
            nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
            row.addView(nm, 1, rowHeight());

            if (m.leader) {
                View out = chip("寝室长", "#37474F");
                row.addView(out);
            } else {
                View setL = chip("设为寝室长", "#1976D2");
                setL.setOnClickListener(v -> {
                    for (DataStore.Member x : store.members()) x.leader = false;
                    store.setLeader(idx, true);
                    renderAll();
                });
                row.addView(setL);
            }
            View del = chip("✕", "#C62828");
            del.setOnClickListener(v -> store.removeMember(idx));
            row.addView(del);

            memberSection.addView(row);
        }

        if (ms.size() < 4) {
            TextView warn = makeText(13, Color.parseColor("#C62828"));
            warn.setText("⚠ 寝室至少 4 人（4~12 人间）");
            memberSection.addView(warn);
        }

        final EditText et = new EditText(this);
        et.setHint("新成员姓名");
        et.setMaxLength(12);
        et.setBackground(new ColorDrawable(Color.parseColor("#FFFFFF")));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        et.setLayoutParams(elp);
        memberSection.addView(et);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        View add = chip("＋ 添加成员", "#2E7D32");
        add.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) return;
            if (store.memberCount() >= 12) {
                ToastSafe.show(this, "最多 12 人");
                return;
            }
            store.addMember(name);
            et.setText("");
            renderAll();
        });
        btns.addView(add);
        memberSection.addView(btns);
    }

    // ============================ 设置 & 同步 ============================

    private void renderSettings() {
        settingsSection.removeAllViews();
        settingsSection.addView(statusView);

        View setName = chip("改名：当前 [" + store.roomName() + "]", "#1976D2");
        setName.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setText(store.roomName());
            et.setMaxLength(16);
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
        settingsSection.addView(setName);

        View setDate = chip("起始日期：" + store.startDate().format(D_FMT), "#1976D2");
        setDate.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setHint("如 2026-09-21 或 今天/明天");
            new AlertDialog.Builder(this)
                    .setTitle("排班起始日期")
                    .setView(et)
                    .setPositiveButton("保存", (d, w) -> {
                        String s = et.getText().toString().trim();
                        LocalDate d = parseDate(s);
                        if (d != null) {
                            store.setStartDate(d);
                            renderAll();
                        }
                    })
                    .show();
        });
        settingsSection.addView(setDate);

        View startOver = chip("重置轮换（从第 1 位重新开始）", "#E65100");
        startOver.setOnClickListener(v -> {
            store.setStartIndex(0);
            renderAll();
            ToastSafe.show(this, "轮换已重置");
        });
        settingsSection.addView(startOver);

        statusView = makeText(13, Color.GRAY);
        settingsSection.addView(statusView);

        View sync = chip("📅 写入系统日历（7 天）", "#2E7D32");
        sync.setOnClickListener(v -> {
            int n = calSync.syncDuty(7);
            if (n < 0) {
                ToastSafe.show(this, "日历不可用：请授予日历权限后重试");
                requestPermissions();
            } else {
                ToastSafe.show(this, "已写入 " + n + " 条值日事件");
            }
        });
        settingsSection.addView(sync);

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
        settingsSection.addView(verify);

        TextView note = makeText(12, Color.GRAY);
        note.setText("说明：排班数据以 App 内存储为准；系统日历仅作展示与提醒，" +
                "被手动删改时点上方「校验修复」一键还原。" +
                "网络日历对账（服务器）即将上线。");
        settingsSection.addView(note);
    }

    // ============================ 工具 ============================

    private void renderAll() {
        renderDays();
        renderMembers();
        renderSettings();
        ((TextView) ((LinearLayout) root.getChildAt(0)).getChildAt(0)).setText(
                "🏠 寝室值日 · " + store.roomName());
        TextView hint = (TextView) ((LinearLayout) root.getChildAt(0)).getChildAt(1);
        hint.setText(store.size() + " 人间 · 每天 " + store.perDay() + " 人");
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
        bg.cornerRadius(dp(16));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
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