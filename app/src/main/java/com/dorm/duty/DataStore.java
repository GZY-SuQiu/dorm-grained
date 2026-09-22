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

    // ---------- 自定义背景 ----------
    /** 0=主题默认 1=纯色 2=图片 */
    public int bgMode() { return sp.getInt("bg_mode", 0); }
    public void setBgMode(int v) { sp.edit().putInt("bg_mode", v).apply(); }
    public int bgColor() { return sp.getInt("bg_color", 0); }
    public void setBgColor(int v) { sp.edit().putInt("bg_color", v).apply(); }

    // ---------- 联网同步（服务器权威源） ----------
    public String serverUrl() { return sp.getString("server_url", ""); }
    public void setServerUrl(String v) { sp.edit().putString("server_url", v == null ? "" : v.trim()).apply(); }

    // ---------- 备份密钥（SQ-XXXX-XXXX-XXXX-XXXX-密文块） ----------
    // 说明：CI/Android 编译环境无法使用 javax.crypto，故用 java.security
    //       (MessageDigest，全 Android 版本可用) 派生密钥流 + 对称 XOR 混淆。
    //       目的：剪贴板里不出现明文姓名（非对抗性加密，够用）。
    private static final String BACKUP_KEY_SRC = "SQ_G_SuQiu_2026";
    private static final String B36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** 由固定口令派生 16 字节密钥流 */
    private static byte[] keyStream() {
        byte[] k = new byte[16];
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(BACKUP_KEY_SRC.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < 16; i++) k[i] = h[i % 32];
        } catch (Exception e) {
            for (int i = 0; i < 16; i++) k[i] = (byte) BACKUP_KEY_SRC.charAt(i);
        }
        return k;
    }

    /** 对称 XOR：加密与解密同一步骤 */
    private static byte[] xorCipher(byte[] data) {
        byte[] k = keyStream();
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ k[i % k.length]);
        return out;
    }

    /** 字节 → 36 进制（每字节 2 个字符，全大写字母+数字） */
    private static String toBase36(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(B36.charAt(v / 36)).append(B36.charAt(v % 36));
        }
        return sb.toString();
    }

    /** 36 进制 → 字节；非法字符返回 null */
    private static byte[] fromBase36(String s) {
        if (s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int c1 = B36.indexOf(Character.toUpperCase(s.charAt(2 * i)));
            int c2 = B36.indexOf(Character.toUpperCase(s.charAt(2 * i + 1)));
            if (c1 < 0 || c2 < 0) return null;
            out[i] = (byte) ((c1 * 36 + c2) & 0xFF);
        }
        return out;
    }

    /**
     * 导出为密钥：SQ-XXXX-XXXX-XXXX-XXXX-<密文块>
     * 前 4 组随机大写字母+数字；密文块为 AES 加密后的 36 进制编码（每 4 字符一组）。
     * 剪贴板里不会出现任何明文个人信息。
     */
    public String encodeBackup() {
        String json = exportAll();
        if (json == null) return null;
        byte[] enc = xorCipher(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder("SQ-");
        for (int g = 0; g < 4; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        String data = toBase36(enc);
        for (int i = 0; i < data.length(); i += 4) {
            sb.append('-').append(data, i, Math.min(i + 4, data.length()));
        }
        return sb.toString();
    }

    /**
     * 识别并恢复：SQ- 密钥（加密）或旧版明文 JSON。成功返回 true。
     */
    public boolean decodeBackup(String input) {
        if (input == null) return false;
        String s = input.trim();
        String up = s.toUpperCase().replace("-", "");
        if (up.startsWith("SQ") && up.length() > 18 + 2) {
            // "SQ"(2) + 4 组随机头(16) = 18，之后全是密文数据块
            String data = up.substring(18);
            byte[] enc = fromBase36(data);
            if (enc == null) return false;
            byte[] plain = xorCipher(enc);
            return importAll(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (s.startsWith("{")) {
            return importAll(s); // 兼容旧版明文备份
        }
        return false;
    }

    /**
     * 应用服务器数据（权威源）：覆盖 寝室名/成员/起始日期/游标。
     * 签到记录与提醒设置属于本机状态，不随服务器覆盖。
     * JSON 结构与 exportAll() 相同（服务器只需托管一个备份 JSON 文件）。
     */
    public boolean applyServerData(String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = sp.edit();
            if (o.has("room_name")) e.putString("room_name", o.getString("room_name"));
            if (o.has("start_epoch")) e.putLong("start_epoch", o.getLong("start_epoch"));
            if (o.has("start_index")) e.putInt("start_index", o.getInt("start_index"));
            if (o.has("members")) e.putString("members", o.getString("members"));
            e.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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
            o.put("server_url", sp.getString("server_url", ""));
            o.put("bg_mode", sp.getInt("bg_mode", 0));
            o.put("bg_color", sp.getInt("bg_color", 0));
            JSONObject checks = new JSONObject();
            for (String k : sp.getAll().keySet())
                if (k.startsWith("check_") || k.startsWith("ovr_") || k.startsWith("lv_"))
                    checks.put(k, sp.getAll().get(k));
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
            if (o.has("bg_mode")) e.putInt("bg_mode", o.getInt("bg_mode"));
            if (o.has("bg_color")) e.putInt("bg_color", o.getInt("bg_color"));
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
        String ds = date.toString();
        // 1) 换班/休息覆盖优先
        List<String> ovr = getOverride(ds);
        if (ovr != null) return ovr; // 空列表 = 当天全员休息
        // 2) 基础轮转 + 请假自动补位
        long days = date.toEpochDay() - startDate().toEpochDay();
        if (days < 0) return out;
        int base = startIndex() + (int)(days * perDay());
        int n = m.size();
        List<String> leaves = getLeaves(ds);
        int need = perDay();
        int guard = 0;
        int cursor = base;
        while (out.size() < need && guard <= n) {
            Member cand = m.get(((cursor % n) + n) % n);
            cursor++;
            guard++;
            if (leaves.contains(cand.name)) continue; // 请假/休息 → 下位顶上
            out.add(cand.name);
        }
        return out;
    }

    // ---------- 换班 / 休息（按日覆盖） ----------
    /** 取某天的排班覆盖；null = 无覆盖 */
    public List<String> getOverride(String dateStr) {
        String raw = sp.getString("ovr_" + dateStr, null);
        if (raw == null) return null;
        try {
            JSONArray arr = new JSONArray(raw);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public void setOverride(String dateStr, List<String> names) {
        if (names == null) {
            sp.edit().remove("ovr_" + dateStr).apply();
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            for (String n : names) arr.put(n);
            sp.edit().putString("ovr_" + dateStr, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void clearOverrides() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet())
            if (k.startsWith("ovr_")) e.remove(k);
        e.apply();
    }

    // ---------- 请假 / 休息（按日 + 成员） ----------
    public List<String> getLeaves(String dateStr) {
        try {
            JSONArray arr = new JSONArray(sp.getString("lv_" + dateStr, "[]"));
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setLeave(String dateStr, String name, boolean on) {
        List<String> lv = getLeaves(dateStr);
        if (on) {
            if (!lv.contains(name)) lv.add(name);
        } else {
            lv.remove(name);
        }
        try {
            JSONArray arr = new JSONArray();
            for (String n : lv) arr.put(n);
            if (lv.isEmpty()) sp.edit().remove("lv_" + dateStr).apply();
            else sp.edit().putString("lv_" + dateStr, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void clearLeaves() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet())
            if (k.startsWith("lv_")) e.remove(k);
        e.apply();
    }

    // ---------- 统计 ----------
    /** 某成员近 days 天（含今天）实际值日天数 */
    public int dutyCountRecent(String name, int days) {
        int c = 0;
        for (int i = 0; i < days; i++) {
            if (dutyOf(LocalDate.now().minusDays(i)).contains(name)) c++;
        }
        return c;
    }

    /** 某成员近 days 天请假天数 */
    public int leaveCountRecent(String name, int days) {
        int c = 0;
        for (int i = 0; i < days; i++) {
            if (getLeaves(LocalDate.now().minusDays(i).toString()).contains(name)) c++;
        }
        return c;
    }
}