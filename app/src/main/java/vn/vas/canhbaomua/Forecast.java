package vn.vas.canhbaomua;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dự báo tổ hợp (ensemble) từ nhiều trung tâm khí tượng: mỗi trung tâm chạy hàng chục kịch bản.
 * Khả năng mưa = trung bình (theo từng mô hình) tỉ lệ kịch bản có mưa. Mô hình nào lỗi thì bỏ qua.
 */
public class Forecast {
    static final String[][] MODELS = {
            {"ecmwf_ifs025", "ECMWF (châu Âu)"},
            {"ecmwf_aifs025", "ECMWF AI"},
            {"gfs025", "GFS (Mỹ)"},
            {"icon_global", "ICON (Đức)"},
            {"ukmo_global_ensemble_20km", "UKMO (Anh)"},
            {"gem_global", "GEM (Canada)"}};
    static final double WET = 0.1; // mm/giờ

    public static class Hour { public String time; public int prob, agree, n; public double mm; public String hres = "-"; }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("cb_fc", Context.MODE_PRIVATE); }

    /** Tải lại nếu dữ liệu cũ hơn maxAgeMin. Chạy ở luồng nền. */
    public static void refreshIfOld(Context c, double lat, double lon, int maxAgeMin) {
        SharedPreferences s = sp(c);
        boolean moved = Math.abs(s.getFloat("lat", 0) - lat) > 0.05 || Math.abs(s.getFloat("lon", 0) - lon) > 0.05;
        if (!moved && System.currentTimeMillis() - s.getLong("t", 0) < maxAgeMin * 60_000L) return;
        Map<String, List<Double>> fracs = new LinkedHashMap<>();
        Map<String, List<Double>> vals = new LinkedHashMap<>();
        List<String> okModels = new ArrayList<>();
        int members = 0;
        for (String[] m : MODELS) {
            try {
                String u = String.format(Locale.US, "https://ensemble-api.open-meteo.com/v1/ensemble?latitude=%.4f&longitude=%.4f"
                        + "&hourly=precipitation&models=%s&forecast_days=3&timezone=auto&cell_selection=nearest", lat, lon, m[0]);
                JSONObject hr = new JSONObject(Net.getText(u)).getJSONObject("hourly");
                JSONArray time = hr.getJSONArray("time");
                List<JSONArray> mem = new ArrayList<>();
                Iterator<String> it = hr.keys();
                while (it.hasNext()) { String k = it.next(); if (k.startsWith("precipitation")) mem.add(hr.getJSONArray(k)); }
                if (mem.isEmpty()) continue;
                boolean any = false;
                for (int i = 0; i < time.length(); i++) {
                    int n = 0, wet = 0;
                    for (JSONArray a : mem) {
                        if (a.isNull(i)) continue;
                        double v = a.optDouble(i, Double.NaN);
                        if (Double.isNaN(v)) continue;
                        n++; if (v >= WET) wet++;
                        vals.computeIfAbsent(time.getString(i), k -> new ArrayList<>()).add(v);
                    }
                    if (n == 0) continue;
                    any = true;
                    fracs.computeIfAbsent(time.getString(i), k -> new ArrayList<>()).add(wet / (double) n);
                }
                if (any) { okModels.add(m[1]); members += mem.size(); }
            } catch (Exception ignored) { }
        }
        if (okModels.isEmpty()) return; // giữ dữ liệu cũ
        Map<String, Double> hres = new LinkedHashMap<>();
        String hresName = "";
        for (String[] hm : new String[][]{{"ecmwf_ifs", "ECMWF 9 km"}, {"best_match", "mô hình chi tiết nhất"}}) {
            try {
                String u = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&hourly=precipitation&models=%s&forecast_days=3&timezone=auto&cell_selection=nearest", lat, lon, hm[0]);
                JSONObject hr = new JSONObject(Net.getText(u)).getJSONObject("hourly");
                JSONArray time = hr.getJSONArray("time"), pr = null;
                Iterator<String> it = hr.keys();
                while (it.hasNext()) { String k = it.next(); if (k.startsWith("precipitation")) pr = hr.getJSONArray(k); }
                if (pr == null) continue;
                for (int i = 0; i < time.length(); i++) if (!pr.isNull(i)) hres.put(time.getString(i), pr.optDouble(i, 0));
                if (!hres.isEmpty()) { hresName = hm[1]; break; }
            } catch (Exception ignored) { }
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<Double>> e : fracs.entrySet()) {
            List<Double> f = e.getValue();
            double mean = 0; int agree = 0;
            for (double x : f) { mean += x; if (x >= 0.5) agree++; }
            mean /= f.size();
            List<Double> v = vals.get(e.getKey());
            Collections.sort(v);
            double med = v.isEmpty() ? 0 : v.get(v.size() / 2);
            b.append(e.getKey()).append(';').append(Math.round(mean * 100)).append(';')
                    .append(String.format(Locale.US, "%.1f", med)).append(';').append(agree).append(';').append(f.size()).append(';')
                    .append(hres.containsKey(e.getKey()) ? String.format(Locale.US, "%.1f", hres.get(e.getKey())) : "-").append('\n');
        }
        s.edit().putString("data", b.toString()).putLong("t", System.currentTimeMillis())
                .putString("models", String.join(", ", okModels)).putString("hres", hresName).putInt("members", members)
                .putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
    }

    public static List<Hour> hours(Context c) {
        List<Hour> out = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(new Date());
        for (String line : sp(c).getString("data", "").split("\n")) {
            String[] p = line.split(";");
            if (p.length < 5 || p[0].length() < 13) continue;
            if (p[0].substring(0, 13).compareTo(now) < 0) continue;
            Hour h = new Hour();
            h.time = p[0]; h.prob = Integer.parseInt(p[1]); h.mm = Double.parseDouble(p[2]);
            h.agree = Integer.parseInt(p[3]); h.n = Integer.parseInt(p[4]); if (p.length > 5) h.hres = p[5];
            out.add(h);
        }
        return out;
    }

    /** Khả năng mưa cao nhất trong `hrs` giờ tới, -1 nếu chưa có dữ liệu. */
    public static int maxProb(Context c, int hrs) {
        int best = -1, i = 0;
        for (Hour h : hours(c)) { if (i++ >= hrs) break; best = Math.max(best, h.prob); }
        return best;
    }

    public static String models(Context c) { return sp(c).getString("models", ""); }
    public static int members(Context c) { return sp(c).getInt("members", 0); }
    public static long time(Context c) { return sp(c).getLong("t", 0); }

    static String bar(int prob) {
        int n = Math.round(prob / 10f);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(i < n ? '█' : '░');
        return b.toString();
    }

    /** Văn bản hiển thị cho 36 giờ tới. */
    public static String render(Context c) {
        List<Hour> hs = hours(c);
        if (hs.isEmpty()) return "Chưa có dữ liệu dự báo (cần mạng).";
        StringBuilder b = new StringBuilder();
        Prefs pf = new Prefs(c);
        if (pf.statusTime() > 0 && System.currentTimeMillis() - pf.statusTime() < 30 * 60_000L)
            b.append("ĐÚNG TOẠ ĐỘ CÔNG TRÌNH, 1–2 GIỜ TỚI (radar):\n").append(pf.title()).append("\n\n");
        String hn = sp(c).getString("hres", "");
        if (!hn.isEmpty()) b.append("Cột “ô 9km” = lượng mưa ").append(hn).append(" tại ô sát công trình nhất.\n\n");
        // Khung giờ dễ mưa nhất trong ngày hôm nay
        String today = hs.get(0).time.substring(0, 10);
        int bs = -1, be = -1, peak = 0;
        for (int i = 0; i < hs.size(); i++) {
            Hour h = hs.get(i);
            if (!h.time.startsWith(today)) break;
            if (h.prob >= 50) { if (bs < 0) bs = i; be = i; }
            peak = Math.max(peak, h.prob);
        }
        if (bs >= 0) b.append(String.format(Locale.US, "Hôm nay dễ mưa nhất: %sh – %dh (cao nhất %d%%)\n\n",
                hs.get(bs).time.substring(11, 13), Integer.parseInt(hs.get(be).time.substring(11, 13)) + 1, peak));
        else b.append(String.format(Locale.US, "Hôm nay ít khả năng mưa (cao nhất %d%%)\n\n", peak));
        String day = "";
        int shown = 0;
        for (Hour h : hs) {
            if (shown++ >= 36) break;
            String d = h.time.substring(0, 10);
            if (!d.equals(day)) {
                day = d;
                b.append(d.equals(today) ? "— Hôm nay —\n" : "— Ngày " + d.substring(8, 10) + "/" + d.substring(5, 7) + " —\n");
            }
            b.append(String.format(Locale.US, "%sh %s %3d%%  %d/%d MH  ô 9km: %s mm\n",
                    h.time.substring(11, 13), bar(h.prob), h.prob, h.agree, h.n, h.hres));
        }
        return b.toString();
    }
}
