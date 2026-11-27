package vn.vas.canhbaomua;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_CHECK = "vn.vas.canhbaomua.CHECK";
    public static final String ACTION_ACK = "vn.vas.canhbaomua.ACK";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        if (ACTION_ACK.equals(intent.getAction())) {
            new Prefs(c).setAcked(true);
            c.getSystemService(NotificationManager.class).cancel(RainCheck.ID_ALARM);
            new Prefs(c).log("Đã xác nhận cảnh báo");
            return;
        }
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { RainCheck.run(c, false); }
            catch (Throwable t) { new Prefs(c).log("Lỗi: " + t.getClass().getSimpleName()); }
            finally { pr.finish(); }
        }).start();
    }
}
