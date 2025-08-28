package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etName, etEmail, etPassword, etConfirm;
    private CheckBox cbAgree;
    private TextView tvError;
    private TextView tvHaveAccount; // 新增：底部“Already have an account? Sign in”
    private MaterialButton btnSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // 绑定控件
        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);

        cbAgree = findViewById(R.id.cbAgree);
        tvError = findViewById(R.id.tvError);
        btnSignUp = findViewById(R.id.btnSignUp);

        // 绑定“已有账户？Sign in”
        tvHaveAccount = findViewById(R.id.tvHaveAccount);
        setupHaveAccountLink(); // 只让“Sign in”变蓝并可点击

        // 按钮点击
        btnSignUp.setOnClickListener(v -> {
            if (validateForm()) {
                // TODO: 这里写成功逻辑，例如调用 API / Firebase
                Toast.makeText(this, "Sign up success!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 让“Already have an account? Sign in”中的“Sign in”单独高亮并可点击跳转
     */
    private void setupHaveAccountLink() {
        // 从字符串资源拿整句，例如 "Already have an account? Sign in"
        CharSequence origin = tvHaveAccount.getText();
        String full = origin == null ? "Already have an account? Sign in" : origin.toString();
        String keyword = "Sign in";

        int start = full.indexOf(keyword);
        if (start < 0) {
            // 容错：如果找不到“Sign in”，则整行可点
            tvHaveAccount.setOnClickListener(v -> {
                startActivity(new Intent(this, SignInActivity.class));
                finish(); // 可按需移除
            });
            return;
        }
        int end = start + keyword.length();

        SpannableString ss = new SpannableString(full);

        // 方式一：用自定义蓝色（colors.xml 定义 link_blue）
        int linkColor;
        try {
            linkColor = ContextCompat.getColor(this, R.color.link_blue);
        } catch (Exception e) {
            // 兜底：Material 推荐蓝 #1E88E5
            linkColor = Color.parseColor("#1E88E5");
        }
        ss.setSpan(new ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
                finish(); // 如果希望返回键不再回到注册页，保留这一行
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvHaveAccount.setText(ss);
        tvHaveAccount.setMovementMethod(LinkMovementMethod.getInstance());
        tvHaveAccount.setHighlightColor(Color.TRANSPARENT); // 点击时不出现高亮背景
    }

    private boolean validateForm() {
        boolean ok = true;

        // 清掉旧错误
        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);
        tvError.setVisibility(View.GONE);

        // Name
        String name = getText(etName);
        if (name.isEmpty()) {
            tilName.setError("Name is required");
            ok = false;
        }

        // Email
        String email = getText(etEmail);
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email format");
            ok = false;
        }

        // Password
        String pwd = getText(etPassword);
        if (pwd.isEmpty()) {
            tilPassword.setError("Password is required");
            ok = false;
        } else if (pwd.length() < 8) {
            tilPassword.setError("At least 8 characters");
            ok = false;
        }

        // Confirm Password
        String confirm = getText(etConfirm);
        if (!confirm.equals(pwd)) {
            tilConfirm.setError("Passwords do not match");
            ok = false;
        }

        // Terms
        if (!cbAgree.isChecked()) {
            tvError.setText("You must agree to the terms");
            tvError.setVisibility(View.VISIBLE);
            ok = false;
        }

        return ok;
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
