package unimelb.comp90018.equaltrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
public class ForgotPasswordActivity extends AppCompatActivity{
    private ImageButton btnBack;
    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private MaterialButton btnSendReset;
    private TextView tvError, tvSuccess;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        // Bind views
        btnBack = findViewById(R.id.btnBack);
        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etEmail);
        btnSendReset = findViewById(R.id.btnSendReset);
        tvError = findViewById(R.id.tvError);
        tvSuccess = findViewById(R.id.tvSuccess);

        // Back button click
        btnBack.setOnClickListener(v -> {
            finish(); // Return to previous activity
        });

        // Send reset link button click
        btnSendReset.setOnClickListener(v -> {
            if (validateEmail()) {
                sendPasswordResetEmail();
            }
        });
    }

    private void sendPasswordResetEmail() {
        String email = getText(etEmail);

        // Disable button and show loading state
        btnSendReset.setEnabled(false);
        btnSendReset.setText("Sending...");
        tvError.setVisibility(View.GONE);
        tvSuccess.setVisibility(View.GONE);

        // Use Firebase Auth to send password reset email
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Send reset link");

                    if (task.isSuccessful()) {
                        // Email sent successfully
                        tvSuccess.setText("Password reset link has been sent to your email address. Please check your inbox.");
                        tvSuccess.setVisibility(View.VISIBLE);

                        // Optional: Return to sign in after delay
                        btnSendReset.postDelayed(() -> {
                            Intent intent = new Intent(ForgotPasswordActivity.this, SignInActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            finish();
                        }, 3000);

                    } else {
                        // Handle errors
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            tvError.setText("No account found with this email address");
                        } else {
                            tvError.setText("Failed to send reset email. Please try again.");
                        }
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
    }

    private boolean validateEmail() {
        boolean valid = true;

        // Clear old errors
        tilEmail.setError(null);
        tvError.setVisibility(View.GONE);
        tvSuccess.setVisibility(View.GONE);

        String email = getText(etEmail);

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            valid = false;
        }

        return valid;
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
