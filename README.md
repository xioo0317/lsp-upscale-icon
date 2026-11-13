# UpscaleIcon · ColorOS 大图标放大模块

把 OPPO/一加 ColorOS **系统桌面 17.3.12** 的 `2x1 / 1x2 / 2x2` 大图标，从「读取主题专供素材 + 形状 mask + 材质高光层合成」改为**直接把原 1×1 图标等比放大**。

基于 **libxposed 现代 Xposed API**（[libxposed/api](https://github.com/libxposed/api)），不使用旧的 `XposedBridge` / `XposedHelpers` / `IXposedHookLoadPackage`。

> 状态：静态反编译推导 + 本地编译通过；**未上机验证**。宿主类名/方法名/字段名来自 R8 后的 17.3.12，其他版本需重新核对。

## 原理

宿主的 2x1/2x2 大图标由 `com.android.common.util.i6`（IconUtils）统一合成：

```
BubbleTextView → IconUtils.W() / r()
  → com.android.common.util.i6.C(Context, IconFormat, ComponentName) → Drawable
  → tr.k → tr.m.n0() → 读 /data/oplus/uxicons/<pkg>/..._2x2.png
  → 形状 mask + 渐变背景 + 材质高光层 → AdaptiveIconDrawable
```

本模块在 `i6.C(...)`（主链路）与 `i6.r(...)`（大文件夹容器链路）上挂 `Hooker.intercept`：

- 目标 span > 1×1 时，从 `IconFormat` 里取出已缓存的原 1×1 图标 `Bitmap`，等比放大居中，返回一个 `AdaptiveIconDrawable`，**不调用 `chain.proceed()`** → 整条主题素材链被跳过；
- 1×1、参数缺失、取图失败时调用 `chain.proceed()` 放行原逻辑；
- 所有异常都在 hook 内吞掉并记录，绝不把异常抛给宿主。

## API 版本

`module.prop` 中 `minApiVersion=101`；`targetApiVersion` 与编译依赖保持一致：

| 变体 | 依赖 | targetApiVersion |
| --- | --- | --- |
| API 101（默认，用户指定） | `io.github.libxposed:api:101.0.1` | 101 |
| API 102（libxposed 当前版） | `io.github.libxposed:api:102.0.0` | 102 |

101 与 102 的 `hook(Executable).intercept(Hooker)` 链式签名一致，本模块只使用 101 就存在的接口，因此两个变体共用同一份源码。CI 用矩阵同时产出两个构件。

## 构建

```bash
# 需要 JDK 17+、Android SDK 35
./gradlew :app:assembleRelease -PxposedApiVersion=101.0.1 -PxposedTargetApi=101

# 102 变体
./gradlew :app:assembleRelease -PxposedApiVersion=102.0.0 -PxposedTargetApi=102
```

产物在 `app/build/outputs/apk/release/`。Release 用 `signingConfigs.debug` 签名，可直接安装（正式发布请换成自己的 keystore）。

CI（`.github/workflows/build.yml`）在 push / PR / tag 时自动构建，会**校验 APK 内确实包含 `META-INF/xposed/{module.prop,java_init.list,scope.list}`**，再上传 APK artifact；打 `v*` tag 时自动挂到 GitHub Release。

## 安装

1. 设备需已安装支持 libxposed 现代 API 的框架（如 LSPosed v2.0.1+，其完整支持 API 101）；
2. 安装本模块 APK；
3. 在框架管理器启用模块，作用域勾选 **系统桌面 `com.android.launcher`**（`scope.list` 已固定，`staticScope=true`）；
4. 强制停止桌面或重启。图标结果按尺寸缓存，**首次启用建议清除桌面数据**。

## 模块元数据

libxposed 现代 API 不用 `AndroidManifest.xml` 里的 `xposedmodule` meta-data，改用 `META-INF/xposed/` 下三个文件：

| 文件 | 作用 |
| --- | --- |
| `module.prop` | `minApiVersion` / `targetApiVersion` / `staticScope` |
| `java_init.list` | 入口类，每行一个：`cn.hy.upscaleicon.ModuleMain` |
| `scope.list` | 注入作用域：`com.android.launcher` |

## 目录

```
app/src/main/java/cn/hy/upscaleicon/
  ModuleMain.java   — 入口（extends XposedModule），按签名查找并 hook 宿主方法
  IconScaler.java   — 原 1×1 图标等比放大，生成 AdaptiveIconDrawable
  Reflect.java      — 纯 Java 反射，替代已移除的 XposedHelpers
  MainActivity.java — 极简说明页
app/src/main/resources/META-INF/xposed/ — 模块元数据（必须打进 APK）
.github/workflows/build.yml            — CI 构建 + 自检 + 上传构件
```

## 风险

- 模块**未编译上机验证**，视觉效果需真机确认是否还有高光层二次叠加；
- R8 字段名（`f30742a`、`f30752k` 等）随桌面版本漂移，代码已带按类型的兜底，但仍建议按版本核对；
- Hook 后需清桌面数据/重启桌面才会重新生成图标缓存。

## 合规

仅供个人设备上对自有应用进行界面定制与逆向学习使用。请遵守设备厂商条款与当地法律。
