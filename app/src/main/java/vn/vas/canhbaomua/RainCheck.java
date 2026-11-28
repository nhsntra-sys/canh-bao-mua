package vn.vas.canhbaomua;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import java.util.List;
import java.util.Locale;

/** Một lần kiểm tra: lấy vị trí, đọc radar + dự báo, kết luận, báo động nếu cần, hẹn lần sau. */
public class RainCheck {
    static final String CH_ALARM = "rain_alarm", CH_STATUS = "rain_status";
    static final int ID_ALARM = 10, ID_STATUS = 11;

    public static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel a = new NotificationChannel(CH_ALARM, "Báo động mưa", NotificationManager.IMPORTANCE_HIGH);
        Uri snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (snd == null) snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        a.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        a.enableVibration(true);
        a.setVibrationPattern(new long[]{0, 900, 400, 900, 400, 900});
        a.setBypassDnd(true);
        a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel s = new NotificationChannel(CH_STATUS, "Trạng thái giám sát", NotificationManager.IMPORTANCE_LOW);
        s.setShowBadge(false);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(s);
    }

    static void updateLocation(Context c, Prefs p) {
        if (!p.autoLoc()) return;
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        for (String pr : new String[]{"fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        if (best != null && best.getTime() > p.locTime() && System.currentTimeMillis() - best.getTime() < 3600_000L)
            p.setLoc(best.getLatitude(), best.getLongitude());
    }

    public static synchronized void run(Context c, boolean manual) {
        Prefs p = new Prefs(c);
        if (!manual && !p.running()) return;
        channels(c);
        if (p.running()) Scheduler.inMinutes(c, 10); // dự phòng nếu lần này bị lỗi giữa chừng
        if (!manual && p.workOnly() && !Scheduler.inWorkHours(p)) {
            Scheduler.at(c, Scheduler.nextWorkStart(p));
            status(c, p, "", "Ngoài giờ làm – tạm nghỉ", String.format(Locale.US, "Sẽ tự chạy lại lúc %d:00.", p.workStart()));
            return;
        }
        updateLocation(c, p);
        if (!p.hasLoc()) { status(c, p, "", "Chưa có vị trí", "Mở app, bấm “Lấy vị trí hiện tại”."); return; }

        double lat = p.lat(), lon = p.lon();
        int lead = p.lead(), sens = Math.max(0, Math.min(2, p.sens()));
        int thr = new int[]{200, 150, 100}[sens];
        double fracThr = new double[]{0.6, 0.5, 0.35}[sens];

        Nowcast.Result a = null;
        String rErr = null;
        try {
            List<Nowcast.Frame> fr = Net.radarFrames(c, lat, lon, thr);
            if (fr.size() >= 2) {
                long age = Math.max(0, (System.currentTimeMillis() / 1000 - fr.get(fr.size() - 1).t) / 60);
                // ETA tính từ lúc chụp ảnh radar; trừ độ trễ của ảnh để ra số phút tính từ bây giờ
                a = Nowcast.analyze(fr, Nowcast.kmPerPx(lat), lead + (int) Math.min(age, 30), fracThr);
                a.ageMin = age;
                if (a.etaAdv != null) a.etaAdv = (int) Math.max(0, a.etaAdv - age);
                if (a.etaClose != null) a.etaClose = (int) Math.max(0, a.etaClose - age);
            } else rErr = "Thiếu ảnh radar";
        } catch (Exception e) { rErr = "Không tải được radar (mạng yếu?)"; }
        Net.Model m = null;
        try { m = Net.model(lat, lon, lead); } catch (Exception ignored) { }
        try { Forecast.refreshIfOld(c, lat, lon, 120); } catch (Exception ignored) { }
        int ens = Forecast.maxProb(c, 2); // tổ hợp nhiều mô hình, 2 giờ tới

        String lvl = a != null ? a.lvl : "g";
        if (a != null && a.ageMin > 30 && lvl.equals("g")) lvl = "y";
        boolean modelRed = m != null && m.mm >= 0.5 && m.prob >= 60;
        boolean modelY = (m != null && (m.prob >= 60 || m.mm >= 0.2 || (m.cape > 1500 && m.prob >= 40))) || ens >= 60;
        if (a == null && modelRed) lvl = "r";
        else if (lvl.equals("g") && modelY) lvl = "y";

        StringBuilder d = new StringBuilder();
        if (a != null) {
            d.append("Radar ").append(a.ageMin).append(" phút trước");
            if (a.ageMin > 30) d.append(" (CŨ)");
            d.append(". ");
            if (a.raining) d.append("Đang có mưa tại công trình. ");
            if (a.near != null) d.append(String.format(Locale.US, "Mưa gần nhất %.0f km, hướng %s. ", a.near, Nowcast.dirName(a.nearDx, a.nearDy)));
            else d.append("Không có mưa trong ~120 km. ");
            if (a.hasV) d.append(String.format(Locale.US, "Mây mưa đi %.0f km/h, từ hướng %s tới. ", a.speed, Nowcast.dirName(-a.vx, -a.vy)));
        } else d.append(rErr).append(". ");
        if (ens >= 0) d.append(String.format(Locale.US, "Tổ hợp mô hình: %d%% khả năng mưa trong 2 giờ tới. ", ens));
        if (m != null) d.append(String.format(Locale.US, "Năng lượng dông CAPE %.0f%s.", m.cape, m.cape > 1500 ? " (dễ có dông)" : ""));

        String title;
        Integer eta = a == null ? null : (a.raining ? Integer.valueOf(0) : a.etaAdv);
        if (lvl.equals("r")) title = eta == null ? "SẮP MƯA" : (eta == 0 ? "ĐANG MƯA!" : "MƯA SAU ~" + eta + " PHÚT");
        else if (lvl.equals("y")) title = (a != null && a.near != null && a.near <= 25) ? String.format(Locale.US, "Có mưa cách %.0f km – chưa hướng vào công trình", a.near) : "Chú ý – có thể mưa";
        else title = "An toàn – chưa thấy mưa tới";

        status(c, p, lvl, title, d.toString());
        p.log(title + (a == null ? " (không có radar)" : ""));

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (lvl.equals("r") && !p.acked()) alarm(c, title, "Đậy tôn, che vật tư, cho người xuống khỏi mái. " + d);
        if (lvl.equals("g")) { p.setAcked(false); nm.cancel(ID_ALARM); }

        if (p.running()) {
            int next;
            if (!lvl.equals("g") || (a != null && a.near != null && a.near <= 60)) next = 5;
            else if (a != null && a.near == null && (m == null || m.prob < 30)) next = 15;
            else next = 10;
            Scheduler.inMinutes(c, next);
        }
    }

    static void status(Context c, Prefs p, String lvl, String title, String detail) {
        p.saveStatus(lvl, title, detail);
        if (!p.running()) return;
        PendingIntent open = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String t = String.format(Locale.US, "%tH:%<tM · %s", new java.util.Date(), title);
        Notification n = new Notification.Builder(c, CH_STATUS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(t).setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build();
        try { c.getSystemService(NotificationManager.class).notify(ID_STATUS, n); } catch (Exception ignored) { }
    }

    public static void alarm(Context c, String title, String detail) {
        channels(c);
        Intent fi = new Intent(c, AlarmActivity.class).putExtra("t", title).putExtra("d", detail)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent fpi = PendingIntent.getActivity(c, 2, fi, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent ack = PendingIntent.getBroadcast(c, 3,
                new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_ACK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH_ALARM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⛈ " + title).setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fpi, true).setContentIntent(fpi)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, android.R.drawable.ic_lock_idle_alarm), "ĐÃ BIẾT – TẮT CÒI", ack).build())
                .build();
        n.flags |= Notification.FLAG_INSISTENT; // kêu lặp lại đến khi bấm
        try { c.getSystemService(NotificationManager.class).notify(ID_ALARM, n); } catch (Exception ignored) { }
    }
}
