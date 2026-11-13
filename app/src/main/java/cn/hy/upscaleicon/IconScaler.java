package cn.hy.upscaleicon;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/**
 * 把 IconFormat 里已经缓存好的原 1x1 图标 Bitmap 等比放大到目标格子尺寸。
 *
 * <p>刻意不裁切、不叠形状 mask、不叠材质高光层 —— 这正是要替换掉的宿主行为。
 * 返回 null 表示「不接管」，宿主继续走它自己的大图标合成逻辑。</p>
 */
final class IconScaler {

    /** 放大后图标占目标区域的比例，留一点白，视觉上更接近原生大图标留白。 */
    private static final float FILL_RATIO = 0.92f;

    private IconScaler() {
    }

    static Drawable buildScaled(Context ctx, Object fmt, Object cnObj) {
        if (ctx == null || fmt == null) return null;

        // 1) 目标 span：1x1 保持原生链路，绝不接管
        int spanX = Reflect.getInt(fmt, "f30742a");
        int spanY = Reflect.getInt(fmt, "f30745d");
        if (spanX <= 0 || spanY <= 0) {
            int[] ints = Reflect.firstInts(fmt, 4);
            if (ints.length >= 2) {
                spanX = ints[0];
                spanY = ints[1];
            }
        }
        if (spanX <= 1 && spanY <= 1) return null;

        // 2) 原 1x1 图标：IconUtils.W() 已把 loadFromMorphIconCache(1,1) 塞进 f30752k
        Bitmap src = Reflect.findBitmap(fmt);
        if (src == null && cnObj instanceof ComponentName) {
            src = drawableToBitmap(loadPmIcon(ctx, (ComponentName) cnObj));
        }
        if (src == null || src.isRecycled()) return null;

        // 3) 目标像素尺寸：IconUtils.x(DeviceProfile, spanX, spanY) 的计算结果
        int w = Reflect.getInt(fmt, "f30743b");
        int h = Reflect.getInt(fmt, "f30744c");
        if (w <= 0 || h <= 0) {
            // 兜底：与 com.oplus.uxicon.ui.morphicon.q 的标准比例一致
            //   1x2=108x288 / 2x1=288x108 / 2x2=288x288
            w = spanX >= 2 ? 288 : 108;
            h = spanY >= 2 ? 288 : 108;
        }
        // 上限保护：字段漂移读到异常大值时避免 OOM 拖垮桌面
        w = Math.min(Math.max(w, 1), 1024);
        h = Math.min(Math.max(h, 1), 1024);

        // 4) 等比放大 + 居中 + 透明留白
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float scale = Math.min((float) w / src.getWidth(), (float) h / src.getHeight()) * FILL_RATIO;
        int dw = Math.max(1, Math.round(src.getWidth() * scale));
        int dh = Math.max(1, Math.round(src.getHeight() * scale));
        Rect dst = new Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, null, dst, paint);

        Drawable fg = new BitmapDrawable(ctx.getResources(), out);
        // loadClusterIcon 分支要求返回值 instanceof AdaptiveIconDrawable，统一包一层透明背景
        return new AdaptiveIconDrawable(new ColorDrawable(Color.TRANSPARENT), fg);
    }

    /** 兜底取应用原始图标，与 tr.m.D() = getActivityIcon 的行为一致。 */
    private static Drawable loadPmIcon(Context ctx, ComponentName cn) {
        PackageManager pm = ctx.getPackageManager();
        try {
            if (cn != null && cn.getClassName() != null && !cn.getClassName().isEmpty()) {
                Drawable d = pm.getActivityIcon(cn);
                if (d != null) return d;
            }
        } catch (Throwable ignored) {
        }
        try {
            return pm.getApplicationIcon(cn != null ? cn.getPackageName() : "");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        if (d == null) return null;
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) return b;
        }
        int w = Math.max(1, d.getIntrinsicWidth());
        int h = Math.max(1, d.getIntrinsicHeight());
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }
}
