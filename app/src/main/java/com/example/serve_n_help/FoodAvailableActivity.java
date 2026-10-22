package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Donation feed.
 * Donors: their active listings (Donations tab) or delivered/expired ones (History tab).
 * NGOs: every available donation (Donations tab) or the pickups they accepted (History tab).
 */
public class FoodAvailableActivity extends BaseActivity {

    public static final String EXTRA_HISTORY = "history";

    private static final String[] CHIP_CATEGORIES = {null, "Cooked Meals", "Bakery", "Raw Produce", "Packaged Food"};
    private static final int[] CHIP_IDS = {R.id.chip_all, R.id.chip_cat1, R.id.chip_cat2, R.id.chip_cat3, R.id.chip_cat4};

    private final List<Donation> donations = new ArrayList<>();
    private ListenerRegistration listener;
    private LinearLayout container;
    private EditText etSearch;
    private BottomNavigationView bottomNav;
    private boolean isNgo;
    private boolean historyMode;
    private String categoryFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_available);

        if (FirebaseRepo.uid() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        historyMode = getIntent().getBooleanExtra(EXTRA_HISTORY, false);
        container = findViewById(R.id.layout_available_items);
        etSearch = findViewById(R.id.et_food_search);
        ImageButton btnMap = findViewById(R.id.btn_filter_tune);
        View btnQuickAdd = findViewById(R.id.btn_quick_add_food);
        bottomNav = findViewById(R.id.bottom_navigation_feed);
        TextView tvHeaderName = findViewById(R.id.tv_feed_header_name);

        container.removeAllViews();
        tvHeaderName.setText("");
        findViewById(R.id.btn_back_food_available).setOnClickListener(v -> finish());

        // The tune button opens the map of nearby donations
        btnMap.setImageResource(R.drawable.ic_location_on);
        btnMap.setContentDescription("Map view");
        btnMap.setOnClickListener(v -> startActivity(new Intent(this, DonationsMapActivity.class)));

        btnQuickAdd.setOnClickListener(v -> startActivity(new Intent(this, AddFoodActivity.class)));
        findViewById(R.id.iv_feed_profile).setOnClickListener(v -> RestaurantDashboardActivity.confirmSignOut(this));
        findViewById(R.id.btn_feed_notifications).setOnClickListener(v -> showNotifications());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { render(); }
        });

        // Category filter chips
        for (int i = 0; i < CHIP_IDS.length; i++) {
            TextView chip = findViewById(CHIP_IDS[i]);
            String category = CHIP_CATEGORIES[i];
            chip.setText(category == null ? "All" : category);
            chip.setOnClickListener(v -> {
                categoryFilter = category;
                render();
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                if (isNgo) {
                    startActivity(new Intent(this, DonationsMapActivity.class));
                } else {
                    finish();
                }
                return false;
            } else if (id == R.id.nav_profile) {
                RestaurantDashboardActivity.confirmSignOut(this);
                return false;
            }
            boolean history = id == R.id.nav_history;
            if (history != historyMode) {
                historyMode = history;
                listen();
            }
            return true;
        });

        FirebaseRepo.users().document(FirebaseRepo.uid()).get()
                .addOnSuccessListener(doc -> {
                    isNgo = FirebaseRepo.ROLE_NGO.equals(doc.getString("role"));
                    String name = doc.getString("name");
                    tvHeaderName.setText(name == null ? "" : name);
                    btnQuickAdd.setVisibility(isNgo ? View.GONE : View.VISIBLE);
                    // For NGOs this is the home screen, so there is nothing to go back to
                    if (isNgo) findViewById(R.id.btn_back_food_available).setVisibility(View.GONE);
                    listen();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Could not load profile: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.getMenu().findItem(historyMode ? R.id.nav_history : R.id.nav_donations).setChecked(true);
        }
        // Expiry countdowns change over time
        if (container != null && !donations.isEmpty()) render();
    }

    private void listen() {
        if (listener != null) listener.remove();
        ((TextView) findViewById(R.id.tv_feed_header_role)).setText(
                historyMode ? (isNgo ? "My Pickups" : "History") : (isNgo ? "NGO" : "Donations"));
        ((TextView) findViewById(R.id.tv_feed_title_main)).setText(
                historyMode ? (isNgo ? "My Pickups" : "Donation History") : (isNgo ? "Food Available" : "My Listings"));
        bottomNav.getMenu().findItem(historyMode ? R.id.nav_history : R.id.nav_donations).setChecked(true);

        Query query;
        if (isNgo) {
            query = historyMode
                    ? FirebaseRepo.donations().whereEqualTo("ngoId", FirebaseRepo.uid())
                    : FirebaseRepo.donations().whereEqualTo("status", Donation.STATUS_AVAILABLE);
        } else {
            query = FirebaseRepo.donations().whereEqualTo("donorId", FirebaseRepo.uid());
        }
        listener = query.addSnapshotListener((snap, e) -> {
            if (e != null) {
                Toast.makeText(this, "Could not load donations: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            donations.clear();
            if (snap != null) donations.addAll(snap.toObjects(Donation.class));
            // Newest first (sorted locally so no composite index is needed)
            donations.sort((a, b) -> {
                if (a.createdAt == null || b.createdAt == null) return 0;
                return b.createdAt.compareTo(a.createdAt);
            });
            render();
        });
    }

    /** Whether a donation belongs in the current tab, before search/category filters. */
    private boolean inCurrentTab(Donation d) {
        if (isNgo) {
            return historyMode || !d.isExpired();
        }
        boolean finished = Donation.STATUS_DELIVERED.equals(d.status) || d.isExpired();
        return historyMode == finished;
    }

    private void render() {
        container.removeAllViews();
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());

        List<Donation> tab = new ArrayList<>();
        for (Donation d : donations) {
            if (inCurrentTab(d)) tab.add(d);
        }

        int shown = 0;
        for (Donation d : tab) {
            if (categoryFilter != null && !categoryFilter.equals(d.category)) continue;
            String haystack = (d.foodName + " " + d.category + " " + d.pickupAddress + " " + d.dietaryNotes)
                    .toLowerCase(Locale.getDefault());
            if (!q.isEmpty() && !haystack.contains(q)) continue;
            container.addView(buildItem(d));
            shown++;
        }

        for (int i = 0; i < CHIP_IDS.length; i++) {
            TextView chip = findViewById(CHIP_IDS[i]);
            boolean selected = categoryFilter == null ? CHIP_CATEGORIES[i] == null : categoryFilter.equals(CHIP_CATEGORIES[i]);
            Ui.styleChip(chip, selected);
            if (CHIP_CATEGORIES[i] == null) chip.setText("All (" + tab.size() + ")");
        }
        ((TextView) findViewById(R.id.tv_feed_live_count)).setText(
                (historyMode ? "RECORDS • " : "LIVE INVENTORY • ") + tab.size() + (tab.size() == 1 ? " BATCH" : " BATCHES"));
        updateBanner();

        if (shown == 0) {
            TextView empty = new TextView(this);
            if (!tab.isEmpty()) {
                empty.setText("No donations match your search.");
            } else if (isNgo) {
                empty.setText(historyMode ? "You have not accepted any pickups yet." : "No food available right now. Check back soon!");
            } else {
                empty.setText(historyMode ? "Delivered and expired donations will appear here." : "You have no active listings. Tap \"Add Food\" to post one.");
            }
            empty.setPadding(8, 24, 8, 24);
            container.addView(empty);
        }
    }

    private void showNotifications() {
        List<String> lines = new ArrayList<>();
        for (Donation d : donations) {
            if (lines.size() == 8) break;
            if (isNgo && !historyMode) {
                if (!d.isExpired()) lines.add("• New: " + d.foodName + " (" + d.quantity + " " + d.unit + ") from "
                        + d.donorName + ", posted " + Ui.dateTime(d.createdAt));
            } else if (d.ngoName != null) {
                lines.add("• " + d.foodName + ": " + Donation.statusLabel(d.status).toLowerCase(Locale.getDefault())
                        + (isNgo ? "" : " by " + d.ngoName));
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage(lines.isEmpty() ? "You're all caught up." : android.text.TextUtils.join("\n\n", lines))
                .setPositiveButton("OK", null)
                .show();
    }

    private void updateBanner() {
        int meals = 0;
        int delivered = 0;
        for (Donation d : donations) {
            if (Donation.STATUS_DELIVERED.equals(d.status)) {
                meals += d.mealsEstimate();
                delivered++;
            }
        }
        TextView title = findViewById(R.id.tv_feed_banner_title);
        TextView text = findViewById(R.id.tv_feed_banner_text);
        if (isNgo && !historyMode) {
            title.setText("Rescue food near you");
            text.setText("Tap the map pin above to see donations around you");
        } else if (delivered == 0) {
            title.setText("Your impact starts here");
            text.setText(isNgo ? "Accept a pickup to start rescuing food" : "Completed donations will show your impact");
        } else {
            title.setText(delivered + (delivered == 1 ? " delivery completed" : " deliveries completed"));
            text.setText((isNgo ? "You have helped feed about " : "Your food has helped feed about ") + meals + " people");
        }
    }

    private View buildItem(Donation d) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_food_available, container, false);
        ((TextView) item.findViewById(R.id.tv_feed_status)).setText(d.isExpired() ? "Expired" : Donation.statusLabel(d.status));
        ((TextView) item.findViewById(R.id.tv_feed_expiry)).setText(Ui.expiryText(d));
        ((TextView) item.findViewById(R.id.tv_feed_id)).setText(d.shortId());
        ((TextView) item.findViewById(R.id.tv_feed_title)).setText(d.foodName);
        ((TextView) item.findViewById(R.id.tv_feed_quantity)).setText(d.quantity + " " + d.unit
                + String.format(Locale.getDefault(), " (approx %.1f kg)", d.weightEstimateKg()));
        ((TextView) item.findViewById(R.id.tv_feed_category_tags)).setText(
                d.category + (d.dietaryNotes == null || d.dietaryNotes.isEmpty() ? "" : " • " + d.dietaryNotes));
        ((TextView) item.findViewById(R.id.tv_feed_prep)).setText("Prep: " + d.prepTime);
        ((TextView) item.findViewById(R.id.tv_feed_expires_time)).setText("Expires: " + d.expiryDate + " " + d.expiryTime);
        ((TextView) item.findViewById(R.id.tv_feed_location)).setText(
                (isNgo && d.donorName != null ? d.donorName + ", " : "") + d.pickupAddress);
        ((TextView) item.findViewById(R.id.tv_feed_fulfillment)).setText(d.deliveryMethod);

        View btnDelete = item.findViewById(R.id.btn_feed_delete);
        View btnEdit = item.findViewById(R.id.btn_feed_edit);
        boolean canDelete = !isNgo && Donation.STATUS_AVAILABLE.equals(d.status);
        btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        btnEdit.setVisibility(View.GONE);
        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete donation")
                .setMessage("Remove \"" + d.foodName + "\" from the feed?")
                .setPositiveButton("Delete", (dialog, which) -> FirebaseRepo.donations().document(d.id).delete()
                        .addOnSuccessListener(x -> Toast.makeText(this, "Donation removed", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancel", null)
                .show());
        item.findViewById(R.id.btn_feed_info).setOnClickListener(v -> DonationDetailsActivity.open(this, d.id));
        item.setOnClickListener(v -> DonationDetailsActivity.open(this, d.id));
        return item;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
