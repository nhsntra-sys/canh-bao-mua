package vn.vas.canhbaomua;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Prefs {
    final SharedPreferences sp;
    public Prefs(Context c) { sp = c.getSharedPreferences("cb", Context.MODE_PRIVATE); }

    public boolean running() { return sp.getBoolean("running", false); }
    public void setRunning(boolean v) { sp.edit().putBoolean("running", v).apply(); }
    public boolean hasLoc() { return sp.contains("lat"); }
    public double lat() { return Double.parseDouble(sp.getString("lat", "0")); }
    public double lon() { return Double.parseDouble(sp.getString("lon", "0")); }
    public void setLoc(double lat, double lon) {
        sp.edit().putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon))
                .putLong("locTime", System.currentTimeMillis()).apply();
    }
    public long locTime() { return sp.getLong("locTime", 0); }
    public boolean autoLoc() { return sp.getBoolean("autoLoc", true); }
    public void setAutoLoc(boolean v) { sp.edit().putBoolean("autoLoc", v).apply(); }
    public int lead() { return sp.getInt("lead", 45); }
    public void setLead(int v) { sp.edit().putInt("lead", v).apply(); }
    public int sens() { return sp.getInt("sens", 1); }
    public void setSens(int v) { sp.edit().putInt("sens", v).apply(); }
    public boolean workOnly() { return sp.getBoolean("workOnly", true); }
    public void setWorkOnly(boolean v) { sp.edit().putBoolean("workOnly", v).apply(); }
    public int workStart() { return sp.getInt("ws", 5); }
    public int workEnd() { return sp.getInt("we", 19); }
    public void setWork(int s, int e) { sp.edit().putInt("ws", s).putInt("we", e).apply(); }
    public boolean acked() { return sp.getBoolean("acked", false); }
    public void setAcked(boolean v) { sp.edit().putBoolean("acked", v).apply(); }
    public long next() { return sp.getLong("next", 0); }
    public void setNext(long v) { sp.edit().putLong("next", v).apply(); }

    public void saveStatus(String lvl, String title, String detail) {
        sp.edit().putString("lvl", lvl).putString("title", title).putString("detail", detail)
                .putLong("statusTime", System.currentTimeMillis()).apply();
    }
    public String lvl() { return sp.getString("lvl", ""); }
    public String title() { return sp.getString("title", "Chưa kiểm tra"); }
    public String detail() { return sp.getString("detail", ""); }
    public long statusTime() { return sp.getLong("statusTime", 0); }

    public void log(String msg) {
        String line = new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date()) + " – " + msg;
        String old = sp.getString("log", "");
        String all = line + (old.isEmpty() ? "" : "\n" + old);
        String[] parts = all.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 25); i++) { if (i > 0) b.append('\n'); b.append(parts[i]); }
        sp.edit().putString("log", b.toString()).apply();
    }
    public String logText() { return sp.getString("log", ""); }
}
