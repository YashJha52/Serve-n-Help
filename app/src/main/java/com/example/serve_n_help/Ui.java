package com.example.serve_n_help;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Small formatting and view helpers shared by the screens. */
final class Ui {

    private Ui() {
    }

    static String time(Timestamp ts) {
        return ts == null ? "" : new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(ts.toDate());
    }

    static String dateTime(Timestamp ts) {
        return ts == null ? "" : new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()).format(ts.toDate());
    }

    /** "Expires in 2h 15m", "Expired", or the stored text when there is no timestamp. */
    static String expiryText(Donation d) {
        if (Donation.STATUS_DELIVERED.equals(d.status)) return "Completed";
        if (d.expiresAt == null) {
            return TextUtils.isEmpty(d.expiryTime) ? "No expiry set" : "Exp. " + d.expiryTime;
        }
        if (!Donation.STATUS_AVAILABLE.equals(d.status)) return "Exp. " + time(d.expiresAt);
        long mins = (d.expiresAt.toDate().getTime() - System.currentTimeMillis()) / 60000;
        if (mins <= 0) return "Expired";
        if (mins < 60) return "Expires in " + mins + "m";
        long hours = mins / 60;
        if (hours < 24) return "Expires in " + hours + "h " + (mins % 60) + "m";
        return "Expires in " + (hours / 24) + "d";
    }

    /** Selected / unselected look for the pill-shaped filter chips. */
    static void styleChip(TextView chip, boolean selected) {
        Context c = chip.getContext();
        chip.setBackgroundResource(selected ? R.drawable.bg_pill_primary : R.drawable.bg_pill_neutral);
        int color = ContextCompat.getColor(c, selected ? R.color.on_primary_fixed : R.color.on_surface_variant);
        chip.setTextColor(color);
        Drawable check = null;
        if (selected) {
            check = ContextCompat.getDrawable(c, R.drawable.ic_check).mutate();
            int size = (int) (14 * c.getResources().getDisplayMetrics().density);
            check.setBounds(0, 0, size, size);
            check.setTint(color);
        }
        chip.setCompoundDrawablesRelative(check, null, null, null);
        chip.setCompoundDrawablePadding((int) (4 * c.getResources().getDisplayMetrics().density));
    }

    static void dial(Context c, String phone) {
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(c, "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    static void sms(Context c, String phone) {
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(c, "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        c.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone)));
    }

    static String initials(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1) + (parts.length > 1 ? parts[1].substring(0, 1) : "");
        return s.toUpperCase(Locale.getDefault());
    }
}
