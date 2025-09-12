package unimelb.comp90018.equaltrip;


import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;


public class SignInActivity extends AppCompatActivity {


    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private CheckBox cbRemember;
    private TextView tvError, tvForgot, tvNoAccount;
    private MaterialButton btnSignIn;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        mAuth = FirebaseAuth.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbRemember = findViewById(R.id.cbRemember);
        tvError = findViewById(R.id.tvError);
        tvForgot = findViewById(R.id.tvForgot);
        tvNoAccount = findViewById(R.id.tvNoAccount);
        btnSignIn = findViewById(R.id.btnSignIn);

        setupNoAccountLink();

        btnSignIn.setOnClickListener(v -> {
            if (validateForm()) {
                boolean remember = cbRemember != null && cbRemember.isChecked();
                signInUser();
//                Toast.makeText(this, "Sign in success!", Toast.LENGTH_SHORT).show();
            }
        });

        if (etPassword != null) {
            etPassword.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    btnSignIn.performClick();
                    return true;
                }
                return false;
            });
        }

        // “忘记密码？”
        if (tvForgot != null) {
            tvForgot.setOnClickListener(v -> {
                Intent intent = new Intent(SignInActivity.this, ForgotPasswordActivity.class);
                startActivity(intent);
            });
        }
    }

    private void signInUser() {
        String email = getText(etEmail);
        String password = getText(etPassword);
        boolean remember = cbRemember != null && cbRemember.isChecked();

        // Disable the login button and display a loading status.
        btnSignIn.setEnabled(false);
        btnSignIn.setText("Signing in...");

        // Using Firebase Authentication for Login Verification
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Login successful. Redirecting to the homepage.
                        Toast.makeText(SignInActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();

                        // If you do not check “Remember Me,” you will not remain logged in.
                        if (!remember) {
                            FirebaseAuth.getInstance().getAccessToken(true);
                        }

                        // Jump to the homepage
                        Intent intent = new Intent(SignInActivity.this, AddBillActivity.class);
                        startActivity(intent);
                        finish(); // 关闭当前页面，防止返回
                    } else {
                        // Login failed
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText(R.string.action_sign_in);
                        tvError.setText("Login failed: " + task.getException().getMessage());
                        tvError.setVisibility(android.view.View.VISIBLE);
                    }
                });
    }

    /** 让 “No account? Sign up” 中的 “Sign up” 单独变蓝并点击跳转到注册页 */
    private void setupNoAccountLink() {
        CharSequence origin = tvNoAccount.getText();
        String full = origin == null ? "No account? Sign up" : origin.toString();
        String keyword = "Sign up";

        int start = full.indexOf(keyword);
        if (start < 0) {
            // 如果字符串里没有“Sign up”，就整行可点击作为兜底
            tvNoAccount.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
            return;
        }
        int end = start + keyword.length();

        SpannableString ss = new SpannableString(full);

        int linkColor;
        try {
            linkColor = ContextCompat.getColor(this, R.color.link_blue); // 若你在 colors.xml 里定义了 link_blue
        } catch (Exception e) {
            linkColor = Color.parseColor("#1E88E5"); // 兜底颜色
        }
        ss.setSpan(new ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(android.view.View widget) {
                startActivity(new Intent(SignInActivity.this, SignUpActivity.class));
                finish(); // 可选：避免返回键回到登录页
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvNoAccount.setText(ss);
        tvNoAccount.setMovementMethod(LinkMovementMethod.getInstance());
        tvNoAccount.setHighlightColor(Color.TRANSPARENT);
    }

    private boolean validateForm() {
        boolean ok = true;

        // 清旧错误
        tilEmail.setError(null);
        tilPassword.setError(null);
        tvError.setVisibility(android.view.View.GONE);

        String email = getText(etEmail);
        String pwd = getText(etPassword);

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email format");
            ok = false;
        }

        if (pwd.isEmpty()) {
            tilPassword.setError("Password is required");
            ok = false;
        } else if (pwd.length() < 8) {
            tilPassword.setError("At least 8 characters");
            ok = false;
        }

        if (!ok) {
            tvError.setText("Please fix the errors above");
            tvError.setVisibility(android.view.View.VISIBLE);
        }
        return ok;
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
