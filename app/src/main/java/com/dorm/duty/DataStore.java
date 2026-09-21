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
        public Member(String n, boolean l) { name = n; leader = l; }
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
                        o.optBoolean("l", false)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public int memberCount() { return members().size(); }

    public void addMember(String name) {
        List<Member> m = members();
        m.add(new Member(name, m.isEmpty())); // 第一个人默认当寝室长
        saveMembers(m);
    }

    public void removeMember(int idx) {
        List<Member> m = members();
        if (idx < 0 || idx >= m.size()) return;
        m.remove(idx);
        if (m.size() == 1 && !m.get(0).leader) m.get(0).leader = true;
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

    private void saveMembers(List<Member> m) {
        try {
            JSONArray arr = new JSONArray();
            for (Member x : m) {
                JSONObject o = new JSONObject();
                o.put("n", x.name);
                o.put("l", x.leader);
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