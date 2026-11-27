package vn.vas.canhbaomua;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

/** Hẹn giờ thức dậy kiểm tra. Giữa các lần kiểm tra app không chạy gì nên gần như không tốn pin. */
public class Scheduler {
    static PendingIntent pi(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_CHECK);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void at(Context c, long when) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c));
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c));
        }
        new Prefs(c).setNext(when);
    }

    public static void inMinutes(Context c, int min) { at(c, System.currentTimeMillis() + min * 60_000L); }

    public static void cancel(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pi(c));
        new Prefs(c).setNext(0);
    }

    public static boolean inWorkHours(Prefs p) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= p.workStart() && h < p.workEnd();
    }

    public static long nextWorkStart(Prefs p) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, p.workStart());
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }
}
