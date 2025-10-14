package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * 启动页：
 * - 检查当前是否有用户已登录
 * - 若已登录 -> 跳转 HomeActivity
 * - 若未登录 -> 跳转 SignInActivity
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Android 12+ 官方 SplashScreen
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 自定义开屏布局（你的 logo 或动画）
        setContentView(R.layout.activity_splash);

        // 删除这行：不要清空登录状态
        // FirebaseAuth.getInstance().signOut();

        // 延迟显示动画后再决定跳转去哪里
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = mAuth.getCurrentUser();

            Intent next;
            if (currentUser != null) {
                // 用户已登录，跳主页
                next = new Intent(SplashActivity.this, HomeActivity.class);
            } else {
                // 用户未登录，跳登录页
                next = new Intent(SplashActivity.this, SignInActivity.class);
            }

            // 保留任务栈清理逻辑（避免回退到闪屏页）
            next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(next);
            finish();

        }, 1200);  // 延迟 1.2 秒显示动画
    }
}

// 可以使用的版本，但无开局动画
/*
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ 获取 Firebase 身份验证实例
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // ✅ 用户已登录，直接进入主页
            Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
            startActivity(intent);
        } else {
            // ❌ 用户未登录，进入登录页面
            Intent intent = new Intent(SplashActivity.this, SignInActivity.class);
            startActivity(intent);
        }

        // 关闭闪屏页，防止返回
        finish();
    }
}
*/

// Old version, comments on 14th Oct 2025 by Ziyan
/*
package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Android 12+ 官方开屏
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 自定义开屏布局（可选）
        setContentView(R.layout.activity_splash);

        // 每次启动都先清掉会话
        FirebaseAuth.getInstance().signOut();

        // 如集成了 Google/Facebook，可选追加登出（按需解开）：
        // GoogleSignInClient gsc = GoogleSignIn.getClient(
        //         this, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build());
        // gsc.signOut();
        // LoginManager.getInstance().logOut();

        // 轻微延迟以展示开屏动画
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent next = new Intent(SplashActivity.this, SignInActivity.class) // 或改成 SignUpActivity.class
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(next);
            finish();
        }, 1200);
    }
}
*/