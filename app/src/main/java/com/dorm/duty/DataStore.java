package com.dorm.duty;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 本地数据仓库：人员、寝室配置、排班游标、签到记录，全部存 SharedPreferences(JSON) */
public class DataStore {
    private final SharedPreferences sp;
    public static final String TAG = "DormDuty";

    public DataStore(Context c) {
        sp = c.getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }

    public static class Member {
        public String name;
        public boolean leader;
        public String bed; // 床位/备注，如 "3号床"
        public Member(String n, boolean l) { this(n, l, ""); }
        public Member(String n, boolean l, String b) { name = n; leader = l; bed = b == null ? "" : b; }
    }

    /** 成员展示名：张三（3号床）；无床位就纯名字 */
    public static String displayText(Member x) {
        return (x == null || x.name == null) ? "" :
                x.name + ((x.bed == null || x.bed.isEmpty()) ? "" : "（" + x.bed + "）");
    }

    // ---------- 寝室配置 ----------
    public boolean isPrivacyAccepted() { return sp.getBoolean("privacy_accepted", false); }
    public void setPrivacyAccepted(boolean v) { sp.edit().putBoolean("privacy_accepted", v).apply(); }

    public String roomName() { return sp.getString("room_name", "我的寝室"); }
    public void setRoomName(String v) { sp.edit().putString("room_name", v).apply(); }

    public int size() { return sp.getInt("room_size", 6); }
    public void setSize(int v) { sp.edit().putInt("room_size", v).apply(); }

    public int perDay() { return memberCount() <= 6 ? 1 : 2; } // 按实际成员数：6人以下每天1人，7人以上每天2人

    public LocalDate startDate() {
        long e = sp.getLong("start_epoch", LocalDate.now().toEpochDay());
        return LocalDate.ofEpochDay(e);
    }
    public void setStartDate(LocalDate d) { sp.edit().putLong("start_epoch", d.toEpochDay()).apply(); }

    public int startIndex() { return sp.getInt("start_index", 0); }
    public void setStartIndex(int v) { sp.edit().putInt("start_index", v).apply(); }
    public void advanceIndex(int n) { setStartIndex((startIndex() + n) % Math.max(1, memberCount())); }

