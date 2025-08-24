package unimelb.comp90018.equaltrip;


import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etName, etEmail, etPassword, etConfirm;
    private CheckBox cbAgree;
    private TextView tvError;
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

        // 按钮点击
        btnSignUp.setOnClickListener(v -> {
            if (validateForm()) {
                // TODO: 这里写成功逻辑，例如调用 API
                Toast.makeText(this, "Sign up success!", Toast.LENGTH_SHORT).show();
            }
        });
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
