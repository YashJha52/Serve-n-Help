package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Objects;

public class CreateAccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        // Find views
        ImageButton btnBack = findViewById(R.id.btn_back_create_account);
        TextView tvSignIn = findViewById(R.id.tv_navigate_signin);
        MaterialButton btnSubmit = findViewById(R.id.btn_create_account_submit);

        // Input Fields
        TextInputEditText etOrgName = findViewById(R.id.et_org_name);
        TextInputEditText etEmail = findViewById(R.id.et_register_email);
        TextInputEditText etPhone = findViewById(R.id.et_mobile_phone);
        TextInputEditText etPassword = findViewById(R.id.et_create_password);
        TextInputEditText etConfirmPassword = findViewById(R.id.et_confirm_password);

        // Go back to login
        btnBack.setOnClickListener(v -> finish());
        tvSignIn.setOnClickListener(v -> finish());

        // Create account and go to Dashboard with Validation
        btnSubmit.setOnClickListener(v -> {
            if (validateRegistration(etOrgName, etEmail, etPhone, etPassword, etConfirmPassword)) {
                Intent intent = new Intent(CreateAccountActivity.this, RestaurantDashboardActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private boolean validateRegistration(TextInputEditText etOrg, TextInputEditText etEmail,
                                         TextInputEditText etPhone, TextInputEditText etPass,
                                         TextInputEditText etConfirm) {
        
        String orgName = Objects.requireNonNull(etOrg.getText()).toString().trim();
        String email = Objects.requireNonNull(etEmail.getText()).toString().trim();
        String phone = Objects.requireNonNull(etPhone.getText()).toString().trim();
        String pass = Objects.requireNonNull(etPass.getText()).toString().trim();
        String confirm = Objects.requireNonNull(etConfirm.getText()).toString().trim();

        if (TextUtils.isEmpty(orgName)) {
            etOrg.setError("Organization name required");
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Valid email required");
            return false;
        }

        if (phone.length() != 10) {
            etPhone.setError("Enter 10-digit phone number");
            return false;
        }

        if (pass.length() < 8) {
            etPass.setError("Password must be at least 8 characters");
            return false;
        }

        if (!pass.equals(confirm)) {
            etConfirm.setError("Passwords do not match");
            return false;
        }

        return true;
    }
}