    // ---------- 成员 ----------
    public List<Member> members() {
        List<Member> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("members", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Member(o.getString("n"),
                        o.optBoolean("l", false),
                        o.optString("b", "")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public int memberCount() { return members().size(); }

    public void addMember(String name, String bed) {
        List<Member> m = members();
        m.add(new Member(name, m.isEmpty(), bed)); // 第一个人默认当寝室长
        saveMembers(m);
    }

    public void removeMember(int idx) {
        List<Member> m = members();
        if (idx < 0 || idx >= m.size()) return;
        m.remove(idx);
        boolean hasLeader = false;
        for (Member x : m) if (x.leader) hasLeader = true;
        if (!m.isEmpty() && !hasLeader) m.get(0).leader = true; // 删完没人当寝室长就补上
        saveMembers(m);
    }

    public void setLeader(int idx, boolean leader) {
        List<Member> m = members();
        if (idx < 0 || idx >= m.size()) return;
        if (leader) {
            for (Member x : m) x.leader = false; // 先清掉旧寝室长（之前改的是临时列表，没保存）
        }
        m.get(idx).leader = leader;
        saveMembers(m);
    }

    /** 编辑成员资料：姓名 + 床位（都可为空则不动对应字段） */
    public void editMember(int idx, String name, String bed) {
        List<Member> m = members();
        if (idx < 0 || idx >= m.size()) return;
        Member me = m.get(idx);
        if (name != null && !name.trim().isEmpty()) me.name = name.trim();
        if (bed != null) me.bed = bed.trim();
        saveMembers(m);
    }

    /** 某天值日成员的展示名列表（带床位），如 ["张三（3号床）"] */
    public List<String> dutyDisplayOf(LocalDate date) {
        List<String> names = dutyOf(date);
        List<String> out = new ArrayList<>();
        for (String n : names) {
            boolean found = false;
            for (Member x : members()) {
                if (x.name.equals(n)) { out.add(displayText(x)); found = true; break; }
            }
            if (!found) out.add(n);
        }
        return out;
    }

    // ---------- 提醒 ----------
    public boolean isReminderEnabled() { return sp.getBoolean("reminder_enabled", false); }
    public void setReminderEnabled(boolean v) { sp.edit().putBoolean("reminder_enabled", v).apply(); }
    public int reminderHour() { return sp.getInt("reminder_hour", 7); }
    public int reminderMinute() { return sp.getInt("reminder_minute", 0); }
    public void setReminderTime(int h, int m) {
        sp.edit().putInt("reminder_hour", h).putInt("reminder_minute", m).apply();
    }

    // ---------- 日历目标 ----------
    /** 手动指定的写入日历 id；0 = 自动（优先自建 DormDuty，失败回退系统日历） */
    public int selectedCalendarId() { return sp.getInt("cal_id", 0); }
    public void setSelectedCalendarId(int v) { sp.edit().putInt("cal_id", v).apply(); }

    // ---------- 备份 / 恢复 ----------
    /** 导出全部数据为 JSON 字符串 */
    public String exportAll() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("room_name", sp.getString("room_name", "我的寝室"));
            o.put("start_epoch", sp.getLong("start_epoch", LocalDate.now().toEpochDay()));
            o.put("start_index", sp.getInt("start_index", 0));
            o.put("members", sp.getString("members", "[]"));
            o.put("privacy_accepted", sp.getBoolean("privacy_accepted", false));
            o.put("reminder_enabled", sp.getBoolean("reminder_enabled", false));
            o.put("reminder_hour", sp.getInt("reminder_hour", 7));
            o.put("reminder_minute", sp.getInt("reminder_minute", 0));
            o.put("theme_index", sp.getInt("theme_index", 0));
            o.put("cal_id", sp.getInt("cal_id", 0));
            JSONObject checks = new JSONObject();
            for (String k : sp.getAll().keySet())
                if (k.startsWith("check_")) checks.put(k, sp.getAll().get(k));
            o.put("checks", checks);
            return o.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 JSON 字符串恢复全部数据；成功返回 true */
    public boolean importAll(String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = sp.edit();
            if (o.has("room_name")) e.putString("room_name", o.getString("room_name"));
            if (o.has("start_epoch")) e.putLong("start_epoch", o.getLong("start_epoch"));
            if (o.has("start_index")) e.putInt("start_index", o.getInt("start_index"));
            if (o.has("members")) e.putString("members", o.getString("members"));
            if (o.has("privacy_accepted")) e.putBoolean("privacy_accepted", o.getBoolean("privacy_accepted"));
            if (o.has("reminder_enabled")) e.putBoolean("reminder_enabled", o.getBoolean("reminder_enabled"));
            if (o.has("reminder_hour")) e.putInt("reminder_hour", o.getInt("reminder_hour"));
            if (o.has("reminder_minute")) e.putInt("reminder_minute", o.getInt("reminder_minute"));
            if (o.has("theme_index")) e.putInt("theme_index", o.getInt("theme_index"));
            if (o.has("cal_id")) e.putInt("cal_id", o.getInt("cal_id"));
            if (o.has("checks")) {
                JSONObject c = o.getJSONObject("checks");
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    e.putString(k, c.getString(k));
                }
            }
            e.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveMembers(List<Member> m) {
        try {
            JSONArray arr = new JSONArray();
            for (Member x : m) {
                JSONObject o = new JSONObject();
                o.put("n", x.name);
                o.put("l", x.leader);
                o.put("b", x.bed == null ? "" : x.bed);
                arr.put(o);
            }
            sp.edit().putString("members", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---------- 签到 ----------
    public boolean isChecked(String dateStr, String name) {
        try {
            JSONArray arr = new JSONArray(sp.getString("check_" + dateStr, "[]"));
            for (int i = 0; i < arr.length(); i++)
                if (arr.getString(i).equals(name)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public void toggleCheck(String dateStr, String name) {
        try {
            JSONArray arr = new JSONArray(sp.getString("check_" + dateStr, "[]"));
            boolean found = false;
            for (int i = 0; i < arr.length(); i++)
                if (arr.getString(i).equals(name)) { arr.remove(i); found = true; break; }
            if (!found) arr.put(name);
            sp.edit().putString("check_" + dateStr, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** 排班核心：某一天值日的人（从 startDate 起，每天 perDay 人顺序轮转） */
    public List<String> dutyOf(LocalDate date) {
        List<Member> m = members();
        List<String> out = new ArrayList<>();
        if (m.isEmpty()) return out;
        long days = date.toEpochDay() - startDate().toEpochDay();
        if (days < 0) return out;
        int base = startIndex() + (int)(days * perDay());
        int n = m.size();
        for (int i = 0; i < perDay(); i++) {
            out.add(m.get(((base + i) % n + n) % n).name);
        }
        return out;
    }
}