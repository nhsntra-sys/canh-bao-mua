package vn.vas.canhbaomua;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = Color.rgb(17, 17, 17), CARD = Color.rgb(32, 32, 32), YEL = Color.rgb(255, 196, 0),
            RED = Color.rgb(227, 18, 11), GRN = Color.rgb(31, 157, 85), INK = Color.rgb(240, 240, 240), MUT = Color.rgb(170, 170, 170);
    Prefs p;
    LinearLayout statusBox;
    TextView stTitle, stDetail, stTime, locTxt, logTxt, fcTxt, fcInfo;
    Button startBtn;
    final Handler h = new Handler(Looper.getMainLooper());
    LocationListener gpsL;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        RainCheck.channels(this);
        getWindow().setStatusBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        root.setBackgroundColor(BG);

        TextView h1 = text("⛈ Cảnh báo mưa công trình", 20, INK);
        h1.setTypeface(null, Typeface.BOLD);
        root.addView(h1);

        statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        stTime = text("", 14, Color.WHITE);
        stTitle = text("", 28, Color.WHITE);
        stTitle.setTypeface(null, Typeface.BOLD);
        stDetail = text("", 15, Color.WHITE);
        statusBox.addView(stTime); statusBox.addView(stTitle); statusBox.addView(stDetail);
        root.addView(statusBox, lp(dp(10)));

        startBtn = button("", YEL, Color.BLACK);
        startBtn.setTextSize(19);
        startBtn.setOnClickListener(v -> toggle());
        root.addView(startBtn, lp(dp(12)));

        LinearLayout row = new LinearLayout(this);
        Button now = button("↻ Kiểm tra ngay", CARD, INK);
        now.setOnClickListener(v -> checkNow());
        Button test = button("🔊 Thử còi", CARD, INK);
        test.setOnClickListener(v -> RainCheck.alarm(this, "THỬ CÒI", "Đây là còi thử. Bấm ĐÃ BIẾT để tắt."));
        row.addView(now, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
        row.addView(test, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row, lp(dp(8)));

        // --- Vị trí ---
        LinearLayout loc = card(root, "Vị trí công trình");
        locTxt = text("", 15, INK);
        loc.addView(locTxt);
        Button gps = button("📍 Lấy vị trí hiện tại (GPS)", CARD, INK);
        gps.setOnClickListener(v -> getGps());
        loc.addView(gps, lp(dp(8)));
        Button man = button("✎ Nhập toạ độ tay", CARD, INK);
        man.setOnClickListener(v -> manualLoc());
        loc.addView(man, lp(dp(6)));
        CheckBox auto = check("Tự cập nhật khi anh di chuyển (dùng vị trí sẵn có, không bật GPS thêm)", p.autoLoc());
        auto.setOnCheckedChangeListener((cb, on) -> p.setAutoLoc(on));
        loc.addView(auto, lp(dp(6)));

        // --- Cài đặt ---
        LinearLayout set = card(root, "Cài đặt");
        set.addView(text("Báo trước", 14, MUT));
        Spinner lead = spinner(new String[]{"30 phút", "45 phút", "60 phút"}, p.lead() == 30 ? 0 : p.lead() == 60 ? 2 : 1,
                i -> p.setLead(new int[]{30, 45, 60}[i]));
        set.addView(lead, lp(dp(2)));
        set.addView(text("Độ nhạy", 14, MUT), lp(dp(8)));
        Spinner sens = spinner(new String[]{"Thấp – chỉ mưa rõ", "Vừa (khuyên dùng)", "Cao – cả mưa nhỏ"}, p.sens(), p::setSens);
        set.addView(sens, lp(dp(2)));
        set.addView(text("Giờ chạy (ngoài giờ app nghỉ hẳn để tiết kiệm pin)", 14, MUT), lp(dp(8)));
        String[] hrs = {"Cả ngày đêm", "5:00 – 19:00", "6:00 – 18:00", "4:00 – 21:00"};
        int[][] hv = {{0, 24}, {5, 19}, {6, 18}, {4, 21}};
        int cur = 0;
        if (p.workOnly()) for (int i = 1; i < hv.length; i++) if (hv[i][0] == p.workStart() && hv[i][1] == p.workEnd()) cur = i;
        Spinner work = spinner(hrs, cur, i -> { p.setWorkOnly(i != 0); p.setWork(hv[i][0], hv[i][1]); });
        set.addView(work, lp(dp(2)));

        // --- Giúp app chạy nền ổn định ---
        LinearLayout bg = card(root, "Để app chạy nền không bị tắt (làm 1 lần)");
        Button bat = button("1. Cho phép chạy nền (tắt tối ưu pin)", CARD, INK);
        bat.setOnClickListener(v -> askBattery());
        bg.addView(bat, lp(dp(4)));
        Button bgl = button("2. Cho phép vị trí “Luôn luôn” (tuỳ chọn)", CARD, INK);
        bgl.setOnClickListener(v -> askBgLocation());
        bg.addView(bgl, lp(dp(6)));
        Button app = button("3. Mở cài đặt app (Pin → Không hạn chế)", CARD, INK);
        app.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        bg.addView(app, lp(dp(6)));

        Button radar = button("🗺 Xem bản đồ radar", CARD, YEL);
        radar.setOnClickListener(v -> {
            String u = p.hasLoc() ? String.format(Locale.US, "https://www.rainviewer.com/map.html?loc=%.4f,%.4f,9", p.lat(), p.lon()) : "https://www.rainviewer.com/map.html";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        });
        root.addView(radar, lp(dp(12)));

        LinearLayout fc = card(root, "Dự báo theo giờ (tổng hợp nhiều trung tâm khí tượng)");
        fcInfo = text("", 13, MUT);
        fc.addView(fcInfo);
        fcTxt = text("", 13, INK);
        fcTxt.setTypeface(Typeface.MONOSPACE);
        fc.addView(fcTxt, lp(dp(6)));
        Button fcBtn = button("↻ Tải lại dự báo", CARD, INK);
        fcBtn.setOnClickListener(v -> loadForecast(0));
        fc.addView(fcBtn, lp(dp(8)));

        LinearLayout lg = card(root, "Nhật ký kiểm tra");
        logTxt = text("", 13, MUT);
        lg.addView(logTxt);

        root.addView(text("Radar: RainViewer (nguồn Đài Khí tượng Thuỷ văn). Dự báo: ECMWF, NOAA, DWD, UKMO, ECCC qua Open-Meteo. App chỉ hỗ trợ, vẫn nên để ý trời.", 12, MUT), lp(dp(12)));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);
        askBasics();
    }

    @Override protected void onResume() { super.onResume(); refresh(); loadForecast(60); }

    void loadForecast(int maxAge) {
        if (!p.hasLoc()) return;
        fcInfo.setText("Đang tải dự báo…");
        final double la = p.lat(), lo = p.lon();
        new Thread(() -> {
            try { Forecast.refreshIfOld(getApplicationContext(), la, lo, maxAge); } catch (Throwable ignored) { }
            h.post(this::showForecast);
        }).start();
    }

    void showForecast() {
        long t = Forecast.time(this);
        fcInfo.setText(t == 0 ? "Chưa tải được dự báo." : String.format(Locale.US,
                "%d kịch bản từ: %s. Cập nhật %tH:%<tM. Cột %% = tỉ lệ kịch bản có mưa; “x/y mô hình” = số trung tâm đa số báo mưa.",
                Forecast.members(this), Forecast.models(this), t));
        fcTxt.setText(Forecast.render(this));
    }

    void refresh() {
        String lvl = p.lvl();
        int col = lvl.equals("r") ? RED : lvl.equals("y") ? YEL : lvl.equals("g") ? GRN : Color.rgb(60, 60, 60);
        int ink = lvl.equals("y") ? Color.BLACK : Color.WHITE;
        GradientDrawable g = new GradientDrawable();
        g.setColor(col); g.setCornerRadius(dp(14));
        statusBox.setBackground(g);
        stTitle.setTextColor(ink); stDetail.setTextColor(ink); stTime.setTextColor(ink);
        stTitle.setText(p.running() || p.statusTime() > 0 ? p.title() : "Chưa bắt đầu");
        stDetail.setText(p.running() || p.statusTime() > 0 ? p.detail() : "Lấy vị trí rồi bấm Bắt đầu giám sát.");
        String t = p.statusTime() > 0 ? String.format(Locale.US, "Cập nhật %tH:%<tM", p.statusTime()) : "";
        if (p.running() && p.next() > 0) t += String.format(Locale.US, " · lần tới %tH:%<tM", p.next());
        stTime.setText(p.running() ? "ĐANG GIÁM SÁT · " + t : "ĐÃ DỪNG " + t);
        startBtn.setText(p.running() ? "■ Dừng giám sát" : "▶ Bắt đầu giám sát");
        locTxt.setText(p.hasLoc() ? String.format(Locale.US, "%.5f, %.5f\n(cập nhật %td/%<tm %<tH:%<tM)", p.lat(), p.lon(), p.locTime()) : "Chưa có vị trí");
        logTxt.setText(p.logText().isEmpty() ? "Chưa có" : p.logText());
    }

    void toggle() {
        if (p.running()) {
            p.setRunning(false);
            Scheduler.cancel(this);
            getSystemService(android.app.NotificationManager.class).cancel(RainCheck.ID_STATUS);
            p.log("Dừng giám sát");
        } else {
            if (!p.hasLoc()) { Toast.makeText(this, "Bấm “Lấy vị trí hiện tại” trước", Toast.LENGTH_LONG).show(); return; }
            p.setRunning(true);
            p.setAcked(false);
            p.log("Bắt đầu giám sát");
            if (!ignoringBattery()) askBattery();
            checkNow();
        }
        refresh();
    }

    void checkNow() {
        if (!p.hasLoc()) { Toast.makeText(this, "Chưa có vị trí", Toast.LENGTH_SHORT).show(); return; }
        stTitle.setText("Đang kiểm tra…");
        new Thread(() -> {
            try { RainCheck.run(getApplicationContext(), true); } catch (Throwable t) { p.log("Lỗi: " + t.getClass().getSimpleName()); }
            h.post(this::refresh);
        }).start();
    }

    // ---------- Quyền ----------
    void askBasics() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    boolean ignoringBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    void askBattery() {
        if (ignoringBattery()) { Toast.makeText(this, "Đã cho phép chạy nền ✓", Toast.LENGTH_SHORT).show(); return; }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    void askBgLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { askBasics(); return; }
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Vị trí khi chạy nền")
                    .setMessage("Ở màn hình tiếp theo, chọn “Luôn cho phép”. App chỉ đọc vị trí sẵn có của máy, không tự bật GPS nên không tốn thêm pin.")
                    .setPositiveButton("Tiếp tục", (d, w) -> requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2))
                    .setNegativeButton("Thôi", null).show();
        } else Toast.makeText(this, "Đã cho phép ✓", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 3 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) getGps();
    }

    // ---------- Vị trí ----------
    void getGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 3);
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        if (gpsL != null) lm.removeUpdates(gpsL);
        locTxt.setText("Đang lấy GPS… (ra chỗ thoáng nếu lâu)");
        gpsL = new LocationListener() {
            public void onLocationChanged(Location l) {
                if (l.hasAccuracy() && l.getAccuracy() > 200 && LocationManager.NETWORK_PROVIDER.equals(l.getProvider())) {
                    if (!p.hasLoc()) { p.setLoc(l.getLatitude(), l.getLongitude()); refresh(); }
                    return; // chờ GPS chính xác hơn
                }
                lm.removeUpdates(this);
                gpsL = null;
                p.setLoc(l.getLatitude(), l.getLongitude());
                p.log(String.format(Locale.US, "Vị trí mới %.5f, %.5f (±%.0f m)", l.getLatitude(), l.getLongitude(), l.getAccuracy()));
                Toast.makeText(MainActivity.this, "Đã lưu vị trí ✓", Toast.LENGTH_SHORT).show();
                refresh();
            }
            public void onProviderEnabled(String s) { }
            public void onProviderDisabled(String s) { }
            public void onStatusChanged(String s, int i, Bundle b) { }
        };
        try {
            boolean any = false;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsL); any = true; }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, gpsL); any = true; }
            if (!any) { Toast.makeText(this, "Hãy bật Vị trí (GPS) trên máy", Toast.LENGTH_LONG).show(); startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
        } catch (SecurityException e) { return; }
        final LocationListener mine = gpsL;
        h.postDelayed(() -> { if (gpsL == mine && mine != null) { lm.removeUpdates(mine); gpsL = null; refresh(); } }, 90_000);
    }

    void manualLoc() {
        EditText e = new EditText(this);
        e.setHint("10.82310, 106.62970");
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        if (p.hasLoc()) e.setText(String.format(Locale.US, "%.5f, %.5f", p.lat(), p.lon()));
        new AlertDialog.Builder(this).setTitle("Toạ độ công trình")
                .setMessage("Trên Google Maps, nhấn giữ vào công trình → bấm vào toạ độ để copy → dán vào đây.")
                .setView(e)
                .setPositiveButton("Lưu", (d, w) -> {
                    try {
                        String[] s = e.getText().toString().replace(" ", "").split(",");
                        double la = Double.parseDouble(s[0]), lo = Double.parseDouble(s[1]);
                        if (Math.abs(la) > 90 || Math.abs(lo) > 180) throw new IllegalArgumentException();
                        p.setLoc(la, lo);
                        p.setAutoLoc(false);
                        p.log("Nhập toạ độ tay");
                        Toast.makeText(this, "Đã lưu. Đã tắt tự cập nhật vị trí.", Toast.LENGTH_LONG).show();
                        recreate();
                    } catch (Exception ex) { Toast.makeText(this, "Toạ độ không đúng dạng “vĩ độ, kinh độ”", Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton("Huỷ", null).show();
    }

    // ---------- Giao diện ----------
    interface IntCb { void on(int i); }

    Spinner spinner(String[] items, int sel, IntCb cb) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(a);
        s.setSelection(Math.max(0, Math.min(items.length - 1, sel)));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> av, View v, int i, long id) { if (first) { first = false; return; } cb.on(i); }
            public void onNothingSelected(AdapterView<?> av) { }
        });
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(230, 230, 230)); g.setCornerRadius(dp(8));
        s.setBackground(g);
        s.setMinimumHeight(dp(46));
        return s;
    }

    LinearLayout card(LinearLayout root, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(26, 26, 26)); g.setCornerRadius(dp(12)); g.setStroke(dp(1), Color.rgb(51, 51, 51));
        c.setBackground(g);
        TextView t = text(title, 15, YEL);
        t.setTypeface(null, Typeface.BOLD);
        c.addView(t);
        root.addView(c, lp(dp(12)));
        return c;
    }

    TextView text(String s, int sp, int col) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(col);
        return t;
    }

    Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(fg); b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg); g.setCornerRadius(dp(10)); g.setStroke(dp(1), Color.rgb(70, 70, 70));
        b.setBackground(g);
        b.setMinHeight(dp(50));
        return b;
    }

    CheckBox check(String s, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(s); c.setTextColor(INK); c.setChecked(on);
        return c;
    }

    LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = top;
        return l;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
