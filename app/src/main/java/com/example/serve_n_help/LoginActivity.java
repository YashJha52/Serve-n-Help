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
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends BaseActivity {

    private static final String PREFS = "serve_n_help";
    private static final String KEY_REMEMBER = "remember_me";

    private String selectedRole = FirebaseRepo.ROLE_DONOR;

    private MaterialButton btnRoleDonor;
    private MaterialButton btnRoleNgo;
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
        ivRoleHint = findViewById(R.id.iv_role_hint_icon);
        tvRoleHint = findViewById(R.id.tv_role_hint_text);

        cbRemember.setChecked(prefs.getBoolean(KEY_REMEMBER, true));

        // Donor / NGO segmented toggle
        btnRoleDonor.setOnClickListener(v -> selectRole(FirebaseRepo.ROLE_DONOR));
        btnRoleNgo.setOnClickListener(v -> selectRole(FirebaseRepo.ROLE_NGO));
        selectRole(FirebaseRepo.ROLE_DONOR);


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
        findViewById(R.id.btn_sso_google).setOnClickListener(v -> signInWithGoogle());
        findViewById(R.id.btn_sso_ngo).setOnClickListener(comingSoon);

        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, CreateAccountActivity.class)));
    }

    private void selectRole(String role) {
        selectedRole = role;
        boolean donor = FirebaseRepo.ROLE_DONOR.equals(role);
        Ui.styleSegment(btnRoleDonor, donor);
        Ui.styleSegment(btnRoleNgo, !donor);
        ivRoleHint.setImageResource(donor ? R.drawable.ic_restaurant : R.drawable.ic_volunteer_activism);
        tvRoleHint.setText(donor
                ? "Post surplus food and track who collects it."
                : "Find food near you and accept pickups.");
        btnLogin.setText(loginLabel());
    }

    private String loginLabel() {
        return FirebaseRepo.ROLE_NGO.equals(selectedRole) ? "Sign in" : getString(R.string.sign_in_as_restaurant);
    }


    /**
     * Google sign-in through Credential Manager, then Firebase. The web client ID is generated
     * from google-services.json once the Google provider is enabled in the Firebase console.
     */
    private void signInWithGoogle() {
        int clientIdRes = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (clientIdRes == 0) {
            Toast.makeText(this, "Google sign-in is not enabled for this Firebase project yet. "
                    + "Enable it in the Firebase console and download google-services.json again.", Toast.LENGTH_LONG).show();
            return;
        }
        GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(clientIdRes))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        setLoading(true);
        CredentialManager.create(this).getCredentialAsync(this, request, null,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                            firebaseSignInWithGoogle(google.getIdToken());
                        } else {
                            setLoading(false);
                            Toast.makeText(LoginActivity.this, "Unexpected sign-in response", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        setLoading(false);
                        if (e instanceof GetCredentialCancellationException) return;
                        String msg = e instanceof NoCredentialException
                                ? "No Google account found on this device. Add one in Settings → Accounts."
                                : "Google sign-in failed: " + e.getMessage();
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void firebaseSignInWithGoogle(String idToken) {
        FirebaseRepo.auth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    FirebaseRepo.users().document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    checkRoleAndRoute(user.getUid());
                                    return;
                                }
                                // First Google sign-in: create the profile with the role picked on the toggle
                                Map<String, Object> profile = new HashMap<>();
                                profile.put("userId", user.getUid());
                                profile.put("name", user.getDisplayName() == null ? "" : user.getDisplayName());
                                profile.put("representative", user.getDisplayName() == null ? "" : user.getDisplayName());
                                profile.put("email", user.getEmail());
                                profile.put("phone", user.getPhoneNumber() == null ? "" : user.getPhoneNumber());
                                profile.put("role", selectedRole);
                                profile.put("signInMethod", "google");
                                profile.put("createdAt", FieldValue.serverTimestamp());
                                FirebaseRepo.users().document(user.getUid()).set(profile)
                                        .addOnSuccessListener(x -> openHome(this, selectedRole))
                                        .addOnFailureListener(e -> {
                                            setLoading(false);
                                            Toast.makeText(this, "Saving profile failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Could not load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, friendlyAuthError(e), Toast.LENGTH_LONG).show();
                });
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
