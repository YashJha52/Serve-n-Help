package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Account screen: identity, past donations (donors) or past deliveries (NGOs),
 * editable account details, appearance, and sign out at the end.
 */
public class ProfileActivity extends BaseActivity {

    private ListenerRegistration historyListener;
    private boolean isNgo;
    private DocumentSnapshot profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        if (FirebaseRepo.uid() == null) {
            finish();
            return;
        }

        findViewById(R.id.btn_back_profile).setOnClickListener(v -> finish());
        findViewById(R.id.btn_profile_signout).setOnClickListener(v -> RestaurantDashboardActivity.confirmSignOut(this));
        findViewById(R.id.btn_history_all).setOnClickListener(v -> startActivity(
                new Intent(this, FoodAvailableActivity.class).putExtra(FoodAvailableActivity.EXTRA_HISTORY, true)));
        bindAppearance();

        FirebaseRepo.users().document(FirebaseRepo.uid()).get()
                .addOnSuccessListener(doc -> {
                    profile = doc;
                    isNgo = FirebaseRepo.ROLE_NGO.equals(doc.getString("role"));
                    bindIdentity();
                    bindDetails();
                    listenForHistory();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Could not load your account: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ----- Identity -----

    private void bindIdentity() {
        String name = valueOr("name", "Your organisation");
        ((TextView) findViewById(R.id.tv_profile_avatar)).setText(Ui.initials(name));
        ((TextView) findViewById(R.id.tv_profile_name)).setText(name);
        ((TextView) findViewById(R.id.tv_profile_role)).setText(isNgo ? "NGO" : "Food donor");
        ((TextView) findViewById(R.id.tv_profile_email)).setText(valueOr("email", ""));
        ((TextView) findViewById(R.id.tv_history_title)).setText(isNgo ? "Past deliveries" : "Past donations");

        Timestamp since = profile.getTimestamp("createdAt");
        ((TextView) findViewById(R.id.tv_profile_since)).setText(since == null ? ""
                : "Member since " + new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(since.toDate()));
    }

    // ----- History + impact -----

    private void listenForHistory() {
        Query query = FirebaseRepo.donations().whereEqualTo(isNgo ? "ngoId" : "donorId", FirebaseRepo.uid());
        historyListener = query.addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            List<Donation> all = snap.toObjects(Donation.class);
            all.sort((a, b) -> {
                Timestamp ta = a.deliveredAt != null ? a.deliveredAt : a.createdAt;
                Timestamp tb = b.deliveredAt != null ? b.deliveredAt : b.createdAt;
                return ta == null || tb == null ? 0 : tb.compareTo(ta);
            });

            int delivered = 0;
            int meals = 0;
            double kg = 0;
            List<Donation> past = new ArrayList<>();
            for (Donation d : all) {
                boolean done = Donation.STATUS_DELIVERED.equals(d.status);
                if (done) {
                    delivered++;
                    meals += d.mealsEstimate();
                    kg += d.weightEstimateKg();
                }
                // Donors also see listings that expired unclaimed; NGOs only completed deliveries
                if (done || (!isNgo && d.isExpired())) past.add(d);
            }

            setStat(R.id.tv_profile_stat1_value, R.id.tv_profile_stat1_label,
                    String.valueOf(all.size()), isNgo ? (all.size() == 1 ? "pickup accepted" : "pickups accepted") : (all.size() == 1 ? "donation posted" : "donations posted"));
            setStat(R.id.tv_profile_stat2_value, R.id.tv_profile_stat2_label,
                    String.valueOf(delivered), "delivered");
            if (isNgo) {
                setStat(R.id.tv_profile_stat3_value, R.id.tv_profile_stat3_label, String.valueOf(meals), "people fed");
            } else {
                setStat(R.id.tv_profile_stat3_value, R.id.tv_profile_stat3_label,
                        String.format(Locale.getDefault(), "%.0f", kg), "kg rescued");
            }
            renderHistory(past);
        });
    }

    private void setStat(int valueId, int labelId, String value, String label) {
        ((TextView) findViewById(valueId)).setText(value);
        ((TextView) findViewById(labelId)).setText(label);
    }

