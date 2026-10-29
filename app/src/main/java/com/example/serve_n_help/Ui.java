package com.example.serve_n_help;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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

    /** Selected / unselected look for filter and diet chips. */
    static void styleChip(TextView chip, boolean selected) {
        Context c = chip.getContext();
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        int color = ContextCompat.getColor(c, selected ? R.color.accent_strong : R.color.ink_muted);
        chip.setTextColor(color);
        Drawable check = null;
        if (selected) {
            check = ContextCompat.getDrawable(c, R.drawable.ic_check).mutate();
            int size = (int) (16 * c.getResources().getDisplayMetrics().density);
            check.setBounds(0, 0, size, size);
            check.setTint(color);
        }
        chip.setCompoundDrawablesRelative(check, null, null, null);
        chip.setCompoundDrawablePadding((int) (4 * c.getResources().getDisplayMetrics().density));
    }

    /**
     * Status tag colours. Text always names the status too, so colour is never the only signal.
     * Available = accent, in progress = info, expired = danger, delivered = neutral.
     */
    static void styleStatus(TextView tag, Donation d) {
        int text;
        int fill;
        if (d.isExpired()) {
            text = R.color.danger; fill = R.color.danger_tint;
        } else if (Donation.STATUS_ACCEPTED.equals(d.status) || Donation.STATUS_PICKED_UP.equals(d.status)) {
            text = R.color.info; fill = R.color.info_tint;
        } else if (Donation.STATUS_DELIVERED.equals(d.status)) {
            text = R.color.ink_muted; fill = R.color.surface_alt;
        } else {
            text = R.color.accent_strong; fill = R.color.accent_tint;
        }
        Context c = tag.getContext();
        tag.setText(d.isExpired() ? "Expired" : Donation.statusLabel(d.status));
        tag.setTextColor(ContextCompat.getColor(c, text));
        tag.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, fill)));
    }

    /** Centred, muted empty-state line used in place of a blank list. */
    static TextView emptyState(Context c) {
        TextView t = new TextView(c);
        t.setTextAppearance(R.style.TextAppearance_SNH_BodySmall);
        t.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (32 * c.getResources().getDisplayMetrics().density);
        t.setPadding(pad / 2, pad, pad / 2, pad);
        return t;
    }

    /** Segmented control: the chosen option lifts to the card colour, the rest blend into the track. */
    static void styleSegment(com.google.android.material.button.MaterialButton button, boolean selected) {
        Context c = button.getContext();
        int bg = ContextCompat.getColor(c, selected ? R.color.segment_on : R.color.surface_alt);
        int fg = ContextCompat.getColor(c, selected ? R.color.ink : R.color.ink_muted);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setIconTint(ColorStateList.valueOf(fg));
        button.setSelected(selected);
    }

    /** Colours the confirm button of a destructive dialog (delete, sign out) red. */
    static void destructive(androidx.appcompat.app.AlertDialog dialog) {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(dialog.getContext(), R.color.danger));
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
