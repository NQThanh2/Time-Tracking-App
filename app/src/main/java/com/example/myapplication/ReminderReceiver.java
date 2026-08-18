package com.example.myapplication;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_EMPLOYEE_NAME = "employee_name";
    static final String EXTRA_SHIFT_DATE = "shift_date";
    static final String EXTRA_SHIFT_START = "shift_start";
    private static final String CHANNEL_ID = "work_schedule_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nhắc lịch làm",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Thông báo trước ca làm 10 phút");
        manager.createNotificationChannel(channel);

        String employeeName = intent.getStringExtra(EXTRA_EMPLOYEE_NAME);
        String shiftStart = intent.getStringExtra(EXTRA_SHIFT_START);
        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String prefix = employeeName == null || employeeName.isEmpty() ? "Ca của bạn" : employeeName;
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle("Còn 10 phút đến giờ làm")
                .setContentText(prefix + " • Bắt đầu " + shiftStart + " — nhớ bấm tay")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        int notificationId = (intent.getStringExtra(EXTRA_SHIFT_DATE) + shiftStart).hashCode();
        manager.notify(notificationId, notification);
    }
}
