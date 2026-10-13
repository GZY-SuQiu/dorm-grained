package com.dorm.duty;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * 值日提醒：AlarmManager 定时 + 本地通知。
 * 每天按设定时间唤醒，通知"今天谁值日"。重新武装时机：
 * 每次打开 App、修改提醒设置、写入日历后。
 */
public class ReminderManager {

    public static final String CHANNEL_ID = "dorm_duty_reminder";

    /** 创建通知渠道（API 26+ 需要，幂等） */
    public static void ensureChannel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "值日提醒", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("每天早上提醒今天谁值日");
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        } catch (Exception ignored) {}
    }

    /** 按当前设置重新武装明日提醒（关闭时取消） */
    public static void scheduleDaily(Context c, DataStore store) {
        ensureChannel(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!store.isReminderEnabled()) {
            am.cancel(pi);
            return;
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, store.reminderHour());
        next.set(Calendar.MINUTE, store.reminderMinute());
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(Calendar.getInstance())) next.add(Calendar.DAY_OF_MONTH, 1);
        // 用 set（非精确）：无需 SCHEDULE_EXACT_ALARM 权限，提醒场景够用
        am.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
    }
}
