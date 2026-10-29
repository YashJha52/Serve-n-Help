package com.example.serve_n_help;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
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
    private AlertDialog codeDialog;
    private Donation donation;
    private String role;
    private String userName = "";
    private String userPhone = "";
    private boolean deleting;

    private static final long PORTER_POLL_MS = 3000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable porterPoll = this::refreshPorter;
    private String trackedOrderId;
    /** Whether the drop point being picked is for "accept and book" (vs. booking after accepting). */
    private boolean bookAlsoAccepts;

    private final ActivityResultLauncher<Intent> dropPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || donation == null) return;
                Intent data = result.getData();
                LatLng drop = new LatLng(data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0),
                        data.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0));
                quotePorter(drop, data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS));
            });

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || donation == null) return;
                if (result.getData().getBooleanExtra(HandoverScanActivity.EXTRA_MANUAL, false)) {
                    promptForCode();
                    return;
                }
                String[] parts = Handover.parse(result.getData().getStringExtra(HandoverScanActivity.EXTRA_PAYLOAD));
                if (parts == null) {
                    showHandoverError("That isn't a Serve-N-Help pickup code. Ask the donor to open this donation and tap Show pickup code.");
                } else if (!parts[0].equals(donation.id)) {
                    showHandoverError("This code is for a different donation. Check you're collecting \"" + donation.foodName + "\".");
                } else {
                    confirmPickup(parts[1]);
                }
            });

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
            // The donor's code dialog closes by itself once the NGO's scan lands
            if (codeDialog != null && codeDialog.isShowing() && !Donation.STATUS_ACCEPTED.equals(donation.status)) {
                codeDialog.dismiss();
                Toast.makeText(this, "Pickup confirmed by " + donation.ngoName, Toast.LENGTH_LONG).show();
            }
            bind();
        });
    }

    private void bind() {
        if (donation == null || role == null) return;
        Donation d = donation;
        boolean isNgo = FirebaseRepo.ROLE_NGO.equals(role);

        text(R.id.tv_details_id, d.shortId());
        Ui.styleStatus(findViewById(R.id.tv_details_status), d);
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
        text(R.id.tv_details_containers_label, "Made at");
        text(R.id.tv_details_containers, d.prepTime == null ? "-" : d.prepTime.replace("Today, ", ""));
        text(R.id.tv_details_address_title, d.donorName);
        text(R.id.tv_details_address, d.pickupAddress);
        text(R.id.tv_details_notes, TextUtils.isEmpty(d.dietaryNotes) ? "No additional notes." : d.dietaryNotes);
        text(R.id.tv_details_method, d.deliveryMethod);
        text(R.id.tv_details_meals, "Feeds about " + d.mealsEstimate() + " people");
        // Roughly 2.5 kg CO2e avoided per kg of food rescued
        text(R.id.tv_details_co2, String.format(Locale.getDefault(), "Saves ~%.0f kg CO₂", d.weightEstimateKg() * 2.5));

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
        bindPorter(d, isNgo);
    }

    private void bindTimeline(Donation d) {
        int current = d.stepIndex();
        text(R.id.tv_details_step, "Step " + (current + 1) + " of 4");

        Timestamp[] times = {d.createdAt, d.acceptedAt, d.pickedUpAt, d.deliveredAt};
        String[] doneDesc = {
                "Listed on the community rescue feed",
                "Claimed by " + (d.ngoName == null ? "an NGO" : d.ngoName),
                (d.porterOrderId != null ? "Porter driver collected it from " : "Collected from ")
                        + (d.donorName == null ? "the donor" : d.donorName)
                        + (d.pickupVerified ? ", verified by QR code" : ""),
                d.porterOrderId != null ? "Delivered to " + (d.ngoName == null ? "the NGO" : d.ngoName) + " by Porter"
                        : "Food delivered to people in need"};
        String[] pendingDesc = {
                "",
                d.isExpired() ? "Expired before an NGO accepted it" : "Waiting for an NGO to accept",
                "Waiting for pickup",
                "Waiting for delivery"};

        int primary = ContextCompat.getColor(this, R.color.accent);
        int onPrimary = ContextCompat.getColor(this, R.color.on_accent);
        int onSurface = ContextCompat.getColor(this, R.color.ink);
        int muted = ContextCompat.getColor(this, R.color.ink_muted);
        int outline = ContextCompat.getColor(this, R.color.ink_faint);
        int divider = ContextCompat.getColor(this, R.color.line);

        for (int i = 0; i < 4; i++) {
            boolean done = i <= current;
            ImageView icon = findViewById(STEP_ICONS[i]);
            icon.setBackgroundResource(done ? R.drawable.bg_step_done : R.drawable.bg_step_todo);
            icon.setImageTintList(ColorStateList.valueOf(done ? onPrimary : outline));

            TextView title = findViewById(STEP_TITLES[i]);
            title.setTextColor(i == current ? primary : done ? onSurface : muted);
            TextView time = findViewById(STEP_TIMES[i]);
            time.setText(done ? Ui.time(times[i]) : "");
            time.setTextColor(i == current ? primary : muted);
            TextView desc = findViewById(STEP_DESCS[i]);
            desc.setText(done ? doneDesc[i] : pendingDesc[i]);
            desc.setTextColor(done ? onSurface : muted);

            if (i < 3) findViewById(STEP_LINES[i]).setBackgroundColor(i < current ? primary : divider);
        }
    }

    private void bindActions(Donation d, boolean isNgo) {
        MaterialButton btnPrimary = findViewById(R.id.btn_handover_food);
        MaterialButton btnSecondary = findViewById(R.id.btn_edit_details);
        btnPrimary.setVisibility(View.VISIBLE);
        btnPrimary.setEnabled(true);
        btnPrimary.setIcon(null);
        btnSecondary.setVisibility(View.GONE);

        boolean isMyPickup = FirebaseRepo.uid().equals(d.ngoId);

        if (d.isExpired()) {
            btnPrimary.setVisibility(View.GONE);
        } else if (isNgo && Donation.STATUS_AVAILABLE.equals(d.status) && d.isPorter()) {
            btnPrimary.setText("Accept & book Porter");
            btnPrimary.setIconResource(R.drawable.ic_moped);
            btnPrimary.setOnClickListener(v -> pickDrop(true));
        } else if (isNgo && Donation.STATUS_AVAILABLE.equals(d.status)) {
            btnPrimary.setText("Accept pickup");
            btnPrimary.setOnClickListener(v -> acceptDonation(btnPrimary, null, null));
        } else if (d.porterOrderId != null && !Donation.STATUS_DELIVERED.equals(d.status) && (!isNgo || isMyPickup)) {
            // Porter moves the food from here on; the card above shows progress
            btnPrimary.setText("Track live");
            btnPrimary.setIconResource(R.drawable.ic_moped);
            btnPrimary.setOnClickListener(v -> PorterTrackingActivity.open(this, d.id));
        } else if (isNgo && isMyPickup && Donation.STATUS_ACCEPTED.equals(d.status) && d.isPorter()) {
            // Accepted, but no bike yet (booking failed or was cancelled): book one, or collect in person
            btnPrimary.setText("Book Porter");
            btnPrimary.setIconResource(R.drawable.ic_moped);
            btnPrimary.setOnClickListener(v -> pickDrop(false));
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Scan code");
            btnSecondary.setIconResource(R.drawable.ic_qr_code_scanner);
            tintSecondary(btnSecondary, R.color.ink);
            btnSecondary.setOnClickListener(v -> scanLauncher.launch(new Intent(this, HandoverScanActivity.class)));
        } else if (isNgo && isMyPickup && Donation.STATUS_ACCEPTED.equals(d.status)) {
            // Pickup is only confirmed by scanning (or typing) the donor's one-time code
            btnPrimary.setText("Scan code");
            btnPrimary.setIconResource(R.drawable.ic_qr_code_scanner);
            btnPrimary.setOnClickListener(v -> scanLauncher.launch(new Intent(this, HandoverScanActivity.class)));
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Enter code");
            btnSecondary.setIconResource(R.drawable.ic_keyboard);
            tintSecondary(btnSecondary, R.color.ink);
            btnSecondary.setOnClickListener(v -> promptForCode());
        } else if (!isNgo && Donation.STATUS_ACCEPTED.equals(d.status)) {
            btnPrimary.setText("Show pickup code");
            btnPrimary.setIconResource(R.drawable.ic_qr_code_scanner);
            btnPrimary.setOnClickListener(v -> showPickupCode(btnPrimary));
        } else if (isNgo && isMyPickup && Donation.STATUS_PICKED_UP.equals(d.status)) {
            btnPrimary.setText("Mark as delivered");
            btnPrimary.setOnClickListener(v -> updateStatus(btnPrimary, Donation.STATUS_DELIVERED, "deliveredAt"));
        } else {
            btnPrimary.setVisibility(View.GONE);
        }

        text(R.id.tv_details_footer, footerHint(d, isNgo, isMyPickup));

        // Donors can withdraw a listing until an NGO claims it
        if (!isNgo && Donation.STATUS_AVAILABLE.equals(d.status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Delete");
            btnSecondary.setIconResource(R.drawable.ic_delete);
            tintSecondary(btnSecondary, R.color.danger);
            btnSecondary.setOnClickListener(v -> confirmDelete());
        }
    }

    /** One-line hint under the action buttons explaining what happens next. */
    private static String footerHint(Donation d, boolean isNgo, boolean isMyPickup) {
        if (d.isExpired()) return "This donation expired before it was claimed";
        if (d.isPorter() && !Donation.STATUS_DELIVERED.equals(d.status)) {
            if (Donation.STATUS_AVAILABLE.equals(d.status)) {
                return isNgo ? "You'll choose where Porter drops it and see the fare before booking"
                        : "The NGO that accepts books a Porter bike to collect it";
            }
            if (isNgo && !isMyPickup) return "Another NGO has accepted this donation";
            if (d.porterOrderId != null) {
                return isNgo ? "Porter is bringing the food to you" : "Hand the food to the Porter driver when they arrive";
            }
            return isNgo ? "Book a Porter bike, or collect it yourself with the donor's code"
                    : "Waiting for the NGO to book a Porter bike";
        }
        switch (d.status) {
            case Donation.STATUS_AVAILABLE:
                return isNgo ? "Accepting reserves this food for your organisation" : "Waiting for an NGO to accept this donation";
            case Donation.STATUS_ACCEPTED:
                return isNgo && !isMyPickup ? "Another NGO has accepted this donation"
                        : isNgo ? "At the pickup point, scan the QR code on the donor's phone"
                        : "When the NGO arrives, show them your pickup code";
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
        Ui.destructive(new MaterialAlertDialogBuilder(this)
                .setTitle("Delete donation")
                .setMessage("Remove \"" + donation.foodName + "\" from the feed?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleting = true;
                    WriteBatch batch = FirebaseRepo.db().batch();
                    batch.delete(Handover.privateDoc(donation.id));
                    batch.delete(FirebaseRepo.donations().document(donation.id));
                    batch.commit()
                            .addOnSuccessListener(x -> Toast.makeText(this, "Donation removed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                deleting = false;
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    /** Claims the donation in a transaction so two NGOs cannot accept the same food. */
    private void acceptDonation(MaterialButton button, Map<String, Object> extra, Runnable onAccepted) {
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
            if (extra != null) update.putAll(extra);
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
                .addOnSuccessListener(x -> {
                    if (onAccepted != null) onAccepted.run();
                    else Toast.makeText(this, "Pickup accepted! Find it under My Pickups.", Toast.LENGTH_LONG).show();
                })
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

    /** Shows the Porter card while a bike is booked and keeps it updated. */
    private void bindPorter(Donation d, boolean isNgo) {
        boolean booked = d.porterOrderId != null;
        findViewById(R.id.frame_porter).setVisibility(booked ? View.VISIBLE : View.GONE);
        if (!booked) {
            trackedOrderId = null;
            handler.removeCallbacks(porterPoll);
            return;
        }
        // "Track live" sits in the bottom bar here; the card keeps only Cancel
        findViewById(R.id.btn_porter_track).setVisibility(View.GONE);
        findViewById(R.id.btn_porter_cancel).setOnClickListener(v -> confirmCancelPorter());
        if (!d.porterOrderId.equals(trackedOrderId)) {
            trackedOrderId = d.porterOrderId;
            refreshPorter();
        }
    }

    private void refreshPorter() {
        handler.removeCallbacks(porterPoll);
        if (donation == null || donation.porterOrderId == null) return;
        Porter.api().trackOrder(donation.porterOrderId).addOnSuccessListener(t -> {
            if (isFinishing() || donation == null || !t.orderId.equals(donation.porterOrderId)) return;
            PorterCard.bind(this, t, donation.dropAddress);
            boolean canCancel = FirebaseRepo.ROLE_NGO.equals(role) && FirebaseRepo.uid().equals(donation.ngoId) && t.cancellable();
            findViewById(R.id.btn_porter_cancel).setVisibility(canCancel ? View.VISIBLE : View.GONE);
            Porter.syncDonation(donation, t);
            boolean finished = Porter.STATUS_ENDED.equals(t.status) || Porter.STATUS_CANCELLED.equals(t.status);
            // The real API pushes webhooks; the sandbox is polled while this screen is open
            if (!finished) handler.postDelayed(porterPoll, PORTER_POLL_MS);
        });
    }

    /** NGO step 1: choose where the bike should drop the food (usually the NGO's own address). */
    private void pickDrop(boolean alsoAccept) {
        bookAlsoAccepts = alsoAccept;
        dropPicker.launch(new Intent(this, LocationPickerActivity.class)
                .putExtra(LocationPickerActivity.EXTRA_PROMPT, "Where should Porter drop the food?"));
    }

    /** NGO step 2: fetch the fare and confirm before anything is booked. */
    private void quotePorter(LatLng drop, String dropAddress) {
        MaterialButton button = findViewById(R.id.btn_handover_food);
        button.setEnabled(false);
        button.setText("Getting fare…");
        LatLng pickup = new LatLng(donation.latitude, donation.longitude);
        Porter.api().getQuote(pickup, drop)
                .addOnSuccessListener(q -> {
                    bind();
                    StringBuilder msg = new StringBuilder();
                    double kg = donation.weightEstimateKg();
                    if (kg > Porter.MAX_LOAD_KG) {
                        msg.append(String.format(Locale.getDefault(),
                                "Heads up: this is about %.0f kg and Porter bikes carry up to %.0f kg, so it may take two trips.\n\n",
                                kg, Porter.MAX_LOAD_KG));
                    }
                    msg.append(q.vehicle).append(" · ")
                            .append(String.format(Locale.getDefault(), "%.1f km", q.distanceKm)).append('\n')
                            .append("Driver at the pickup in about ").append(Math.max(1, q.pickupEtaSeconds / 60)).append(" min\n\n")
                            .append("Drop: ").append(dropAddress).append("\n\n")
                            .append("Your organisation pays Porter ").append(Porter.rupees(q.fare)).append(" for this trip.");
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(bookAlsoAccepts ? "Accept and book Porter?" : "Book Porter?")
                            .setMessage(msg)
                            .setPositiveButton("Book for " + Porter.rupees(q.fare), (dlg, w) -> bookPorter(drop, dropAddress, q.fare))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    bind();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Porter can't take this trip")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
    }

    /** NGO step 3: claim the donation (if needed), then create the Porter order. */
    private void bookPorter(LatLng drop, String dropAddress, int fare) {
        MaterialButton button = findViewById(R.id.btn_handover_food);
        Map<String, Object> dropFields = new HashMap<>();
        dropFields.put("dropAddress", dropAddress);
        dropFields.put("dropLat", drop.latitude);
        dropFields.put("dropLng", drop.longitude);
        Runnable createOrder = () -> {
            Porter.OrderRequest r = new Porter.OrderRequest();
            r.requestId = donation.id;
            r.pickup = new LatLng(donation.latitude, donation.longitude);
            r.pickupName = donation.donorName;
            r.pickupAddress = donation.pickupAddress;
            r.drop = drop;
            r.dropName = userName;
            r.dropAddress = dropAddress;
            String donationId = donation.id;
            Porter.api().createOrder(r)
                    .addOnSuccessListener(orderId -> {
                        Map<String, Object> update = new HashMap<>(dropFields);
                        update.put("porterOrderId", orderId);
                        update.put("porterFare", fare);
                        FirebaseRepo.donations().document(donationId).update(update);
                        Toast.makeText(this, "Porter booked. Finding a driver…", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        button.setEnabled(true);
                        Toast.makeText(this, "Porter booking failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        };
        if (bookAlsoAccepts) {
            acceptDonation(button, dropFields, createOrder);
        } else {
            button.setEnabled(false);
            createOrder.run();
        }
    }

    private void confirmCancelPorter() {
        Ui.destructive(new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel the Porter bike?")
                .setMessage("You'll still have this donation. You can book another bike or collect it yourself.")
                .setPositiveButton("Cancel bike", (dlg, w) -> Porter.api()
                        .cancelOrder(donation.porterOrderId, "Cancelled by NGO")
                        .addOnSuccessListener(x -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("porterOrderId", FieldValue.delete());
                            update.put("porterFare", FieldValue.delete());
                            FirebaseRepo.donations().document(donation.id).update(update);
                            Toast.makeText(this, "Porter booking cancelled", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Keep bike", null)
                .show());
    }

    private static void tintSecondary(MaterialButton button, int colorRes) {
        int color = ContextCompat.getColor(button.getContext(), colorRes);
        button.setTextColor(color);
        button.setIconTint(ColorStateList.valueOf(color));
    }

    /**
     * Donor side: shows the one-time pickup code, creating it the first time. Only its hash goes
     * on the public donation; the code itself sits in a document only the donor can read.
     */
    private void showPickupCode(MaterialButton button) {
        button.setEnabled(false);
        String donationId = donation.id;
        Handover.privateDoc(donationId).get()
                .addOnSuccessListener(doc -> {
                    String code = doc.getString("code");
                    if (code != null && Handover.hash(donationId, code).equals(donation.handoverHash)) {
                        button.setEnabled(true);
                        showCodeDialog(code);
                        return;
                    }
                    String fresh = Handover.newCode();
                    Map<String, Object> secret = new HashMap<>();
                    secret.put("code", fresh);
                    secret.put("createdAt", FieldValue.serverTimestamp());
                    WriteBatch batch = FirebaseRepo.db().batch();
                    batch.set(Handover.privateDoc(donationId), secret);
                    batch.update(FirebaseRepo.donations().document(donationId), "handoverHash", Handover.hash(donationId, fresh));
                    batch.commit()
                            .addOnSuccessListener(x -> {
                                button.setEnabled(true);
                                showCodeDialog(fresh);
                            })
                            .addOnFailureListener(e -> {
                                button.setEnabled(true);
                                Toast.makeText(this, "Couldn't create the code: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    Toast.makeText(this, "Couldn't load the code: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showCodeDialog(String code) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_pickup_code, null);
        int size = (int) (220 * getResources().getDisplayMetrics().density);
        ((ImageView) content.findViewById(R.id.iv_pickup_qr)).setImageBitmap(
                Handover.qrBitmap(Handover.qrPayload(donation.id, code), size));
        ((TextView) content.findViewById(R.id.tv_pickup_code)).setText(Handover.pretty(code));

        // Full brightness makes the code much easier to scan; restored when the dialog closes
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float previous = lp.screenBrightness;
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        codeDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Pickup code")
                .setView(content)
                .setPositiveButton("Done", null)
                .setOnDismissListener(x -> {
                    WindowManager.LayoutParams restore = getWindow().getAttributes();
                    restore.screenBrightness = previous;
                    getWindow().setAttributes(restore);
                })
                .show();
    }

    /** NGO fallback when the camera can't be used: type the 8-character code. */
    private void promptForCode() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_field, null);
        TextInputLayout til = content.findViewById(R.id.til_edit);
        TextInputEditText input = content.findViewById(R.id.et_edit);
        til.setHelperText("8 letters and numbers, shown under the donor's QR code");
        input.setHint("e.g. ABCD-2345");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(Handover.CODE_LENGTH + 1)});

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Enter pickup code")
                .setView(content)
                .setPositiveButton("Confirm pickup", null)
                .setNegativeButton("Cancel", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = Handover.normalise(input.getText() == null ? "" : input.getText().toString());
            if (code.length() != Handover.CODE_LENGTH) {
                til.setError("The code has " + Handover.CODE_LENGTH + " characters");
                return;
            }
            if (!matches(code)) {
                til.setError(donation.handoverHash == null
                        ? "The donor hasn't opened their pickup code yet"
                        : "That code doesn't match. Check it with the donor.");
                return;
            }
            dialog.dismiss();
            confirmPickup(code);
        });
    }

    private boolean matches(String code) {
        return donation.handoverHash != null && Handover.hash(donation.id, code).equals(donation.handoverHash);
    }

    /** Checks the code against the published hash, then records the verified pickup. */
    private void confirmPickup(String code) {
        if (donation.handoverHash == null) {
            showHandoverError("The donor hasn't opened their pickup code yet. Ask them to tap Show pickup code, then scan again.");
            return;
        }
        if (!matches(code)) {
            showHandoverError("This code doesn't match the donation. Ask the donor to show the code again.");
            return;
        }
        MaterialButton button = findViewById(R.id.btn_handover_food);
        button.setEnabled(false);
        Map<String, Object> update = new HashMap<>();
        update.put("status", Donation.STATUS_PICKED_UP);
        update.put("pickedUpAt", FieldValue.serverTimestamp());
        update.put("handoverProof", code);
        update.put("pickupVerified", true);
        String foodName = donation.foodName;
        FirebaseRepo.donations().document(donation.id).update(update)
                .addOnSuccessListener(x -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Pickup confirmed")
                        .setMessage("The code matched. " + foodName + " is now marked as picked up.")
                        .setPositiveButton("OK", null)
                        .show())
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    showHandoverError("Couldn't confirm the pickup: " + e.getMessage());
                });
        FirebaseRepo.pickupRequests().whereEqualTo("donationId", donation.id).get()
                .addOnSuccessListener(snap -> snap.getDocuments()
                        .forEach(doc -> doc.getReference().update("status", Donation.STATUS_PICKED_UP)));
    }

    private void showHandoverError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Pickup not confirmed")
                .setMessage(message)
                .setPositiveButton("Scan again", (d, w) -> scanLauncher.launch(new Intent(this, HandoverScanActivity.class)))
                .setNegativeButton("Close", null)
                .show();
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
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(porterPoll);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (trackedOrderId != null) refreshPorter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(porterPoll);
        if (listener != null) listener.remove();
    }
}
