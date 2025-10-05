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
