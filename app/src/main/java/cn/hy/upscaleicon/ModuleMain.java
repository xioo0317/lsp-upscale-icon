package cn.hy.upscaleicon;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * ColorOS 系统桌面 17.3.12 ——「大图标改用原 1x1 图标等比放大」模块入口。
 *
 * <p>使用 libxposed 现代 API（module.prop 中 minApiVersion=101），
 * 不再依赖 XposedBridge / XposedHelpers / IXposedHookLoadPackage。
 * 入口由 {@code META-INF/xposed/java_init.list} 注册，
 * 作用域由 {@code META-INF/xposed/scope.list} 固定为 com.android.launcher。</p>
 *
 * <p>原理：宿主的 2x1 / 1x2 / 2x2 大图标由 {@code com.android.common.util.i6}
 * 统一合成。本模块在其两个出口方法上直接返回「把原 1x1 图标等比放大」的
 * {@link android.graphics.drawable.AdaptiveIconDrawable}，并且<strong>不调用
 * chain.proceed()</strong>，从而跳过 tr.k → tr.m → 主题素材 PNG 读取 →
 * 形状 mask → 材质高光层的整条链。</p>
 *
 * <p>现代 API 的 Hooker.intercept 契约：直接 return 替代值 == 替换返回值；
 * 调用 chain.proceed() 并返回其结果 == 放行原逻辑。这与旧版
 * beforeHookedMethod + setResult 等价，但类型安全且无需 XposedHelpers。</p>
 *
 * <p>所有宿主类名 / 方法名 / 字段名均来自 R8 后的 17.3.12。方法查找同时支持
 * 「精确名」与「按签名+包名启发式」两种策略，字段名漂移时按类型兜底；
 * 任何一步失败都静默放行，绝不把异常抛给宿主，避免桌面反复重启。</p>
 */
public final class ModuleMain extends XposedModule {

    static final String TAG = "UpscaleIcon";

    /** 作用域：17.3.12 实测包名（androguard 解析 AndroidManifest 确认）。 */
    private static final Set<String> TARGET_PKGS = new HashSet<>(Arrays.asList(
            "com.android.launcher"));

    /** 未混淆的公共入口类 IconUtils，是整条大图标合成链的统一出口。 */
    private static final String CLS_ICON_UTILS = "com.android.common.util.i6";

    /** 是否同时覆盖大文件夹容器路径 IconUtils.r(Context, MorphIconInfo)。 */
    private static final boolean ENABLE_CLUSTER_HOOK = true;

    private volatile boolean hooked;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName()
                + " | framework=" + getFrameworkName()
                + " | api=" + getApiVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (TARGET_PKGS.contains(param.getPackageName())) {
            install(param.getDefaultClassLoader());
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (TARGET_PKGS.contains(param.getPackageName())) {
            install(param.getClassLoader());
        }
    }

    private synchronized void install(ClassLoader cl) {
        if (hooked || cl == null) return;
        try {
            Class<?> utils = Class.forName(CLS_ICON_UTILS, false, cl);
            int count = 0;

            // ★ 主链路：C(Context, IconFormat, ComponentName) -> Drawable
            //   getMorphIconDrawable / loadMorphUxIcon 的统一出口。
            Method main = findMethod(utils, "C", 3, Context.class, ComponentName.class);
            if (main != null) {
                hook(main).setPriority(PRIORITY_DEFAULT).intercept(chain -> {
                    try {
                        Context ctx = (Context) chain.getArg(0);
                        Object fmt = chain.getArg(1);
                        Object cn = chain.getArg(2);
                        Drawable repl = IconScaler.buildScaled(ctx, fmt, cn);
                        if (repl != null) return repl;   // 不 proceed == 完全替换
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "C() hook failed, pass through", t);
                    }
                    return chain.proceed();
                });
                count++;
            }

            // ○ 备选链路：r(Context, MorphIconInfo) -> Drawable
            //   覆盖 loadClusterIcon / 大文件夹容器，返回值要求是 AdaptiveIconDrawable。
            if (ENABLE_CLUSTER_HOOK) {
                Method cluster = findMethod(utils, "r", 2, Context.class, null);
                if (cluster != null) {
                    hook(cluster).setPriority(PRIORITY_DEFAULT).intercept(chain -> {
                        try {
                            Context ctx = (Context) chain.getArg(0);
                            Object info = chain.getArg(1);
                            Object fmt = Reflect.getObject(info, "f30771e");
                            if (fmt == null) fmt = Reflect.findFormatObject(info);
                            if (fmt != null) {
                                Object cn = Reflect.getObject(info, "f30767a");
                                Drawable repl = IconScaler.buildScaled(ctx, fmt, cn);
                                if (repl != null) return repl;
                            }
                        } catch (Throwable t) {
                            log(Log.ERROR, TAG, "r() hook failed, pass through", t);
                        }
                        return chain.proceed();
                    });
                    count++;
                }
            }

            if (count == 0) {
                log(Log.WARN, TAG, "no target method in " + CLS_ICON_UTILS
                        + " (desktop version mismatch?), hooks NOT installed");
                return;
            }
            hooked = true;
            log(Log.INFO, TAG, count + " hook(s) installed for " + TARGET_PKGS);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install failed, pass through", t);
        }
    }

    /**
     * 在宿主类里按签名找目标方法。
     *
     * <p>先按精确方法名（"C" / "r"）匹配；若 R8 重命名导致找不到，
     * 退化为「参数个数 + 首参 Context + 末参 ComponentName + 返回 Drawable +
     * 中间参数类型来自 morphicon/uxicon 包」的启发式匹配，避免硬绑单个混淆名。</p>
     */
    private static Method findMethod(Class<?> cls, String name, int argc,
                                     Class<?> first, Class<?> last) {
        Method heuristic = null;
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length != argc) continue;
            if (first != null && p[0] != first) continue;
            if (last != null && p[argc - 1] != last) continue;
            if (!Drawable.class.isAssignableFrom(m.getReturnType())) continue;
            if (!Modifier.isStatic(m.getModifiers())) continue;

            int middleEnd = argc - (last != null ? 1 : 0);
            boolean uxType = false;
            for (int i = 1; i < middleEnd; i++) {
                String n = p[i].getName();
                if (n.contains("morphicon") || n.contains("uxicon")) {
                    uxType = true;
                    break;
                }
            }
            if (!uxType) continue;

            m.setAccessible(true);
            if (name.equals(m.getName())) return m;
            if (heuristic == null) heuristic = m;
        }
        return heuristic;
    }
}