    private void renderHistory(List<Donation> past) {
        LinearLayout rows = findViewById(R.id.layout_history_rows);
        rows.removeAllViews();
        findViewById(R.id.btn_history_all).setVisibility(past.size() > 5 ? View.VISIBLE : View.GONE);

        if (past.isEmpty()) {
            TextView empty = Ui.emptyState(this);
            empty.setText(isNgo
                    ? "Food you collect and deliver will be listed here."
                    : "Once an NGO delivers your food, it's listed here with the date and who collected it.");
            rows.addView(empty);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        for (int i = 0; i < Math.min(5, past.size()); i++) {
            Donation d = past.get(i);
            if (i > 0) rows.addView(divider());
            View row = LayoutInflater.from(this).inflate(R.layout.item_history_row, rows, false);
            Timestamp when = d.deliveredAt != null ? d.deliveredAt : d.createdAt;
            String partner = isNgo ? d.donorName : d.ngoName;
            String sub = (when == null ? "" : fmt.format(when.toDate()) + " · ") + d.quantity + " " + d.unit
                    + (TextUtils.isEmpty(partner) ? "" : (isNgo ? " · from " : " · to ") + partner);
            ((TextView) row.findViewById(R.id.tv_hist_title)).setText(d.foodName);
            ((TextView) row.findViewById(R.id.tv_hist_sub)).setText(sub);
            Ui.styleStatus(row.findViewById(R.id.tv_hist_status), d);
            row.setOnClickListener(v -> DonationDetailsActivity.open(this, d.id));
            rows.addView(row);
        }
    }

    // ----- Account details -----

    private void bindDetails() {
        LinearLayout rows = findViewById(R.id.layout_detail_rows);
        rows.removeAllViews();
        addDetail(rows, R.drawable.ic_storefront, "Organisation", "name", InputType.TYPE_TEXT_FLAG_CAP_WORDS, 0);
        addDetail(rows, R.drawable.ic_person, "Contact person", "representative", InputType.TYPE_TEXT_FLAG_CAP_WORDS, 0);
        addDetail(rows, R.drawable.ic_call, "Mobile number", "phone", InputType.TYPE_CLASS_PHONE, 10);
        addDetail(rows, R.drawable.ic_mail, "Email", "email", 0, 0);
        addDetail(rows, R.drawable.ic_location_city, "City", "city", InputType.TYPE_TEXT_FLAG_CAP_WORDS, 0);
        addDetail(rows, R.drawable.ic_pin_drop, "Pincode", "pincode", InputType.TYPE_CLASS_NUMBER, 6);
        addDetail(rows, R.drawable.ic_verified_user, isNgo ? "NGO Darpan ID" : "FSSAI licence number",
                "licenseId", InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, 0);
    }

    private void addDetail(LinearLayout rows, int icon, String label, String field, int inputType, int exactLength) {
        if (rows.getChildCount() > 0) rows.addView(divider());
        View row = LayoutInflater.from(this).inflate(R.layout.item_profile_row, rows, false);
        ((ImageView) row.findViewById(R.id.iv_row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.tv_row_label)).setText(label);
        TextView value = row.findViewById(R.id.tv_row_value);
        String current = valueOr(field, "");
        value.setText(current.isEmpty() ? "Not added" : current);
        value.setTextColor(getColor(current.isEmpty() ? R.color.ink_muted : R.color.ink));

        // Email is the sign-in identity, so it is shown but not editable here
        boolean editable = !"email".equals(field);
        row.findViewById(R.id.iv_row_edit).setVisibility(editable ? View.VISIBLE : View.INVISIBLE);
        if (editable) {
            row.setOnClickListener(v -> editField(label, field, inputType, exactLength, value));
        } else {
            row.setClickable(false);
            row.setBackground(null);
        }
        rows.addView(row);
    }

    private void editField(String label, String field, int inputType, int exactLength, TextView valueView) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_field, null);
        TextInputLayout til = content.findViewById(R.id.til_edit);
        TextInputEditText input = content.findViewById(R.id.et_edit);
        input.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : (InputType.TYPE_CLASS_TEXT | inputType));
        if (inputType == InputType.TYPE_CLASS_PHONE || inputType == InputType.TYPE_CLASS_NUMBER) input.setInputType(inputType);
        if (exactLength > 0) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(exactLength)});
        if ("phone".equals(field)) til.setPrefixText("+91 ");
        input.setText(valueOr(field, ""));
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setView(content)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();

        // Validate before closing so a mistake can be fixed in place
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (("name".equals(field)) && text.isEmpty()) {
                til.setError("Your organisation needs a name");
                return;
            }
            if (exactLength > 0 && !text.isEmpty() && text.length() != exactLength) {
                til.setError("Enter all " + exactLength + " digits");
                return;
            }
            FirebaseRepo.users().document(FirebaseRepo.uid()).update(field, text)
                    .addOnSuccessListener(x -> {
                        Toast.makeText(this, label + " saved", Toast.LENGTH_SHORT).show();
                        FirebaseRepo.users().document(FirebaseRepo.uid()).get().addOnSuccessListener(doc -> {
                            profile = doc;
                            bindIdentity();
                            bindDetails();
                        });
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Couldn't save: " + e.getMessage(), Toast.LENGTH_LONG).show());
            dialog.dismiss();
        });
    }

    // ----- Appearance -----

    private void bindAppearance() {
        MaterialButton system = findViewById(R.id.btn_theme_system);
        MaterialButton light = findViewById(R.id.btn_theme_light);
        MaterialButton dark = findViewById(R.id.btn_theme_dark);
        int mode = ThemePrefs.get(this);
        Ui.styleSegment(system, mode == ThemePrefs.SYSTEM);
        Ui.styleSegment(light, mode == ThemePrefs.LIGHT);
        Ui.styleSegment(dark, mode == ThemePrefs.DARK);
        ((TextView) findViewById(R.id.tv_theme_hint)).setText(
                mode == ThemePrefs.LIGHT ? "Always light, whatever your phone is set to."
                        : mode == ThemePrefs.DARK ? "Always dark. Easier on the eyes at night."
                        : "Auto follows your phone's light or dark setting.");

        // Changing the mode recreates the screen with the new palette
        system.setOnClickListener(v -> ThemePrefs.set(this, ThemePrefs.SYSTEM));
        light.setOnClickListener(v -> ThemePrefs.set(this, ThemePrefs.LIGHT));
        dark.setOnClickListener(v -> ThemePrefs.set(this, ThemePrefs.DARK));
    }

    // ----- Helpers -----

    private View divider() {
        View line = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                (int) getResources().getDisplayMetrics().density);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        lp.setMarginStart(margin);
        lp.setMarginEnd(margin);
        line.setLayoutParams(lp);
        line.setBackgroundColor(getColor(R.color.line));
        return line;
    }

    private String valueOr(String field, String fallback) {
        String v = profile == null ? null : profile.getString(field);
        return TextUtils.isEmpty(v) ? fallback : v;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (historyListener != null) historyListener.remove();
    }
}
