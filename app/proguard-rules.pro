# 官方 libxposed 要求的规则：入口类不能被移除，且混淆后需同步改写 java_init.list
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 反射访问的是宿主(R8 后)字段，不是本模块字段；这里保证辅助逻辑不被误删
-keep class cn.hy.upscaleicon.Reflect { *; }
-keep class cn.hy.upscaleicon.IconScaler { *; }

# compileOnly 的框架 API 不在 APK 中，压制 R8 警告
-dontwarn io.github.libxposed.api.**
