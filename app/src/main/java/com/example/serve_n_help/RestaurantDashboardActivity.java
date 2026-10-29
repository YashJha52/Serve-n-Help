package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RestaurantDashboardActivity extends BaseActivity {

    private ListenerRegistration donationsListener;
    private final List<Donation> donations = new ArrayList<>();
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant_dashboard);

        if (FirebaseRepo.uid() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        TextView tvName = findViewById(R.id.tv_dashboard_restaurant_name);
        TextView tvGreeting = findViewById(R.id.tv_dashboard_greeting);
        TextView tvKitchenId = findViewById(R.id.tv_dashboard_kitchen_id);
        MaterialButton btnAddFood = findViewById(R.id.btn_add_food_cta);
        TextView tvSeeAll = findViewById(R.id.tv_see_all_donations);
        bottomNav = findViewById(R.id.bottom_navigation);
        LinearLayout cards = findViewById(R.id.layout_donation_cards);

        tvGreeting.setText(greeting());
        tvName.setText("");

        FirebaseRepo.users().document(FirebaseRepo.uid()).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    String rep = doc.getString("representative");
                    String license = doc.getString("licenseId");
                    if (name != null) tvName.setText(name);
                    String who = !TextUtils.isEmpty(rep) ? rep.split("\\s+")[0] : name;
                    tvGreeting.setText(greeting() + (TextUtils.isEmpty(who) ? "" : ", " + who));
                    tvKitchenId.setText(TextUtils.isEmpty(license) ? "Food donor" : "FSSAI licensed · " + license);
                });

        btnAddFood.setOnClickListener(v -> startActivity(new Intent(this, AddFoodActivity.class)));
        tvSeeAll.setOnClickListener(v -> startActivity(new Intent(this, FoodAvailableActivity.class)));
        findViewById(R.id.iv_dashboard_profile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btn_dashboard_notifications).setOnClickListener(v -> showNotifications());

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_donations) {
                startActivity(new Intent(this, FoodAvailableActivity.class));
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, FoodAvailableActivity.class)
                        .putExtra(FoodAvailableActivity.EXTRA_HISTORY, true));
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
            return id == R.id.nav_home;
        });

        // Live list of this donor's donations; sorted locally so no composite index is needed
        donationsListener = FirebaseRepo.donations()
                .whereEqualTo("donorId", FirebaseRepo.uid())
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Could not load donations: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    donations.clear();
                    if (snap != null) donations.addAll(snap.toObjects(Donation.class));
                    donations.sort((a, b) -> a.createdAt == null || b.createdAt == null ? 0 : b.createdAt.compareTo(a.createdAt));
                    updateStats();

                    cards.removeAllViews();
                    List<Donation> active = new ArrayList<>();
                    for (Donation d : donations) {
                        if (!Donation.STATUS_DELIVERED.equals(d.status) && !d.isExpired()) active.add(d);
                    }
                    ((TextView) findViewById(R.id.tv_available_count)).setText(String.valueOf(active.size()));
                    if (active.isEmpty()) {
                        TextView empty = Ui.emptyState(this);
                        empty.setText("Nothing live right now. Had food left over today? Tap Add food and nearby NGOs will see it.");
                                                cards.addView(empty);
                        return;
                    }
                    for (Donation d : active.subList(0, Math.min(5, active.size()))) {
                        cards.addView(buildCard(cards, d));
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from another tab should highlight Home again
        if (bottomNav != null) bottomNav.getMenu().findItem(R.id.nav_home).setChecked(true);
    }

    private void updateStats() {
        double kg = 0;
        int active = 0;
        int completed = 0;
        boolean urgent = false;
        long twoHours = System.currentTimeMillis() + 2 * 60 * 60 * 1000;
        for (Donation d : donations) {
            if (Donation.STATUS_DELIVERED.equals(d.status)) {
                completed++;
                kg += d.weightEstimateKg();
            } else if (!d.isExpired()) {
                active++;
                if (Donation.STATUS_AVAILABLE.equals(d.status) && d.expiresAt != null
                        && d.expiresAt.toDate().getTime() < twoHours) {
                    urgent = true;
                }
            }
        }
        ((TextView) findViewById(R.id.tv_stat_total_kg)).setText(String.format(Locale.getDefault(), "%.0f", kg));
        ((TextView) findViewById(R.id.tv_stat_active)).setText(String.valueOf(active));
        ((TextView) findViewById(R.id.tv_stat_completed)).setText(String.valueOf(completed));
        findViewById(R.id.tv_stat_urgent).setVisibility(urgent ? View.VISIBLE : View.GONE);
    }

    private void showNotifications() {
        List<String> lines = new ArrayList<>();
        for (Donation d : donations) {
            if (d.ngoName == null) continue;
            String when = Ui.dateTime(d.deliveredAt != null ? d.deliveredAt : d.pickedUpAt != null ? d.pickedUpAt : d.acceptedAt);
            lines.add("• " + d.foodName + ": " + Donation.statusLabel(d.status).toLowerCase(Locale.getDefault())
                    + " by " + d.ngoName + (when.isEmpty() ? "" : " (" + when + ")"));
            if (lines.size() == 10) break;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Notifications")
                .setMessage(lines.isEmpty() ? "No updates yet. You will see here when an NGO accepts your food."
                        : TextUtils.join("\n\n", lines))
                .setPositiveButton("OK", null)
                .show();
    }

    private View buildCard(LinearLayout parent, Donation d) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_dashboard_donation, parent, false);
        ((TextView) card.findViewById(R.id.tv_food_title)).setText(d.foodName);
        ((TextView) card.findViewById(R.id.tv_food_subtitle)).setText(d.category + " • " + d.deliveryMethod);
        Ui.styleStatus(card.findViewById(R.id.tv_status_badge), d);
        ((TextView) card.findViewById(R.id.tv_portion_chip)).setText(d.quantity + " " + d.unit);
        ((TextView) card.findViewById(R.id.tv_expiry_chip)).setText(Ui.expiryText(d));

        View footnote = card.findViewById(R.id.layout_logistics_footnote);
        if (d.ngoName != null) {
            footnote.setVisibility(View.VISIBLE);
            ((TextView) card.findViewById(R.id.tv_partner_name)).setText(d.ngoName);
            ((TextView) card.findViewById(R.id.tv_eta_status)).setText(
                    Donation.STATUS_PICKED_UP.equals(d.status) ? "Food picked up • on the way" : "Pickup accepted • awaiting collection");
            card.findViewById(R.id.btn_partner_call).setOnClickListener(v -> Ui.dial(this, d.ngoPhone));
        } else {
            footnote.setVisibility(View.GONE);
        }

        card.setOnClickListener(v -> DonationDetailsActivity.open(this, d.id));
        return card;
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    static void confirmSignOut(AppCompatActivity activity) {
        Ui.destructive(new MaterialAlertDialogBuilder(activity)
                .setTitle("Sign out")
                .setMessage("Do you want to sign out of Serve-N-Help?")
                .setPositiveButton("Sign out", (dialog, which) -> {
                    FirebaseRepo.auth().signOut();
                    // Forget the chosen Google account so the picker shows again next time
                    CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                            ContextCompat.getMainExecutor(activity),
                            new CredentialManagerCallback<Void, ClearCredentialException>() {
                                @Override public void onResult(Void unused) { }
                                @Override public void onError(ClearCredentialException e) { }
                            });
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (donationsListener != null) donationsListener.remove();
    }
}
