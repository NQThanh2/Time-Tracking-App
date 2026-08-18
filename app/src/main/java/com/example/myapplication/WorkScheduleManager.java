package com.example.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class WorkScheduleManager {
    private static final String PREFS_NAME = "PersonalWorkSchedule";
    private static final String KEY_EMPLOYEE_NAME = "employee_name";
    private static final String KEY_SHIFTS = "shifts";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter STATUS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM", new Locale("vi", "VN"));

    private WorkScheduleManager() {
    }

    static void saveSchedule(Context context, String employeeName, List<WorkShift> shifts) {
        cancelScheduledReminders(context, loadShifts(context));
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EMPLOYEE_NAME, employeeName.trim())
                .putString(KEY_SHIFTS, serialize(shifts))
                .apply();
        scheduleReminders(context, shifts);
    }

    static String getEmployeeName(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EMPLOYEE_NAME, "");
    }

    static List<WorkShift> loadShifts(Context context) {
        String serialized = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SHIFTS, "");
        List<WorkShift> shifts = new ArrayList<>();
        if (serialized == null || serialized.isEmpty()) {
            return shifts;
        }
        for (String line : serialized.split("\\n")) {
            String[] parts = line.split("\\|");
            if (parts.length != 3) {
                continue;
            }
            try {
                shifts.add(new WorkShift(
                        LocalDate.parse(parts[0], DATE_FORMAT),
                        LocalTime.parse(parts[1], TIME_FORMAT),
                        LocalTime.parse(parts[2], TIME_FORMAT)
                ));
            } catch (RuntimeException ignored) {
                // Ignore a damaged saved entry and preserve the remaining schedule.
            }
        }
        shifts.sort(Comparator.comparing(shift -> shift.date));
        return shifts;
    }

    static String getStatusText(Context context) {
        String name = getEmployeeName(context);
        List<WorkShift> shifts = loadShifts(context);
        if (name == null || name.isEmpty() || shifts.isEmpty()) {
            return context.getString(R.string.schedule_not_imported);
        }
        LocalDateTime now = LocalDateTime.now();
        for (WorkShift shift : shifts) {
            LocalDateTime start = shift.date.atTime(shift.start);
            if (start.isAfter(now)) {
                return name + " • Ca tiếp theo "
                        + STATUS_DATE_FORMAT.format(shift.date) + " "
                        + TIME_FORMAT.format(shift.start) + "–" + TIME_FORMAT.format(shift.end);
            }
        }
        return name + " • Đã lưu " + shifts.size() + " ca, không còn ca sắp tới";
    }

    static void reschedule(Context context) {
        scheduleReminders(context, loadShifts(context));
    }

    static boolean canScheduleExactReminders(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        return alarmManager != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms());
    }

    private static void scheduleReminders(Context context, List<WorkShift> shifts) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || alarmManager.canScheduleExactAlarms();
        String employeeName = getEmployeeName(context);

        for (WorkShift shift : shifts) {
            long reminderAt = shift.date
                    .atTime(shift.start)
                    .minusMinutes(10)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            if (reminderAt <= now) {
                continue;
            }
            PendingIntent reminder = createReminderPendingIntent(context, employeeName, shift);
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, reminder);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, reminder);
            }
        }
    }

    private static void cancelScheduledReminders(Context context, List<WorkShift> shifts) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        String employeeName = getEmployeeName(context);
        for (WorkShift shift : shifts) {
            alarmManager.cancel(createReminderPendingIntent(context, employeeName, shift));
        }
    }

    private static PendingIntent createReminderPendingIntent(
            Context context,
            String employeeName,
            WorkShift shift
    ) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction("com.example.myapplication.WORK_SHIFT_REMINDER")
                .putExtra(ReminderReceiver.EXTRA_EMPLOYEE_NAME, employeeName)
                .putExtra(ReminderReceiver.EXTRA_SHIFT_DATE, shift.date.toString())
                .putExtra(ReminderReceiver.EXTRA_SHIFT_START, TIME_FORMAT.format(shift.start));
        return PendingIntent.getBroadcast(
                context,
                requestCodeFor(shift),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int requestCodeFor(WorkShift shift) {
        long value = shift.date.toEpochDay() * 100L + shift.start.getHour();
        return (int) (value ^ (value >>> 32));
    }

    private static String serialize(List<WorkShift> shifts) {
        StringBuilder serialized = new StringBuilder();
        for (WorkShift shift : shifts) {
            if (serialized.length() > 0) {
                serialized.append('\n');
            }
            serialized.append(DATE_FORMAT.format(shift.date))
                    .append('|')
                    .append(TIME_FORMAT.format(shift.start))
                    .append('|')
                    .append(TIME_FORMAT.format(shift.end));
        }
        return serialized.toString();
    }
}
