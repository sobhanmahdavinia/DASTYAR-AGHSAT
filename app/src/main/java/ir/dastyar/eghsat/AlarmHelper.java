package ir.dastyar.eghsat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmHelper {
    /** Schedule the reminder for the actual due date, at 09:00 local time. */
    public static void schedule(Context c, Installment x) {
        if (x.isFinished()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long trigger = x.nextDueMillis();
        long now = System.currentTimeMillis();
        // If the due time has already passed, deliver once as soon as possible.
        if (trigger < now + 5000L) trigger = now + 5000L;

        Intent i = new Intent(c, ReminderReceiver.class).putExtra("id", x.id);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int) (x.id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
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
