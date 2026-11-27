package vn.vas.canhbaomua;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tải dữ liệu: radar RainViewer (có lưu đệm để đỡ tốn mạng/pin) và dự báo Open-Meteo. */
public class Net {
    static class Http { int code; byte[] body; }

    static Http get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "CanhBaoMua/1.0 (Android)");
        try {
            Http h = new Http();
            h.code = c.getResponseCode();
            if (h.code == 200) {
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    h.body = bo.toByteArray();
                }
            }
            return h;
        } finally { c.disconnect(); }
    }

    static String getText(String url) throws IOException {
        Http h = get(url);
        if (h.code != 200 || h.body == null) throw new IOException("HTTP " + h.code);
        return new String(h.body, "UTF-8");
    }

    /** Ảnh radar 30 phút gần nhất (4 khung), dạng mặt nạ có/không mưa quanh công trình. */
    public static List<Nowcast.Frame> radarFrames(Context ctx, double lat, double lon, int thr) throws Exception {
        JSONObject j = new JSONObject(getText("https://api.rainviewer.com/public/weather-maps.json"));
        String host = j.getString("host");
        JSONArray past = j.getJSONObject("radar").getJSONArray("past");
        double[] g = Nowcast.ll2px(lat, lon);
        int gx = (int) Math.round(g[0]), gy = (int) Math.round(g[1]);
        File dir = new File(ctx.getCacheDir(), "radar");
        dir.mkdirs();
        String key = gx + "_" + gy + "_" + thr;
        List<Nowcast.Frame> out = new ArrayList<>();
        for (int i = Math.max(0, past.length() - 4); i < past.length(); i++) {
            JSONObject f = past.getJSONObject(i);
            long t = f.getLong("time");
            File cf = new File(dir, "m_" + t + "_" + key + ".bin");
            byte[] m = null;
            if (cf.exists() && cf.length() == (long) Nowcast.W * Nowcast.W) m = readAll(cf);
            if (m == null) {
                m = buildMask(host, f.getString("path"), gx, gy, thr);
                if (m != null) try (FileOutputStream fo = new FileOutputStream(cf)) { fo.write(m); }
            }
            if (m != null) { Nowcast.Frame fr = new Nowcast.Frame(); fr.t = t; fr.m = m; out.add(fr); }
        }
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (System.currentTimeMillis() - f.lastModified() > 3 * 3600_000L) f.delete();
        return out;
    }

    static byte[] readAll(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return off == b.length ? b : null;
        } catch (IOException e) { return null; }
    }

    static byte[] buildMask(String host, String path, int gx, int gy, int thr) {
        final int W = Nowcast.W, R = Nowcast.R, TS = Nowcast.TS;
        byte[] m = new byte[W * W];
        int ok = 0;
        int tx0 = Math.floorDiv(gx - R, TS), tx1 = Math.floorDiv(gx + R, TS);
        int ty0 = Math.floorDiv(gy - R, TS), ty1 = Math.floorDiv(gy + R, TS);
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++) {
                Http h;
                try { h = get(host + path + "/256/" + Nowcast.Z + "/" + tx + "/" + ty + "/2/0_0.png"); }
                catch (IOException e) { continue; }
                if (h.code == 404 || h.code == 204) { ok++; continue; } // không có dữ liệu = không mưa
                if (h.code != 200 || h.body == null) continue;
                Bitmap b = BitmapFactory.decodeByteArray(h.body, 0, h.body.length);
                if (b == null) continue;
                ok++;
                int bw = b.getWidth(), bh = b.getHeight();
                int[] px = new int[bw * bh];
                b.getPixels(px, 0, bw, 0, 0, bw, bh);
                b.recycle();
                double sc = bw / (double) TS;
                for (int y = 0; y < W; y++) {
                    int ly = gy - R + y - ty * TS;
                    if (ly < 0 || ly >= TS) continue;
                    int py = Math.min(bh - 1, (int) (ly * sc));
                    for (int x = 0; x < W; x++) {
                        int lx = gx - R + x - tx * TS;
                        if (lx < 0 || lx >= TS) continue;
                        int a = px[py * bw + Math.min(bw - 1, (int) (lx * sc))] >>> 24;
                        if (a >= thr) m[y * W + x] = 1;
                    }
                }
            }
        return ok > 0 ? m : null;
    }

    public static class Model { public double mm; public int prob; public double cape; }

    public static Model model(double lat, double lon, int lead) throws Exception {
        String u = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&minutely_15=precipitation&forecast_minutely_15=8&hourly=precipitation_probability,cape"
                + "&forecast_hours=4&timezone=auto", lat, lon);
        JSONObject j = new JSONObject(getText(u));
        Model m = new Model();
        JSONArray mp = j.optJSONObject("minutely_15") != null ? j.getJSONObject("minutely_15").optJSONArray("precipitation") : null;
        int slots = (int) Math.ceil(lead / 15.0);
        if (mp != null) for (int i = 0; i < Math.min(slots, mp.length()); i++) m.mm += mp.optDouble(i, 0);
        JSONObject hr = j.optJSONObject("hourly");
        if (hr != null) {
            JSONArray pp = hr.optJSONArray("precipitation_probability"), cp = hr.optJSONArray("cape");
            if (pp != null) for (int i = 0; i < Math.min(2, pp.length()); i++) m.prob = Math.max(m.prob, pp.optInt(i, 0));
            if (cp != null) for (int i = 0; i < Math.min(3, cp.length()); i++) m.cape = Math.max(m.cape, cp.optDouble(i, 0));
        }
        if (Double.isNaN(m.mm)) m.mm = 0;
        if (Double.isNaN(m.cape)) m.cape = 0;
        return m;
    }
}
