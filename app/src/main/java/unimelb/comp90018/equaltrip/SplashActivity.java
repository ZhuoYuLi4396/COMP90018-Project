package unimelb.comp90018.equaltrip;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        FirebaseAuth.getInstance().signOut();//test delete anytime
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();

            boolean goHome = user != null && !user.isAnonymous();

            Class<?> target = goHome ? HomeActivity.class : SignUpActivity.class;

            android.content.Intent next =
                    new android.content.Intent(SplashActivity.this, target)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(next);
            finish();
        }, 1200);
    }
}
