package com.dorm.duty;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.util.List;

/** 到点触发：算出今天值日的人，发本地通知 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        DataStore store = new DataStore(c);
        LocalDate today = LocalDate.now();
        List<String> names = store.dutyOf(today);
        if (names.isEmpty()) return; // 没成员就不打扰
        String shown = String.join("、", store.dutyDisplayOf(today));

        String title = "今日值日：" + shown;
        String body = "寝室：" + store.roomName() + " · 别忘了值日哦";

        ReminderManager.ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n;
        try {
            Notification.Builder b = new Notification.Builder(c, ReminderManager.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "寝室：" + store.roomName() + "\n今日值日：" + shown))
                    .setAutoCancel(true)
                    .setContentIntent(content)
                    .setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) b.setPriority(Notification.PRIORITY_HIGH);
            n = b.build();
        } catch (Exception e) {
            return;
        }

        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(1001, n);
        } catch (Exception ignored) {}
    }
}
