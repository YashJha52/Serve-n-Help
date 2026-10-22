package com.example.serve_n_help;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DonationDetailsActivity extends BaseActivity {

    private static final String EXTRA_DONATION_ID = "donation_id";

    private static final int[] STEP_ICONS = {R.id.iv_step1, R.id.iv_step2, R.id.iv_step3, R.id.iv_step4};
    private static final int[] STEP_TITLES = {R.id.tv_step1_title, R.id.tv_step2_title, R.id.tv_step3_title, R.id.tv_step4_title};
    private static final int[] STEP_TIMES = {R.id.tv_step1_time, R.id.tv_step2_time, R.id.tv_step3_time, R.id.tv_step4_time};
    private static final int[] STEP_DESCS = {R.id.tv_step1_desc, R.id.tv_step2_desc, R.id.tv_step3_desc, R.id.tv_step4_desc};
    private static final int[] STEP_LINES = {R.id.line_step1, R.id.line_step2, R.id.line_step3};

    private ListenerRegistration listener;
    private Donation donation;
    private String role;
    private String userName = "";
    private String userPhone = "";
    private boolean deleting;

    public static void open(Context context, String donationId) {
        if (donationId == null) return;
        Intent intent = new Intent(context, DonationDetailsActivity.class);
        intent.putExtra(EXTRA_DONATION_ID, donationId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donation_details);

        String donationId = getIntent().getStringExtra(EXTRA_DONATION_ID);
        if (donationId == null || FirebaseRepo.uid() == null) {
            finish();
            return;
        }

        findViewById(R.id.btn_back_details).setOnClickListener(v -> finish());
        findViewById(R.id.btn_details_map).setOnClickListener(v -> openDirections());
        findViewById(R.id.btn_details_share).setOnClickListener(v -> share());
        findViewById(R.id.btn_details_more).setOnClickListener(this::showMoreMenu);
        // Hide action buttons until we know who is looking at this donation
        findViewById(R.id.btn_handover_food).setVisibility(View.GONE);
        findViewById(R.id.btn_edit_details).setVisibility(View.GONE);

        FirebaseRepo.users().document(FirebaseRepo.uid()).get().addOnSuccessListener(doc -> {
            role = doc.getString("role");
            if (role == null) role = FirebaseRepo.ROLE_DONOR;
            if (doc.getString("name") != null) userName = doc.getString("name");
            if (doc.getString("phone") != null) userPhone = doc.getString("phone");
            bind();
        });

        listener = FirebaseRepo.donations().document(donationId).addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || !snap.exists()) {
                if (!deleting) Toast.makeText(this, "This donation is no longer available", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            donation = snap.toObject(Donation.class);
            bind();
        });
    }

    private void bind() {
        if (donation == null || role == null) return;
        Donation d = donation;
        boolean isNgo = FirebaseRepo.ROLE_NGO.equals(role);

        text(R.id.tv_details_id, d.shortId());
        text(R.id.tv_details_status, d.isExpired() ? "Expired" : Donation.statusLabel(d.status));
        text(R.id.tv_details_title, d.foodName);
        text(R.id.tv_details_category, d.category);
        // Second chip shows the first dietary tag (the delivery method already has its own badge)
        TextView diet = findViewById(R.id.tv_details_diet);
        String firstTag = TextUtils.isEmpty(d.dietaryNotes) ? null : d.dietaryNotes.split("[,.]")[0].trim();
        diet.setVisibility(firstTag == null || firstTag.length() > 18 ? View.GONE : View.VISIBLE);
        diet.setText(firstTag);
        text(R.id.tv_details_expiry, Ui.expiryText(d));
        text(R.id.tv_details_servings, d.quantity + " " + d.unit);
        text(R.id.tv_details_weight, String.format(Locale.getDefault(), "~%.1f kg", d.weightEstimateKg()));
        text(R.id.tv_details_containers_label, "Prepared");
        text(R.id.tv_details_containers, d.prepTime == null ? "-" : d.prepTime.replace("Today, ", ""));
        text(R.id.tv_details_address_title, d.donorName);
        text(R.id.tv_details_address, d.pickupAddress);
        text(R.id.tv_details_notes, TextUtils.isEmpty(d.dietaryNotes) ? "No additional notes." : d.dietaryNotes);
        text(R.id.tv_details_method, d.deliveryMethod);
        text(R.id.tv_details_meals, "Meals Fed: " + d.mealsEstimate() + " People");
        // Roughly 2.5 kg CO2e avoided per kg of food rescued
        text(R.id.tv_details_co2, String.format(Locale.getDefault(), "CO₂ Savings: %.1f kg", d.weightEstimateKg() * 2.5));

        // Partner card: the NGO (for donors) or the donor (for NGOs)
        if (isNgo) {
            text(R.id.tv_details_ngo, d.donorName);
            text(R.id.tv_details_ngo_sub, "Food donor");
            text(R.id.tv_details_avatar, Ui.initials(d.donorName));
            text(R.id.tv_details_contact_name, "Donor contact");
            text(R.id.tv_details_contact_phone, TextUtils.isEmpty(d.donorPhone) ? "Not provided" : d.donorPhone);
        } else {
            text(R.id.tv_details_ngo, d.ngoName != null ? d.ngoName : "Awaiting NGO");
            text(R.id.tv_details_ngo_sub, d.ngoName != null ? "NGO partner" : "No NGO has accepted this yet");
            text(R.id.tv_details_avatar, d.ngoName != null ? Ui.initials(d.ngoName) : "?");
            text(R.id.tv_details_contact_name, "NGO contact");
            text(R.id.tv_details_contact_phone, TextUtils.isEmpty(d.ngoPhone) ? "Not available yet" : d.ngoPhone);
        }
        String contactPhone = isNgo ? d.donorPhone : d.ngoPhone;
        int contactVisibility = TextUtils.isEmpty(contactPhone) ? View.GONE : View.VISIBLE;
        findViewById(R.id.btn_driver_call).setVisibility(contactVisibility);
        findViewById(R.id.btn_driver_chat).setVisibility(contactVisibility);
        findViewById(R.id.btn_driver_call).setOnClickListener(v -> Ui.dial(this, contactPhone));
        findViewById(R.id.btn_driver_chat).setOnClickListener(v -> Ui.sms(this, contactPhone));

        bindTimeline(d);
        bindActions(d, isNgo);
    }

    private void bindTimeline(Donation d) {
        int current = d.stepIndex();
        text(R.id.tv_details_step, "Step " + (current + 1) + " of 4");

        Timestamp[] times = {d.createdAt, d.acceptedAt, d.pickedUpAt, d.deliveredAt};
        String[] doneDesc = {
                "Listed on the community rescue feed",
                "Claimed by " + (d.ngoName == null ? "an NGO" : d.ngoName),
                "Food collected from " + (d.donorName == null ? "the donor" : d.donorName),
                "Food delivered to people in need"};
        String[] pendingDesc = {
                "",
                d.isExpired() ? "Expired before an NGO accepted it" : "Waiting for an NGO to accept",
                "Waiting for pickup",
                "Waiting for delivery"};

        int primary = ContextCompat.getColor(this, R.color.primary);
        int onPrimary = ContextCompat.getColor(this, R.color.on_primary);
        int onSurface = ContextCompat.getColor(this, R.color.on_surface);
        int muted = ContextCompat.getColor(this, R.color.on_surface_variant);
        int outline = ContextCompat.getColor(this, R.color.outline);
        int divider = ContextCompat.getColor(this, R.color.divider);

        for (int i = 0; i < 4; i++) {
            boolean done = i <= current;
            ImageView icon = findViewById(STEP_ICONS[i]);
            icon.setBackgroundResource(done ? R.drawable.bg_status_dot_active : R.drawable.bg_pill_neutral);
            icon.setImageTintList(ColorStateList.valueOf(done ? onPrimary : outline));

            TextView title = findViewById(STEP_TITLES[i]);
            title.setTextColor(i == current ? primary : done ? onSurface : muted);
            TextView time = findViewById(STEP_TIMES[i]);
            time.setText(done ? Ui.time(times[i]) : "");
            time.setTextColor(i == current ? primary : muted);
            TextView desc = findViewById(STEP_DESCS[i]);
            desc.setText(done ? doneDesc[i] : pendingDesc[i]);
            desc.setTextColor(done ? onSurface : outline);

            if (i < 3) findViewById(STEP_LINES[i]).setBackgroundColor(i < current ? primary : divider);
        }
    }

    private void bindActions(Donation d, boolean isNgo) {
        MaterialButton btnPrimary = findViewById(R.id.btn_handover_food);
        MaterialButton btnSecondary = findViewById(R.id.btn_edit_details);
        btnPrimary.setVisibility(View.VISIBLE);
        btnPrimary.setEnabled(true);
        btnSecondary.setVisibility(View.GONE);

        boolean isMyPickup = FirebaseRepo.uid().equals(d.ngoId);

        if (d.isExpired()) {
            btnPrimary.setVisibility(View.GONE);
        } else if (isNgo && Donation.STATUS_AVAILABLE.equals(d.status)) {
            btnPrimary.setText("Accept Pickup");
            btnPrimary.setOnClickListener(v -> acceptDonation(btnPrimary));
        } else if (isNgo && isMyPickup && Donation.STATUS_ACCEPTED.equals(d.status)) {
            btnPrimary.setText("Mark Picked Up");
            btnPrimary.setOnClickListener(v -> updateStatus(btnPrimary, Donation.STATUS_PICKED_UP, "pickedUpAt"));
        } else if (!isNgo && Donation.STATUS_ACCEPTED.equals(d.status)) {
            btnPrimary.setText("Hand Over Food");
            btnPrimary.setOnClickListener(v -> updateStatus(btnPrimary, Donation.STATUS_PICKED_UP, "pickedUpAt"));
        } else if (isNgo && isMyPickup && Donation.STATUS_PICKED_UP.equals(d.status)) {
            btnPrimary.setText("Mark Delivered");
            btnPrimary.setOnClickListener(v -> updateStatus(btnPrimary, Donation.STATUS_DELIVERED, "deliveredAt"));
        } else {
            btnPrimary.setVisibility(View.GONE);
        }

        text(R.id.tv_details_footer, footerHint(d, isNgo, isMyPickup));

        // Donors can withdraw a listing until an NGO claims it
        if (!isNgo && Donation.STATUS_AVAILABLE.equals(d.status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Delete Listing");
            btnSecondary.setIconResource(R.drawable.ic_delete);
            btnSecondary.setOnClickListener(v -> confirmDelete());
        }
    }

    /** One-line hint under the action buttons explaining what happens next. */
    private static String footerHint(Donation d, boolean isNgo, boolean isMyPickup) {
        if (d.isExpired()) return "This donation expired before it was claimed";
        switch (d.status) {
            case Donation.STATUS_AVAILABLE:
                return isNgo ? "Accepting reserves this food for your organisation" : "Waiting for an NGO to accept this donation";
            case Donation.STATUS_ACCEPTED:
                return isNgo && !isMyPickup ? "Another NGO has accepted this donation"
                        : isNgo ? "Collect the food, then mark it picked up" : "Hand the food over when the NGO arrives";
            case Donation.STATUS_PICKED_UP:
                return isNgo ? "Mark delivered once the food is distributed" : "The NGO is on the way to distribute your food";
            default:
                return "Thank you! This donation has been delivered";
        }
    }

    private void showMoreMenu(View anchor) {
        if (donation == null) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Get directions");
        menu.getMenu().add(0, 2, 1, "Show on map");
        boolean canDelete = !FirebaseRepo.ROLE_NGO.equals(role) && Donation.STATUS_AVAILABLE.equals(donation.status);
        if (canDelete) menu.getMenu().add(0, 3, 2, "Delete listing");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) openDirections();
            else if (item.getItemId() == 2) startActivity(new Intent(this, DonationsMapActivity.class)
                    .putExtra(DonationsMapActivity.EXTRA_FOCUS_ID, donation.id));
            else if (item.getItemId() == 3) confirmDelete();
            return true;
        });
        menu.show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete donation")
                .setMessage("Remove \"" + donation.foodName + "\" from the feed?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleting = true;
                    FirebaseRepo.donations().document(donation.id).delete()
                            .addOnSuccessListener(x -> Toast.makeText(this, "Donation removed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                deleting = false;
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Claims the donation in a transaction so two NGOs cannot accept the same food. */
    private void acceptDonation(MaterialButton button) {
        button.setEnabled(false);
        DocumentReference ref = FirebaseRepo.donations().document(donation.id);
        String uid = FirebaseRepo.uid();
        String donationId = donation.id;
        String donorId = donation.donorId;
        FirebaseRepo.db().runTransaction(tx -> {
            String status = tx.get(ref).getString("status");
            if (!Donation.STATUS_AVAILABLE.equals(status)) {
                throw new FirebaseFirestoreException("Already accepted by another NGO",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            Map<String, Object> update = new HashMap<>();
            update.put("status", Donation.STATUS_ACCEPTED);
            update.put("ngoId", uid);
            update.put("ngoName", userName);
            update.put("ngoPhone", userPhone);
            update.put("acceptedAt", FieldValue.serverTimestamp());
            tx.update(ref, update);

            Map<String, Object> request = new HashMap<>();
            request.put("donationId", donationId);
            request.put("ngoId", uid);
            request.put("donorId", donorId);
            request.put("volunteerId", null);
            request.put("status", Donation.STATUS_ACCEPTED);
            request.put("createdAt", FieldValue.serverTimestamp());
            tx.set(FirebaseRepo.pickupRequests().document(), request);
            return null;
        })
                .addOnSuccessListener(x -> Toast.makeText(this, "Pickup accepted! Find it under My Pickups.", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateStatus(MaterialButton button, String status, String timeField) {
        button.setEnabled(false);
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put(timeField, FieldValue.serverTimestamp());
        FirebaseRepo.donations().document(donation.id).update(update)
                .addOnSuccessListener(x -> Toast.makeText(this, "Marked as " + Donation.statusLabel(status), Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
        FirebaseRepo.pickupRequests().whereEqualTo("donationId", donation.id).get()
                .addOnSuccessListener(snap -> snap.getDocuments()
                        .forEach(doc -> doc.getReference().update("status", status)));
    }

    /** Opens turn-by-turn directions to the pickup point in Google Maps. */
    private void openDirections() {
        if (donation == null) return;
        Uri uri = Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f", donation.latitude, donation.longitude));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%f,%f", donation.latitude, donation.longitude))));
        }
    }

    private void share() {
        if (donation == null) return;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, donation.foodName + " (" + donation.quantity + " " + donation.unit
                        + ") available for pickup at " + donation.pickupAddress + " via Serve-N-Help");
        startActivity(Intent.createChooser(send, "Share donation"));
    }

    private void text(int id, String value) {
        ((TextView) findViewById(id)).setText(value == null ? "" : value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
