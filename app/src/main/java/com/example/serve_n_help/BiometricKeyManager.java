package com.example.serve_n_help;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BiometricKeyManager {

    private static final String PREFS_NAME = "snh_biometric_credentials";
    private static final String KEY_SET_EMAILS = "registered_emails";

    public static class AccountInfo {
        public String email;
        public String role;
        public String name;

        public AccountInfo(String email, String role, String name) {
            this.email = email;
            this.role = role;
            this.name = name;
        }

        public String getDisplayName() {
            if (!TextUtils.isEmpty(name)) {
                return name + " (" + email + ")";
            }
            return email;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void saveAccountCredentials(Context context, String email, String password, String role, String name) {
        if (context == null || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) return;

        String cleanEmail = email.trim().toLowerCase();
        SharedPreferences prefs = getPrefs(context);

        prefs.edit()
                .putString("pass_" + cleanEmail, password)
                .putString("role_" + cleanEmail, role != null ? role : FirebaseRepo.ROLE_DONOR)
                .putString("name_" + cleanEmail, name != null ? name : "")
                .apply();

        Set<String> emails = new HashSet<>(prefs.getStringSet(KEY_SET_EMAILS, new HashSet<>()));
        emails.add(cleanEmail);
        prefs.edit().putStringSet(KEY_SET_EMAILS, emails).apply();
    }

    public static synchronized String getSavedPassword(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) return null;
        return getPrefs(context).getString("pass_" + email.trim().toLowerCase(), null);
    }

    public static synchronized String getSavedRole(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) return FirebaseRepo.ROLE_DONOR;
        return getPrefs(context).getString("role_" + email.trim().toLowerCase(), FirebaseRepo.ROLE_DONOR);
    }

    public static synchronized List<AccountInfo> getRegisteredAccounts(Context context) {
        List<AccountInfo> list = new ArrayList<>();
        if (context == null) return list;

        SharedPreferences prefs = getPrefs(context);
        Set<String> emails = prefs.getStringSet(KEY_SET_EMAILS, new HashSet<>());

        for (String email : emails) {
            String role = prefs.getString("role_" + email, FirebaseRepo.ROLE_DONOR);
            String name = prefs.getString("name_" + email, "");
            list.add(new AccountInfo(email, role, name));
        }
        return list;
    }

    public static synchronized AccountInfo getAccountInfo(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) return null;
        String cleanEmail = email.trim().toLowerCase();
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.contains("pass_" + cleanEmail)) return null;

        String role = prefs.getString("role_" + cleanEmail, FirebaseRepo.ROLE_DONOR);
        String name = prefs.getString("name_" + cleanEmail, "");
        return new AccountInfo(cleanEmail, role, name);
    }

    public static synchronized void removeAccount(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) return;
        String cleanEmail = email.trim().toLowerCase();
        SharedPreferences prefs = getPrefs(context);

        prefs.edit()
                .remove("pass_" + cleanEmail)
                .remove("role_" + cleanEmail)
                .remove("name_" + cleanEmail)
                .apply();

        Set<String> emails = new HashSet<>(prefs.getStringSet(KEY_SET_EMAILS, new HashSet<>()));
        emails.remove(cleanEmail);
        prefs.edit().putStringSet(KEY_SET_EMAILS, emails).apply();
    }
}
