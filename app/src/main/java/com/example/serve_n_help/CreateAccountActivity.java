package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Objects;

public class CreateAccountActivity extends BaseActivity {

    private String selectedRole = FirebaseRepo.ROLE_DONOR;

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
        TextInputEditText etRepName = findViewById(R.id.et_rep_name);
        TextInputEditText etCity = findViewById(R.id.et_city);
        TextInputEditText etPincode = findViewById(R.id.et_pincode);
        TextInputEditText etLicense = findViewById(R.id.et_license_id);
        MaterialCheckBox cbTerms = findViewById(R.id.cb_agree_terms);

        // Role selection (Donor / NGO)
        MaterialCardView cardDonor = findViewById(R.id.card_donor_role);
        MaterialCardView cardNgo = findViewById(R.id.card_ngo_role);
        ImageView ivDonorChecked = findViewById(R.id.iv_donor_checked);
        ImageView ivNgoChecked = findViewById(R.id.iv_ngo_checked);
        TextView tvLicenseLabel = findViewById(R.id.tv_license_label);
        cardDonor.setOnClickListener(v -> {
            selectedRole = FirebaseRepo.ROLE_DONOR;
            styleRoleCard(cardDonor, ivDonorChecked, true);
            styleRoleCard(cardNgo, ivNgoChecked, false);
            tvLicenseLabel.setText(R.string.fssai_label);
            tilOf(etLicense).setHint(getString(R.string.fssai_hint));
            tilOf(etLicense).setHelperText(getString(R.string.fssai_helper));
            tilOf(etOrgName).setHint(getString(R.string.org_name_hint));
            btnSubmit.setText(R.string.create_restaurant_account);
        });
        cardNgo.setOnClickListener(v -> {
            selectedRole = FirebaseRepo.ROLE_NGO;
            styleRoleCard(cardNgo, ivNgoChecked, true);
            styleRoleCard(cardDonor, ivDonorChecked, false);
            tvLicenseLabel.setText("NGO Registration No. (Darpan ID)");
            tilOf(etLicense).setHint("e.g. MH/2023/0123456");
            tilOf(etLicense).setHelperText("Used to verify your organisation before pickups.");
            tilOf(etOrgName).setHint("e.g. Hope Community Food Bank");
            btnSubmit.setText("Create NGO Account");
        });

        // Live password strength bar: length, letters + digits, symbols / mixed case
        LinearLayout strength = findViewById(R.id.layout_password_strength);
        updateStrength(strength, "");
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updateStrength(strength, s.toString()); }
        });

        // Go back to login
        btnBack.setOnClickListener(v -> finish());
        tvSignIn.setOnClickListener(v -> finish());

        // Create account and go to Dashboard with Validation
        btnSubmit.setOnClickListener(v -> {
            if (!validateRegistration(etOrgName, etEmail, etPhone, etPassword, etConfirmPassword)) {
                return;
            }
            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please accept the terms to continue", Toast.LENGTH_SHORT).show();
                return;
            }
            String email = textOf(etEmail);
            String password = textOf(etPassword);
            btnSubmit.setEnabled(false);
            CharSequence submitLabel = btnSubmit.getText();
            btnSubmit.setText("Creating account…");

            FirebaseRepo.auth().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser().getUid();
                        Map<String, Object> user = new HashMap<>();
                        user.put("userId", uid);
                        user.put("name", textOf(etOrgName));
                        user.put("representative", textOf(etRepName));
                        user.put("email", email);
                        user.put("phone", textOf(etPhone));
                        user.put("city", textOf(etCity));
                        user.put("pincode", textOf(etPincode));
                        user.put("licenseId", textOf(etLicense));
                        user.put("role", selectedRole);
                        user.put("createdAt", FieldValue.serverTimestamp());

                        FirebaseRepo.users().document(uid).set(user)
                                .addOnSuccessListener(x -> LoginActivity.openHome(this, selectedRole))
                                .addOnFailureListener(e -> {
                                    btnSubmit.setEnabled(true);
                                    btnSubmit.setText(submitLabel);
                                    Toast.makeText(this, "Saving profile failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText(submitLabel);
                        Toast.makeText(this, LoginActivity.friendlyAuthError(e), Toast.LENGTH_LONG).show();
                    });
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

    private void updateStrength(LinearLayout bar, String pass) {
        int score = 0;
        if (pass.length() >= 8) score++;
        if (pass.matches(".*[A-Za-z].*") && pass.matches(".*\\d.*")) score++;
        if (pass.length() >= 12 || pass.matches(".*[^A-Za-z0-9].*")) score++;
        if (pass.isEmpty()) score = 0;
        int color = ContextCompat.getColor(this, score <= 1 ? R.color.error : score == 2 ? R.color.secondary : R.color.primary);
        int empty = ContextCompat.getColor(this, R.color.surface_container);
        for (int i = 0; i < bar.getChildCount(); i++) {
            bar.getChildAt(i).setBackgroundColor(i < Math.max(score, pass.isEmpty() ? 0 : 1) ? color : empty);
        }
    }

    private static TextInputLayout tilOf(TextInputEditText et) {
        View v = (View) et.getParent();
        while (!(v instanceof TextInputLayout)) v = (View) v.getParent();
        return (TextInputLayout) v;
    }

    private void styleRoleCard(MaterialCardView card, ImageView check, boolean selected) {
        card.setCardBackgroundColor(ContextCompat.getColor(this, selected ? R.color.primary_fixed_light : R.color.surface_container_lowest));
        card.setStrokeColor(ContextCompat.getColor(this, selected ? R.color.primary : R.color.divider));
        card.setStrokeWidth((int) (getResources().getDisplayMetrics().density * (selected ? 1.5f : 1f)));
        check.setBackgroundResource(selected ? R.drawable.bg_status_dot_active : R.drawable.bg_pill_neutral);
        check.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, selected ? R.color.on_primary : R.color.surface_dim)));
        check.setContentDescription(selected ? "Selected" : "Not selected");
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
