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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;


// Author: Jinglin Lei
// SignUp Function
//Date: 2025-08-24

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etName, etEmail, etPassword, etConfirm;
    private CheckBox cbAgree;
    private TextView tvError;
    private TextView tvHaveAccount; // 新增：底部“Already have an account? Sign in”
    private MaterialButton btnSignUp;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

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


        tvHaveAccount = findViewById(R.id.tvHaveAccount);
        setupHaveAccountLink();

        // Button click
        btnSignUp.setOnClickListener(v -> {
            if (validateForm()) {
                registerUser();

//                Toast.makeText(this, "Sign up success!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerUser() {
        String name = getText(etName);
        String email = getText(etEmail);
        String password = getText(etPassword);

        // Display loading status
        btnSignUp.setEnabled(false);
        btnSignUp.setText("Creating account...");

        // Create users using Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Registration successful. Obtain the new user's UID.
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            // Store user data in Firestore
                            saveUserDataToFirestore(uid, name, email, password);
                            Toast.makeText(this, "Sign up success!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Registration failed
                        btnSignUp.setEnabled(true);
                        btnSignUp.setText(R.string.action_sign_up);
                        tvError.setText("Registration failed: " + task.getException().getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
    }

    /**
     * Save user data to Firestore
     */
    private void saveUserDataToFirestore(String uid, String name, String email, String password) {
        // 创建用户数据对象
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email);
        userData.put("password", password);
        userData.put("userId", name);

        // Save the data to the “users” collection in Firestore.
        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // Data saved successfully. Redirecting to the login page.
                    Toast.makeText(SignUpActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    // 数据保存失败
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText(R.string.action_sign_up);
                    tvError.setText("Error saving user data: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);

                    // 删除已创建的Auth用户，因为Firestore保存失败
                    if (mAuth.getCurrentUser() != null) {
                        mAuth.getCurrentUser().delete();
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
