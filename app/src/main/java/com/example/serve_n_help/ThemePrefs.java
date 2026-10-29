package com.example.serve_n_help;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/** Stores the Appearance setting: follow the phone, always light, or always dark. */
final class ThemePrefs {

    static final int SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    static final int LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    static final int DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private static final String PREFS = "serve_n_help";
    private static final String KEY_THEME = "theme_mode";

    private ThemePrefs() {
    }

    static int get(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, SYSTEM);
    }

    static void set(Context c, int mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_THEME, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    static void apply(Context c) {
        AppCompatDelegate.setDefaultNightMode(get(c));
    }
}
