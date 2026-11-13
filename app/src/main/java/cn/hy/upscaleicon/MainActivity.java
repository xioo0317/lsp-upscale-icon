package cn.hy.upscaleicon;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * 模块设置页（极简占位）。真正的开关在 LSPosed 管理器里：
 * 勾选本模块 → 作用域选 com.android.launcher → 重启桌面。
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#FF3A3A3A"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(48, 48, 48, 48);
        tv.setText("大图标放大 · UpscaleIcon\n\n"
                + "libxposed 现代 API 101 模块\n"
                + "作用域：com.android.launcher（ColorOS 系统桌面 17.3.12）\n\n"
                + "启用方式：\n"
                + "1. 在 LSPosed 管理器启用本模块\n"
                + "2. 作用域勾选「系统桌面」\n"
                + "3. 强制停止桌面 / 重启手机\n\n"
                + "效果：2x1 / 1x2 / 2x2 大图标不再读取主题专供素材，\n"
                + "改为直接把原 1x1 图标等比放大。\n\n"
                + "注意：图标结果按尺寸缓存，首次启用建议清除桌面数据。");
        setContentView(tv);
    }
}
