package com.example.serve_n_help;

import android.app.Application;

/** Applies the saved Appearance choice before any screen is drawn. */
public class ServeNHelpApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.apply(this);
    }
}
