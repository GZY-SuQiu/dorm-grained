package com.dorm.duty;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日历对账：
 * 1) 在系统日历中建一个 "DormDuty" 本地日历；
 * 2) 把排班写入该日历事件，描述里带校验哈希；
 * 3) verifyAndRepair() 与本地排班数据比对 —— 缺失的补、被篡改的重写、多余的删，
 *    防止有人偷偷改本地日历；
 * 4) 网络日历对账留接口（以后接服务器时实现）：fetchRemotePlan()。
 */
public class CalendarSync {
    private static final String ACCOUNT = "DormDuty";
    private static final String FEED = "dd_" + DataStore.TAG; // 防串日历
    // 日历访问级别：0=只读 1=可写不可评 2=OWN 3=EDIT；>=2 即可编辑
    private static final int ACCESS_EDIT = 2;

    private final Context ctx;
    private final DataStore store;

    public CalendarSync(Context c, DataStore s) { ctx = c; store = s; }

    /** 解析目标日历：用户手选 > 我们的 DormDuty > 尝试创建 > 系统默认 > 任意可写；全失败 -1 */
    public long resolveCalendarId() {
        int sel = store.selectedCalendarId();
        if (sel > 0 && calendarExists(sel)) return sel;
        long own = findOwn();
        if (own > 0) return own;
        long created = createOwn();
        if (created > 0) return created;
        // 无"默认日历"API：自建失败就找设备里任意可编辑的日历
        return firstWritable();
    }

