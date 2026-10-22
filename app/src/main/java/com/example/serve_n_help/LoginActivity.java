package com.example.serve_n_help;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends BaseActivity {

    private static final String PREFS = "serve_n_help";
    private static final String KEY_REMEMBER = "remember_me";

    private String selectedRole = FirebaseRepo.ROLE_DONOR;

    private MaterialButton btnRoleDonor;
    private MaterialButton btnRoleNgo;
    private TextView tvRoleBadge;
    private ImageView ivRoleHint;
    private TextView tvRoleHint;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Already signed in -> skip the login screen, unless "Stay signed in" was unticked
        if (FirebaseRepo.auth().getCurrentUser() != null) {
            if (prefs.getBoolean(KEY_REMEMBER, true)) {
                routeByRole(this, FirebaseRepo.uid());
            } else {
                FirebaseRepo.auth().signOut();
            }
        }

        // Find views
        TextInputLayout tilEmail = findViewById(R.id.til_email);
        TextInputLayout tilPassword = findViewById(R.id.til_password);
        TextInputEditText etEmail = findViewById(R.id.et_email);
        TextInputEditText etPassword = findViewById(R.id.et_password);
        MaterialCheckBox cbRemember = findViewById(R.id.cb_remember_me);
        btnLogin = findViewById(R.id.btn_login_primary);
        TextView tvRegister = findViewById(R.id.tv_register_partner);
        TextView tvForgot = findViewById(R.id.tv_forgot_password);
        btnRoleDonor = findViewById(R.id.btn_role_donor);
        btnRoleNgo = findViewById(R.id.btn_role_ngo);
        tvRoleBadge = findViewById(R.id.tv_role_badge);
        ivRoleHint = findViewById(R.id.iv_role_hint_icon);
        tvRoleHint = findViewById(R.id.tv_role_hint_text);

        cbRemember.setChecked(prefs.getBoolean(KEY_REMEMBER, true));

        // Donor / NGO segmented toggle
        btnRoleDonor.setOnClickListener(v -> selectRole(FirebaseRepo.ROLE_DONOR));
        btnRoleNgo.setOnClickListener(v -> selectRole(FirebaseRepo.ROLE_NGO));
        selectRole(FirebaseRepo.ROLE_DONOR);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Firebase sign-in, then route to the dashboard matching the user's role
        btnLogin.setOnClickListener(v -> {
            if (!validateInputs(tilEmail, etEmail, tilPassword, etPassword)) return;
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            prefs.edit().putBoolean(KEY_REMEMBER, cbRemember.isChecked()).apply();
            setLoading(true);
            FirebaseRepo.auth().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> checkRoleAndRoute(result.getUser().getUid()))
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, friendlyAuthError(e), Toast.LENGTH_LONG).show();
                    });
        });

        tvForgot.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter your email to reset the password");
                return;
            }
            FirebaseRepo.auth().sendPasswordResetEmail(email)
                    .addOnSuccessListener(x -> Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        });

        // Sign-in methods that are not part of this version
        View.OnClickListener comingSoon = v ->
                Toast.makeText(this, "Coming soon. Please sign in with email and password.", Toast.LENGTH_SHORT).show();
        findViewById(R.id.btn_login_biometric).setOnClickListener(comingSoon);
        findViewById(R.id.btn_sso_google).setOnClickListener(comingSoon);
        findViewById(R.id.btn_sso_ngo).setOnClickListener(comingSoon);
        findViewById(R.id.tv_emergency_hotline).setOnClickListener(v -> Ui.dial(this, "1800"));

        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, CreateAccountActivity.class)));
    }

    private void selectRole(String role) {
        selectedRole = role;
        boolean donor = FirebaseRepo.ROLE_DONOR.equals(role);
        styleRoleButton(btnRoleDonor, donor);
        styleRoleButton(btnRoleNgo, !donor);
        tvRoleBadge.setText(donor ? R.string.donor_mode : R.string.ngo_mode);
        ivRoleHint.setImageResource(donor ? R.drawable.ic_restaurant : R.drawable.ic_volunteer_activism);
        tvRoleHint.setText(donor
                ? "Logging in as Restaurant Donor: publish surplus batches, schedule fast kitchen pickups."
                : "Logging in as NGO / Shelter: find nearby food on the map and accept pickups.");
        btnLogin.setText(loginLabel());
    }

    private String loginLabel() {
        return FirebaseRepo.ROLE_NGO.equals(selectedRole) ? "Sign in as NGO" : getString(R.string.sign_in_as_restaurant);
    }

    private void styleRoleButton(MaterialButton button, boolean selected) {
        int bg = ContextCompat.getColor(this, selected ? R.color.primary : R.color.surface_container_low);
        int fg = ContextCompat.getColor(this, selected ? R.color.on_primary : R.color.on_surface_variant);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setIconTint(ColorStateList.valueOf(fg));
    }

    /** Signs the user back out if they picked the wrong account type on the toggle. */
    private void checkRoleAndRoute(String uid) {
        FirebaseRepo.users().document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null) role = FirebaseRepo.ROLE_DONOR;
                    if (!role.equals(selectedRole)) {
                        FirebaseRepo.auth().signOut();
                        setLoading(false);
                        Toast.makeText(this, FirebaseRepo.ROLE_NGO.equals(role)
                                ? "This is an NGO account. Select \"NGO / Bank\" above to sign in."
                                : "This is a Donor account. Select \"Donor / Kitchen\" above to sign in.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    openHome(this, role);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Could not load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Signing in…" : loginLabel());
    }

    static String friendlyAuthError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("network")) return "No internet connection. Please try again.";
        if (msg.contains("credential") || msg.contains("password") || msg.contains("no user record"))
            return "Incorrect email or password.";
        if (msg.contains("already in use")) return "An account with this email already exists. Try signing in.";
        if (msg.contains("blocked") || msg.contains("too many")) return "Too many attempts. Please wait a moment.";
        return msg;
    }

    /** Donors land on the restaurant dashboard, NGOs on the available-food feed. */
    static void routeByRole(AppCompatActivity activity, String uid) {
        FirebaseRepo.users().document(uid).get()
                .addOnSuccessListener(doc -> openHome(activity, doc.getString("role")))
                .addOnFailureListener(e -> Toast.makeText(activity, "Could not load profile: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    static void openHome(AppCompatActivity activity, String role) {
        Class<?> target = FirebaseRepo.ROLE_NGO.equals(role)
                ? FoodAvailableActivity.class
                : RestaurantDashboardActivity.class;
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    private boolean validateInputs(TextInputLayout tilEmail, TextInputEditText etEmail,
                                   TextInputLayout tilPassword, TextInputEditText etPassword) {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        boolean isValid = true;

        // Reset errors
        tilEmail.setError(null);
        tilPassword.setError(null);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            isValid = false;
        }

        return isValid;
    }
}
