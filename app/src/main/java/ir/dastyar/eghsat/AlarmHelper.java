package ir.dastyar.eghsat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmHelper {
    /** Schedule the selected reminder for the current next installment. */
    public static void schedule(Context c, Installment x) {
        if (x.isFinished() || x.reminderMode == 0) { cancel(c, x.id); return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long due = x.nextDueMillis();
        long trigger = due - (x.reminderMode == 2 ? 2L : x.reminderMode == 3 ? 7L : 0L) * 24L * 60L * 60L * 1000L;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(trigger);
        cal.set(java.util.Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, x.reminderHour)));
        cal.set(java.util.Calendar.MINUTE, Math.max(0, Math.min(59, x.reminderMinute)));
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        trigger = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        // If this occurrence's reminder moment has already passed, do not fire a stale notification.
        if (trigger <= now + 1000L) { cancel(c, x.id); return; }

        Intent i = new Intent(c, ReminderReceiver.class).putExtra("id", x.id);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int)(x.id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (android.os.Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            // Graceful fallback when the user has not granted exact-alarm access.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void rescheduleAll(Context c) {
        for (Installment x : Store.all(c)) schedule(c, x);
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int) (id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        pi.cancel();
    }
}