    private long findOwn() {
        try (Cursor cur = ctx.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID},
                CalendarContract.Calendars.ACCOUNT_NAME + "=?",
                new String[]{ACCOUNT}, null)) {
            if (cur != null && cur.moveToFirst())
                return cur.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }

    private long createOwn() {
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT);
        v.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put(CalendarContract.Calendars.NAME, ACCOUNT);
        v.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, store.isClassMode() ? "班级值日表" : "寝室值日表");
        v.put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF2E7D32);
        v.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        v.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT);
        v.put(CalendarContract.Calendars.VISIBLE, 1);
        v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        try {
            Uri uri = ctx.getContentResolver().insert(CalendarContract.Calendars.CONTENT_URI, v);
            if (uri == null) return -1;
            return Long.parseLong(uri.getLastPathSegment());
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean calendarExists(long id) {
        try (Cursor c = ctx.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID},
                CalendarContract.Calendars._ID + "=?",
                new String[]{String.valueOf(id)}, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    /** 设备里第一个可编辑（CAL_EDIT 及以上）的日历 */
    private long firstWritable() {
        try (Cursor c = ctx.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL},
                null, null, null)) {
            if (c != null) while (c.moveToNext()) {
                int lvl = c.getInt(1);
                if (lvl >= ACCESS_EDIT) return c.getLong(0);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static class CalInfo {
        public long id;
        public String name;
    }

    /** 列出设备所有可写日历（供「写入目标日历」选择器） */
    public List<CalInfo> listWritable() {
        List<CalInfo> out = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL},
                null, null, null)) {
            if (c != null) while (c.moveToNext()) {
                int lvl = c.getInt(3);
                if (lvl >= ACCESS_EDIT) {
                    CalInfo ci = new CalInfo();
                    ci.id = c.getLong(0);
                    String display = c.getString(1);
                    String acct = c.getString(2);
                    ci.name = display + ((acct == null || acct.isEmpty()) ? "" : " · " + acct);
                    out.add(ci);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 把从 today 起往后 days 天的排班写入日历（先清掉该日历本区间内旧事件） */
    public int syncDuty(int days) {
        long calId = resolveCalendarId();
        if (calId <= 0) return -1;
        clearRange(calId, days);
        ZoneId z = ZoneId.systemDefault();
        int n = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            List<String> names = store.dutyOf(d);
            if (names.isEmpty()) continue;
            long start = d.atStartOfDay(z).toInstant().toEpochMilli() + 7L * 3600_000; // 7:00
            long end = start + 15L * 3600_000; // 22:00
            String title;
            if (store.isClassMode()) {
                title = "值日：" + store.groupLabelOf(d);
            } else {
                title = "值日：" + String.join("、", store.dutyDisplayOf(d));
            }
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, title);
            v.put(CalendarContract.Events.DESCRIPTION, makeTag(d, names));
            v.put(CalendarContract.Events.DTSTART, start);
            v.put(CalendarContract.Events.DTEND, end);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, z.getId());
            v.put(CalendarContract.Events.HAS_ALARM, 1);
            try {
                ctx.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
                n++;
            } catch (Exception ignored) {}
        }
        return n;
    }

    /** 校验哈希标签：DD|<日期>|<姓名拼接>|<hash> */
    private String makeTag(LocalDate d, List<String> names) {
        String base = FEED + "|" + d + "|" + String.join(",", names);
        return base + "|" + Integer.toHexString(base.hashCode());
    }

    private boolean tagValid(String tag) {
        if (tag == null) return false;
        String[] p = tag.split("\\|");
        if (p.length != 4 || !p[0].equals(FEED)) return false;
        String base = p[0] + "|" + p[1] + "|" + p[2];
        return p[3].equals(Integer.toHexString(base.hashCode()));
    }

    public boolean isOurTag(String tag) {
        return tag != null && tag.startsWith(FEED + "|");
    }

    /** 读取 DormDuty 日历中今天+days 天内的全部事件：<dateStr, tag> */
    private Map<String, String> readEvents(long calId, int days) {
        Map<String, String> map = new HashMap<>();
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = start + days * 86400_000L;
        try (Cursor c = ctx.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events.DESCRIPTION},
                CalendarContract.Events.CALENDAR_ID + "=? AND "
                        + CalendarContract.Events.DTSTART + ">=? AND "
                        + CalendarContract.Events.DTSTART + "<?",
                new String[]{String.valueOf(calId), String.valueOf(start), String.valueOf(end)},
                null)) {
            if (c != null) while (c.moveToNext()) {
                String tag = c.getString(0);
                if (isOurTag(tag)) {
                    String[] p = tag.split("\\|");
                    if (p.length >= 3) map.put(p[1], tag);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private void clearRange(long calId, int days) {
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = start + days * 86400_000L;
        try {
            ctx.getContentResolver().delete(
                    CalendarContract.Events.CONTENT_URI,
                    CalendarContract.Events.CALENDAR_ID + "=? AND "
                            + CalendarContract.Events.DTSTART + ">=? AND "
                            + CalendarContract.Events.DTSTART + "<?",
                    new String[]{String.valueOf(calId), String.valueOf(start), String.valueOf(end)});
        } catch (Exception ignored) {}
    }

    /**
     * 核心防篡改：逐天拿"预期事件"和"日历现状"比对。
     * 返回修复动作计数数组 {补齐, 重写, 删除}；-1 表示日历不可用。
     */
    public int[] verifyAndRepair(int days) {
        long calId = resolveCalendarId();
        if (calId <= 0) return null;
        int added = 0, repaired = 0, removed = 0;
        Map<String, String> cur = readEvents(calId, days);
        ZoneId z = ZoneId.systemDefault();
        for (int i = 0; i < days; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            List<String> names = store.dutyOf(d);
            String expect = names.isEmpty() ? null : makeTag(d, names);
            String actual = cur.get(d.toString());
            if (expect == null) {
                // 今天不该有值日事件，但有 → 只删当天的（防止被人塞假事件）
                if (actual != null) {
                    long ds = d.atStartOfDay(z).toInstant().toEpochMilli();
                    long de = ds + 86400_000L;
                    try {
                        ctx.getContentResolver().delete(
                                CalendarContract.Events.CONTENT_URI,
                                CalendarContract.Events.CALENDAR_ID + "=? AND "
                                        + CalendarContract.Events.DTSTART + ">=? AND "
                                        + CalendarContract.Events.DTSTART + "<?",
                                new String[]{String.valueOf(calId),
                                        String.valueOf(ds), String.valueOf(de)});
                        removed++;
                    } catch (Exception ignored) {}
                }
                continue;
            }
            if (actual == null) {
                // 缺事件 → 单独补一条
                long start = d.atStartOfDay(z).toInstant().toEpochMilli() + 7L * 3600_000;
                long end = start + 15L * 3600_000;
                ContentValues v = new ContentValues();
                v.put(CalendarContract.Events.CALENDAR_ID, calId);
                v.put(CalendarContract.Events.TITLE, "值日：" + String.join("、", store.dutyDisplayOf(d)));
                v.put(CalendarContract.Events.DESCRIPTION, expect);
                v.put(CalendarContract.Events.DTSTART, start);
                v.put(CalendarContract.Events.DTEND, end);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, z.getId());
                try { ctx.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v); added++; }
                catch (Exception ignored) {}
            } else if (!actual.equals(expect)) {
                // 事件被改过 → 只重写这一天（旧版整区间重写 + syncDuty 会反复清空重写，计数虚高且耗时）
                long ds = d.atStartOfDay(z).toInstant().toEpochMilli();
                long de = ds + 86400_000L;
                boolean ok = true;
                try {
                    ctx.getContentResolver().delete(
                            CalendarContract.Events.CONTENT_URI,
                            CalendarContract.Events.CALENDAR_ID + "=? AND "
                                    + CalendarContract.Events.DTSTART + ">=? AND "
                                    + CalendarContract.Events.DTSTART + "<?",
                            new String[]{String.valueOf(calId),
                                    String.valueOf(ds), String.valueOf(de)});
                } catch (Exception e) { ok = false; }
                if (ok) {
                    long start = ds + 7L * 3600_000;
                    long end = start + 15L * 3600_000;
                    ContentValues v = new ContentValues();
                    v.put(CalendarContract.Events.CALENDAR_ID, calId);
                    v.put(CalendarContract.Events.TITLE, "值日：" + String.join("、", store.dutyDisplayOf(d)));
                    v.put(CalendarContract.Events.DESCRIPTION, expect);
                    v.put(CalendarContract.Events.DTSTART, start);
                    v.put(CalendarContract.Events.DTEND, end);
                    v.put(CalendarContract.Events.EVENT_TIMEZONE, z.getId());
                    try {
                        ctx.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
                        repaired++;
                    } catch (Exception ignored) {}
                }
            }
        }
        return new int[]{added, repaired, removed};
    }

    /**
     * 网络日历对账（预留接口）。
     * 以后接服务器时：拉服务器排班 JSON → 与本地排班比对 → 不一致以网络为准重写本地与系统日历。
     * 现在无服务器，返回 null 表示"未配置网络源"。
     */
    public String fetchRemotePlan() {
        return null;
    }
}