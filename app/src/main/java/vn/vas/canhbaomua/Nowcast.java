package vn.vas.canhbaomua;

import java.util.ArrayList;
import java.util.List;

/** Dự báo tức thời từ ảnh radar: tìm hướng/tốc độ mây mưa và thời gian tới công trình. Thuần Java. */
public class Nowcast {
    public static final int Z = 7, TS = 256, R = 100, W = 2 * R + 1;
    public static final double SITE_KM = 1.0; // ~1 điểm ảnh radar quanh đúng toạ độ

    public static class Frame { public long t; public byte[] m; }

    public static class Result {
        public String lvl = "g";
        public boolean raining, hasV;
        public Integer etaAdv, etaClose;
        public Double near, speed, closing;
        public double vx, vy, kmpp;
        public int nearDx, nearDy;
        public long ageMin;
    }

    public static double[] ll2px(double lat, double lon) {
        double n = TS * Math.pow(2, Z), s = Math.sin(Math.toRadians(lat));
        return new double[]{(lon + 180) / 360 * n, (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * n};
    }

    public static double kmPerPx(double lat) {
        return 40075.016 * Math.cos(Math.toRadians(lat)) / (TS * Math.pow(2, Z));
    }

    /** Độ dịch {dx,dy,score} để mưa ở A (cũ) khớp với B (mới). */
    static double[] bestShift(byte[] A, byte[] B, int maxS) {
        final int st = 3;
        int nA = 0, nB = 0;
        for (int y = maxS; y < W - maxS; y += st)
            for (int x = maxS; x < W - maxS; x += st) { nA += A[y * W + x]; nB += B[y * W + x]; }
        if (nA < 6 || nB < 6) return null;
        int best = -1, bx = 0, by = 0;
        for (int dy = -maxS; dy <= maxS; dy++)
            for (int dx = -maxS; dx <= maxS; dx++) {
                int sc = 0;
                for (int y = maxS; y < W - maxS; y += st) {
                    int o = y * W, oa = (y - dy) * W - dx;
                    for (int x = maxS; x < W - maxS; x += st) if (B[o + x] != 0 && A[oa + x] != 0) sc++;
                }
                if (sc > best || (sc == best && dx * dx + dy * dy < bx * bx + by * by)) { best = sc; bx = dx; by = dy; }
            }
        return new double[]{bx, by, best / Math.sqrt((double) nA * nB)};
    }

    static double fracInCircle(byte[] M, double cx, double cy, double rp) {
        int hit = 0, tot = 0;
        double r2 = rp * rp;
        for (int y = (int) Math.floor(cy - rp); y <= cy + rp; y++)
            for (int x = (int) Math.floor(cx - rp); x <= cx + rp; x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r2) continue;
                if (x < 0 || y < 0 || x >= W || y >= W) continue;
                tot++; hit += M[y * W + x];
            }
        return tot > 0 ? (double) hit / tot : 0;
    }

    /** {khoảng cách px, dx, dy} tới điểm mưa gần nhất (bỏ điểm nhiễu lẻ), hoặc null. */
    static double[] nearestRain(byte[] M) {
        int best = Integer.MAX_VALUE, bx = 0, by = 0;
        for (int y = 1; y < W - 1; y++)
            for (int x = 1; x < W - 1; x++) {
                if (M[y * W + x] == 0) continue;
                int n = M[y * W + x - 1] + M[y * W + x + 1] + M[(y - 1) * W + x] + M[(y + 1) * W + x];
                if (n < 2) continue;
                int d = (x - R) * (x - R) + (y - R) * (y - R);
                if (d < best) { best = d; bx = x - R; by = y - R; }
            }
        return best == Integer.MAX_VALUE ? null : new double[]{Math.sqrt(best), bx, by};
    }

    public static Result analyze(List<Frame> masks, double kmpp, int lead, double fracThr) {
        Frame L = masks.get(masks.size() - 1);
        Result out = new Result();
        out.kmpp = kmpp;
        double sx = 0, sy = 0, sw = 0;
        for (int k = 1; k <= 3; k++) {
            int idx = masks.size() - 1 - k;
            if (idx < 0) continue;
            Frame A = masks.get(idx);
            double dt = (L.t - A.t) / 60.0;
            if (dt <= 0) continue;
            int maxS = Math.min(40, (int) Math.ceil(dt * 1.4 / kmpp) + 2);
            double[] s = bestShift(A.m, L.m, maxS);
            if (s != null && s[2] >= 0.3) { sx += s[0] / dt * s[2]; sy += s[1] / dt * s[2]; sw += s[2]; }
        }
        if (sw > 0) {
            out.vx = sx / sw; out.vy = sy / sw;
            out.speed = Math.hypot(out.vx, out.vy) * kmpp * 60;
            out.hasV = out.speed <= 100;
        }
        out.raining = fracInCircle(L.m, R, R, SITE_KM / kmpp) >= fracThr;
        if (out.hasV) {
            for (int t = 0; t <= 120; t += 5) {
                double px = R - out.vx * t, py = R - out.vy * t, rp = (SITE_KM + 0.02 * t) / kmpp;
                if (px < 0 || py < 0 || px >= W || py >= W) break;
                if (fracInCircle(L.m, px, py, rp) >= fracThr) { out.etaAdv = t; break; }
            }
        }
        List<double[]> pts = new ArrayList<>(); // {phút, km}
        double[] last = null;
        for (int i = Math.max(0, masks.size() - 4); i < masks.size(); i++) {
            double[] n = nearestRain(masks.get(i).m);
            if (n != null) pts.add(new double[]{masks.get(i).t / 60.0, n[0] * kmpp});
            if (i == masks.size() - 1) last = n;
        }
        if (last != null) { out.near = last[0] * kmpp; out.nearDx = (int) last[1]; out.nearDy = (int) last[2]; }
        if (pts.size() >= 3 && last != null) {
            double mt = 0, md = 0;
            for (double[] p : pts) { mt += p[0]; md += p[1]; }
            mt /= pts.size(); md /= pts.size();
            double num = 0, den = 0;
            for (double[] p : pts) { num += (p[0] - mt) * (p[1] - md); den += (p[0] - mt) * (p[0] - mt); }
            double slope = den > 0 ? num / den : 0;
            out.closing = -slope * 60;
            if (slope < -0.1) out.etaClose = (int) Math.round(out.near / -slope);
        }
        String lvl = "g";
        // Chỉ báo động khi mưa đang ở / đang đi trúng toạ độ công trình
        if (out.raining || (out.etaAdv != null && out.etaAdv <= lead)) lvl = "r";
        else if ((out.near != null && out.near <= 25) || (out.etaAdv != null && out.etaAdv <= lead + 30)) lvl = "y";
        out.lvl = lvl;
        return out;
    }

    public static String dirName(double dx, double dy) {
        double a = (Math.toDegrees(Math.atan2(dx, -dy)) + 360) % 360;
        String[] n = {"Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc"};
        return n[(int) Math.round(a / 45) % 8];
    }
}
