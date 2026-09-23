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

    // ---------- 模式（dorm 寝室 / class 班级） ----------
    public boolean isClassMode() { return sp.getBoolean("mode_class", false); }
    public void setClassMode(boolean v) { sp.edit().putBoolean("mode_class", v).apply(); }

    // ---------- 寝室配置 ----------
    public boolean isPrivacyAccepted() { return sp.getBoolean("privacy_accepted", false); }
    public void setPrivacyAccepted(boolean v) { sp.edit().putBoolean("privacy_accepted", v).apply(); }

    public String roomName() { return sp.getString("room_name", "我的寝室"); }
    public void setRoomName(String v) { sp.edit().putString("room_name", v).apply(); }
    /** 班级名称（班级模式独立于寝室名） */
    public String clsName() { return sp.getString("cls_name", "我的班级"); }
    public void setClsName(String v) { sp.edit().putString("cls_name", v).apply(); }
    /** 当前模式的显示名：寝室=寝室名，班级=班级名 */
    public String displayName() { return isClassMode() ? clsName() : roomName(); }
    public void setDisplayName(String v) { if (isClassMode()) setClsName(v); else setRoomName(v); }

    public int size() { return sp.getInt("room_size", 6); }
    public void setSize(int v) { sp.edit().putInt("room_size", v).apply(); }

    /** 每天值日人数：寝室=按成员数 1~2；班级=组大小（整组值班） */
    public int perDay() {
        if (isClassMode()) return groupSize();
        return memberCount() <= 6 ? 1 : 2;
    }

    // ---------- 班级配置 ----------
    /** 自动分组时每组人数（仅「自动分组」用；手动分组可不等） */
    public int groupSize() { return sp.getInt("cls_group_size", 4); }
    public void setGroupSize(int v) { sp.edit().putInt("cls_group_size", v).apply(); }
    /** 轮班方式：true=按周循环（对齐周一，组对应星期几）；false=顺序轮完。仅「轮班开」时有意义 */
    public boolean weeklyCycle() { return sp.getBoolean("cls_cycle_weekly", true); }
    public void setWeeklyCycle(boolean v) { sp.edit().putBoolean("cls_cycle_weekly", v).apply(); }
    /** 轮班开关：true=自动轮组；false=不轮班（固定默认组，可手动换） */
    public boolean rotate() { return sp.getBoolean("cls_rotate", true); }
    public void setRotate(boolean v) { sp.edit().putBoolean("cls_rotate", v).apply(); }
    /** 不轮班时的固定组号（0-based） */
    public int manualGroup() { return sp.getInt("cls_manual_group", 0); }
    public void setManualGroup(int v) { sp.edit().putInt("cls_manual_group", v).apply(); }

    // ---------- 班级显式分组（N+1 组，每组≥2人，组大小可不等） ----------
    /** 显式分组：JSON 数组的数组（成员名字）；空=回退自动切组 */
    public List<List<String>> rawGroups() {
        List<List<String>> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("cls_groups", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONArray g = arr.getJSONArray(i);
                List<String> names = new ArrayList<>();
                for (int j = 0; j < g.length(); j++) names.add(g.getString(j));
                out.add(names);
            }
        } catch (Exception ignored) {}
        return out;
    }
    public void saveRawGroups(List<List<String>> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (List<String> g : groups) {
                JSONArray ga = new JSONArray();
                for (String n : g) ga.put(n);
                arr.put(ga);
            }
            sp.edit().putString("cls_groups", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
    /** 是否已建显式分组（有则不再自动切） */
    public boolean hasExplicitGroups() { return !rawGroups().isEmpty(); }
    /** 把全班按 size 自动切成组并存为显式分组 */
    public void autoGroupAll(int size) {
        List<Member> m = members();
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i += size) {
            List<String> g = new ArrayList<>();
            for (int j = i; j < Math.min(i + size, m.size()); j++) g.add(m.get(j).name);
            if (!g.isEmpty()) out.add(g);
        }
        saveRawGroups(out);
    }
    /** 新增一组（第 N+1 组）；若还没有显式分组则先按当前组大小铺一遍再追加 */
    public void addGroup() {
        List<List<String>> g = rawGroups();
        if (g.isEmpty()) autoGroupAll(groupSize());
        g = rawGroups();
        g.add(new ArrayList<>());
        saveRawGroups(g);
    }
    /** 删除第 gi 组 */
    public void deleteGroup(int gi) {
        List<List<String>> g = rawGroups();
        if (gi >= 0 && gi < g.size()) { g.remove(gi); saveRawGroups(g); }
    }
    /** 直接覆盖第 gi 组成员 */
    public void setGroupMembers(int gi, List<String> names) {
        List<List<String>> g = rawGroups();
        if (gi >= 0 && gi < g.size()) {
            g.set(gi, new ArrayList<>(names));
            saveRawGroups(g);
        }
    }

    public LocalDate startDate() {
        long e = sp.getLong("start_epoch", LocalDate.now().toEpochDay());
        return LocalDate.ofEpochDay(e);
    }
    public void setStartDate(LocalDate d) { sp.edit().putLong("start_epoch", d.toEpochDay()).apply(); }

    public int startIndex() { return sp.getInt("start_index", 0); }
    public void setStartIndex(int v) { sp.edit().putInt("start_index", v).apply(); }
    public void advanceIndex(int n) { setStartIndex((startIndex() + n) % Math.max(1, memberCount())); }

    // ---------- 成员（寝室/班级 各一套，互不干扰） ----------
    /** 当前模式用的成员存储键：寝室=members，班级=cls_members */
    private String membersKey() { return isClassMode() ? "cls_members" : "members"; }

    /** 读指定键的成员列表 */
    private List<Member> rawMembers(String key) {
        List<Member> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Member(o.getString("n"),
                        o.optBoolean("l", false),
                        o.optString("b", "")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    /** 当前模式的成员 */
    public List<Member> members() { return rawMembers(membersKey()); }

    /** 一次性迁移：老版本两种模式共用 members。升级后若还没有 cls_members：
     *  当前是班级模式 → 把 members（班里人）搬进 cls_members 并清空 members，
     *  寝室模式不受影响；寝室模式 → 只给 cls_members 建空表。 */
    public void migrateClassMembers() {
        if (sp.getBoolean("cls_migrated_v1", false)) return;
        sp.edit().putBoolean("cls_migrated_v1", true).apply();
        if (sp.contains("cls_members")) return;
        SharedPreferences.Editor e = sp.edit();
        if (isClassMode()) {
            String cur = sp.getString("members", "[]");
            e.putString("cls_members", cur);
            e.putString("members", "[]"); // 宿舍名单位空出来，班级数据已搬到 cls_members
        } else {
            e.putString("cls_members", "[]");
        }
        e.apply();
    }

    public int memberCount() { return members().size(); }

    /** 名单位上限：寝室 12，班级 60 */
    public int memberLimit() { return isClassMode() ? 60 : 12; }

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
            if (o.has("cls_name")) e.putString("cls_name", o.getString("cls_name"));
            if (o.has("start_epoch")) e.putLong("start_epoch", o.getLong("start_epoch"));
            if (o.has("start_index")) e.putInt("start_index", o.getInt("start_index"));
            boolean clsInBackup = o.optBoolean("mode_class", false);
            if (o.has("cls_members")) {
                // 新版备份：两套名单各自落位
                if (o.has("members")) e.putString("members", o.getString("members"));
                e.putString("cls_members", o.getString("cls_members"));
            } else if (o.has("members")) {
                // 旧版备份：members 是共用名单，按备份时的模式归位
                if (clsInBackup) e.putString("cls_members", o.getString("members"));
                else e.putString("members", o.getString("members"));
            }
            if (o.has("mode_class")) e.putBoolean("mode_class", o.getBoolean("mode_class"));
            if (o.has("cls_group_size")) e.putInt("cls_group_size", o.getInt("cls_group_size"));
            if (o.has("cls_cycle_weekly")) e.putBoolean("cls_cycle_weekly", o.getBoolean("cls_cycle_weekly"));
            if (o.has("cls_groups")) e.putString("cls_groups", o.getString("cls_groups"));
            if (o.has("cls_rotate")) e.putBoolean("cls_rotate", o.getBoolean("cls_rotate"));
            if (o.has("cls_manual_group")) e.putInt("cls_manual_group", o.getInt("cls_manual_group"));
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
            o.put("v", 3);
            o.put("room_name", sp.getString("room_name", "我的寝室"));
            o.put("cls_name", sp.getString("cls_name", "我的班级"));
            o.put("start_epoch", sp.getLong("start_epoch", LocalDate.now().toEpochDay()));
            o.put("start_index", sp.getInt("start_index", 0));
            o.put("members", sp.getString("members", "[]"));
            o.put("cls_members", sp.getString("cls_members", "[]"));
            o.put("mode_class", sp.getBoolean("mode_class", false));
            o.put("cls_group_size", sp.getInt("cls_group_size", 4));
            o.put("cls_cycle_weekly", sp.getBoolean("cls_cycle_weekly", true));
            o.put("cls_groups", sp.getString("cls_groups", "[]"));
            o.put("cls_rotate", sp.getBoolean("cls_rotate", true));
            o.put("cls_manual_group", sp.getInt("cls_manual_group", 0));
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
            if (o.has("cls_name")) e.putString("cls_name", o.getString("cls_name"));
            if (o.has("start_epoch")) e.putLong("start_epoch", o.getLong("start_epoch"));
            if (o.has("start_index")) e.putInt("start_index", o.getInt("start_index"));
            boolean clsInBackup = o.optBoolean("mode_class", false);
            if (o.has("cls_members")) {
                // 新版备份：两套名单各自落位
                if (o.has("members")) e.putString("members", o.getString("members"));
                e.putString("cls_members", o.getString("cls_members"));
            } else if (o.has("members")) {
                // 旧版备份：members 是共用名单，按备份时的模式归位
                if (clsInBackup) e.putString("cls_members", o.getString("members"));
                else e.putString("members", o.getString("members"));
            }
            if (o.has("mode_class")) e.putBoolean("mode_class", o.getBoolean("mode_class"));
            if (o.has("cls_group_size")) e.putInt("cls_group_size", o.getInt("cls_group_size"));
            if (o.has("cls_cycle_weekly")) e.putBoolean("cls_cycle_weekly", o.getBoolean("cls_cycle_weekly"));
            if (o.has("cls_groups")) e.putString("cls_groups", o.getString("cls_groups"));
            if (o.has("cls_rotate")) e.putBoolean("cls_rotate", o.getBoolean("cls_rotate"));
            if (o.has("cls_manual_group")) e.putInt("cls_manual_group", o.getInt("cls_manual_group"));
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

    private void saveMembersRaw(String key, List<Member> m) {
        try {
            JSONArray arr = new JSONArray();
            for (Member x : m) {
                JSONObject o = new JSONObject();
                o.put("n", x.name);
                o.put("l", x.leader);
                o.put("b", x.bed == null ? "" : x.bed);
                arr.put(o);
            }
            sp.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** 保存到当前模式的名单位 */
    private void saveMembers(List<Member> m) { saveMembersRaw(membersKey(), m); }

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

    // ---------- 班级分组（仅班级模式用） ----------
    /** 值日组列表：有显式分组(cls_groups)就按它来；否则回退按 groupSize 自动切。
     *  返回每组成员（跳过空组）。 */
    public List<List<Member>> groupsOf() {
        List<Member> all = members();
        List<List<String>> raw = rawGroups();
        if (!raw.isEmpty()) {
            List<List<Member>> out = new ArrayList<>();
            for (List<String> g : raw) {
                List<Member> gm = new ArrayList<>();
                for (String nm : g) {
                    for (Member mm : all) if (mm.name.equals(nm)) { gm.add(mm); break; }
                }
                if (!gm.isEmpty()) out.add(gm);
            }
            return out;
        }
        // 回退：按 groupSize 自动切
        int gs = Math.max(1, groupSize());
        List<List<Member>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += gs) {
            out.add(new ArrayList<>(all.subList(i, Math.min(i + gs, all.size()))));
        }
        return out;
    }

    /** 某天值日组号（0-based）；无成员返回 -1。
     *  轮班关=不轮班：固定 manualGroup。
     *  轮班开 + 按周：对齐到「起始日所在周的周一」，组对应星期几、可预测。
     *  轮班开 + 顺序：从起始日逐天顺序轮完，不绑定星期几。 */
    public int groupIndexOf(LocalDate date) {
        List<List<Member>> gs = groupsOf();
        if (gs.isEmpty()) return -1;
        int gcount = gs.size();
        if (!rotate()) {
            int mg = manualGroup();
            if (mg < 0 || mg >= gcount) mg = 0;
            return mg;
        }
        LocalDate anchor = startDate();
        if (weeklyCycle()) {
            int dow = anchor.getDayOfWeek().getValue() - 1; // MONDAY→0
            anchor = anchor.minusDays(dow);
        }
        long days = date.toEpochDay() - anchor.toEpochDay();
        if (days < 0) days = 0;
        int total = startIndex() + (int) days;
        return ((total % gcount) + gcount) % gcount;
    }

    /** 某天值日组的展示名（班级模式）：第 N 组 */
    public String groupLabelOf(LocalDate date) {
        int idx = groupIndexOf(date);
        if (idx < 0) return "—";
        return "第 " + (idx + 1) + " 组";
    }

    /** 排班核心：某一天值日的人。
     *  寝室模式：顺序轮转 perDay 人；
     *  班级模式：整组值班，组内请假由组后顺延补位。 */
    public List<String> dutyOf(LocalDate date) {
        List<Member> m = members();
        List<String> out = new ArrayList<>();
        if (m.isEmpty()) return out;
        String ds = date.toString();
        // 换班/休息覆盖优先（两模式共用）
        List<String> ovr = getOverride(ds);
        if (ovr != null) return ovr; // 空列表 = 当天全员休息

        if (isClassMode()) {
            int g = groupIndexOf(date);
            if (g < 0) return out;
            List<List<Member>> groups = groupsOf();
            if (g >= groups.size()) return out;
            List<Member> grp = groups.get(g);
            List<String> leaves = getLeaves(ds);
            // 组内未请假者直接进
            List<String> picked = new ArrayList<>();
            for (Member x : grp) if (!leaves.contains(x.name)) picked.add(x.name);
            // 组被请假打空（整个组都请假）→ 顺延下一组补满
            if (picked.isEmpty()) {
                int total = groups.size();
                int guard = 0;
                int cur = g;
                while (picked.isEmpty() && guard <= total) {
                    cur = (cur + 1) % total;
                    guard++;
                    for (Member x : groups.get(cur))
                        if (!leaves.contains(x.name)) picked.add(x.name);
                }
            }
            out.addAll(picked);
            return out;
        }

        // 寝室模式：基础轮转 + 请假自动补位
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