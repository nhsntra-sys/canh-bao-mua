package vn.vas.canhbaomua;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Màn hình báo động toàn màn hình (hiện cả khi khoá máy). Còi do thông báo phát, bấm nút để tắt. */
public class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String t = getIntent().getStringExtra("t"), d = getIntent().getStringExtra("d");

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.rgb(227, 18, 11));
        int pad = dp(24);
        l.setPadding(pad, pad, pad, pad);

        TextView big = new TextView(this);
        big.setText("⛈ " + (t == null ? "SẮP MƯA" : t));
        big.setTextColor(Color.WHITE);
        big.setTextSize(36);
        big.setGravity(Gravity.CENTER);
        big.setTypeface(null, android.graphics.Typeface.BOLD);
        l.addView(big);

        TextView sub = new TextView(this);
        sub.setText(d == null ? "" : d);
        sub.setTextColor(Color.WHITE);
        sub.setTextSize(18);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(16), 0, dp(32));
        l.addView(sub);

        Button ok = new Button(this);
        ok.setText("ĐÃ BIẾT – TẮT CÒI");
        ok.setTextSize(22);
        ok.setTextColor(Color.rgb(227, 18, 11));
        ok.setBackgroundColor(Color.WHITE);
        ok.setMinHeight(dp(72));
        ok.setOnClickListener(v -> {
            Prefs p = new Prefs(this);
            p.setAcked(true);
            p.log("Đã xác nhận cảnh báo");
            getSystemService(NotificationManager.class).cancel(RainCheck.ID_ALARM);
            finish();
        });
        l.addView(ok, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(l);
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
