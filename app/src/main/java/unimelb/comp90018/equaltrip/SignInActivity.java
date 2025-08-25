package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignInActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private CheckBox cbRemember;
    private TextView tvError, tvForgot, tvNoAccount;
    private MaterialButton btnSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in); // 对应你的 XML

        // 绑定控件
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbRemember = findViewById(R.id.cbRemember);
        tvError = findViewById(R.id.tvError);
        tvForgot = findViewById(R.id.tvForgot);
        tvNoAccount = findViewById(R.id.tvNoAccount);
        btnSignIn = findViewById(R.id.btnSignIn);

        // 点击“登录”
        btnSignIn.setOnClickListener(v -> {
            if (validateForm()) {
                boolean remember = cbRemember != null && cbRemember.isChecked();
                // TODO: 调用登录 API；可把 remember 传给后端或本地保存
                Toast.makeText(this, "Sign in success!", Toast.LENGTH_SHORT).show();
            }
        });

        // 键盘回车直接提交
        if (etPassword != null) {
            etPassword.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    btnSignIn.performClick();
                    return true;
                }
                return false;
            });
        }

        // “没有账号？去注册”
        if (tvNoAccount != null) {
            tvNoAccount.setOnClickListener(v ->
                    startActivity(new Intent(this, SignUpActivity.class))
            );
        }

        // “忘记密码？”
        if (tvForgot != null) {
            tvForgot.setOnClickListener(v ->
                    Toast.makeText(this, "TODO: go to reset password screen", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private boolean validateForm() {
        boolean ok = true;

        // 清旧错误
        tilEmail.setError(null);
        tilPassword.setError(null);
        tvError.setVisibility(View.GONE);

        String email = getText(etEmail);
        String pwd = getText(etPassword);

        // 账号（这里按邮箱校验；如果要手机号，一起放宽见备注）
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email format");
            ok = false;
        }

        // 密码
        if (pwd.isEmpty()) {
            tilPassword.setError("Password is required");
            ok = false;
        } else if (pwd.length() < 8) {
            tilPassword.setError("At least 8 characters");
            ok = false;
        }

        if (!ok) {
            tvError.setText("Please fix the errors above");
            tvError.setVisibility(View.VISIBLE);
        }
        return ok;
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
