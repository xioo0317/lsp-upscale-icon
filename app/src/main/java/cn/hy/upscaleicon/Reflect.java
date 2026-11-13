package cn.hy.upscaleicon;

import android.content.Context;
import android.graphics.Bitmap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 Java 反射工具 —— 替代现代 libxposed API 中已不存在的 XposedHelpers。
 *
 * <p>宿主 IconFormat / MorphIconInfo 的字段名是 R8 产物（f30742a 之类），
 * 跨版本会重排，因此所有取值都带按类型的兜底策略。</p>
 */
final class Reflect {

    private Reflect() {
    }

    /** 按候选名依次取对象字段，全失败返回 null。 */
    static Object getObject(Object obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            try {
                Field f = findField(obj.getClass(), n);
                if (f == null) continue;
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 按候选名依次取 int 字段，全失败返回 -1。 */
    static int getInt(Object obj, String... names) {
        if (obj == null) return -1;
        for (String n : names) {
            try {
                Field f = findField(obj.getClass(), n);
                if (f == null || f.getType() != int.class) continue;
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /** 字段名漂移兜底：按类型找第一个可用的 Bitmap 实例字段。 */
    static Bitmap findBitmap(Object obj) {
        if (obj == null) return null;
        for (Class<?> k = obj.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getType() != Bitmap.class || Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Bitmap b = (Bitmap) f.get(obj);
                    if (b != null && !b.isRecycled()) return b;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** 启发式：按声明顺序收集前 count 个实例 int 字段，供 spanX/spanY 兜底。 */
    static int[] firstInts(Object obj, int count) {
        List<Integer> out = new ArrayList<>();
        for (Class<?> k = obj.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (out.size() >= count) break;
                if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    out.add(f.getInt(obj));
                } catch (Throwable ignored) {
                }
            }
            if (out.size() >= count) break;
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /**
     * 在 MorphIconInfo 里找 IconFormat：返回第一个「自身声明了 Bitmap 字段」的
     * 对象字段值。f30771e 漂移时仍可命中。
     */
    static Object findFormatObject(Object info) {
        if (info == null) return null;
        for (Class<?> k = info.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t.isPrimitive() || t == String.class || t == Context.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(info);
                    if (v != null && hasBitmapField(v.getClass())) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean hasBitmapField(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getType() == Bitmap.class) return true;
            }
        }
        return false;
    }
}
