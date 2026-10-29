package com.example.serve_n_help;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class AddFoodActivity extends BaseActivity {

    private static final String[] CATEGORIES = {"Cooked Meals", "Bakery", "Raw Produce", "Packaged Food", "Dairy", "Beverages"};
    private static final String[] UNITS = {"Portions", "Kg", "Boxes", "Packets", "Litres"};

    private String donorName = "";
    private String donorPhone = "";
    private String pickupAddress;
    private double latitude;
    private double longitude;

    // Expiry date and time picked by the user; combined into expiresAt
    private final Calendar expiry = Calendar.getInstance();
    private boolean expiryTimeSet;
    private final Set<String> dietTags = new LinkedHashSet<>();

    private TextView tvPickupAddress;

    private final ActivityResultLauncher<Intent> locationPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    latitude = data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0);
                    longitude = data.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0);
                    pickupAddress = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS);
                    tvPickupAddress.setText(pickupAddress);
                    ((TextView) findViewById(R.id.tv_change_address)).setText("Change");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_food);

        TextInputEditText etFoodName = findViewById(R.id.et_food_name);
        AutoCompleteTextView actvCategory = findViewById(R.id.actv_category);
        TextInputEditText etQuantity = findViewById(R.id.et_quantity);
        AutoCompleteTextView actvUnit = findViewById(R.id.actv_unit);
        TextInputEditText etPrepTime = findViewById(R.id.et_prep_time);
        TextInputEditText etExpiryDate = findViewById(R.id.et_expiry_date);
        TextInputEditText etExpiryTime = findViewById(R.id.et_expiry_time);
        TextInputEditText etNotes = findViewById(R.id.et_dietary_notes);
        TextView tvExpiryHint = findViewById(R.id.tv_expiry_hint);
        RadioButton rbPickup = findViewById(R.id.rb_pickup);
        RadioButton rbSelf = findViewById(R.id.rb_self_delivery);
        MaterialButton btnPublish = findViewById(R.id.btn_publish_donation);
        TextView tvPickupName = findViewById(R.id.tv_pickup_name);
        tvPickupAddress = findViewById(R.id.tv_pickup_address);

        findViewById(R.id.btn_back_add_food).setOnClickListener(v -> finish());

        // Dropdowns: setText(..., false) so the list is not filtered down to the current value
        actvCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, CATEGORIES));
        actvUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, UNITS));
        actvCategory.setText(CATEGORIES[0], false);
        actvUnit.setText(UNITS[0], false);
        actvCategory.setOnClickListener(v -> actvCategory.showDropDown());
        actvUnit.setOnClickListener(v -> actvUnit.showDropDown());
        actvCategory.setOnItemClickListener((parent, view, pos, id) ->
                tvExpiryHint.setText(safetyHint(String.valueOf(parent.getItemAtPosition(pos)))));

        // Sensible defaults: prepared now, best before today
        etPrepTime.setText(formatTime(Calendar.getInstance(), "Today, "));
        etExpiryDate.setText(formatDate(expiry));
        etPrepTime.setOnClickListener(v -> pickTime(Calendar.getInstance(), c -> etPrepTime.setText(formatTime(c, "Today, "))));
        etExpiryDate.setOnClickListener(v -> pickDate(etExpiryDate));
        etExpiryTime.setOnClickListener(v -> pickTime(expiry, c -> {
            expiry.set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY));
            expiry.set(Calendar.MINUTE, c.get(Calendar.MINUTE));
            expiryTimeSet = true;
            etExpiryTime.setText(formatTime(expiry, ""));
        }));

        // Diet tag chips toggle on/off
        bindDietChip(R.id.chip_veg, "Vegetarian");
        bindDietChip(R.id.chip_nut_free, "Nut-Free");
        bindDietChip(R.id.chip_halal, "Halal");

        // Delivery method cards behave like a radio group
        MaterialCardView cardPickup = findViewById(R.id.card_option_pickup);
        MaterialCardView cardSelf = findViewById(R.id.card_option_self);
        View.OnClickListener choosePickup = v -> selectDelivery(false, rbPickup, rbSelf, cardPickup, cardSelf);
        View.OnClickListener chooseSelf = v -> selectDelivery(true, rbPickup, rbSelf, cardPickup, cardSelf);
        cardPickup.setOnClickListener(choosePickup);
        rbPickup.setOnClickListener(choosePickup);
        cardSelf.setOnClickListener(chooseSelf);
        rbSelf.setOnClickListener(chooseSelf);
        selectDelivery(false, rbPickup, rbSelf, cardPickup, cardSelf);

        tvPickupName.setText("");
        tvPickupAddress.setText("Not set yet. Pin the exact spot so the volunteer finds you.");
        findViewById(R.id.tv_change_address).setOnClickListener(v -> openPicker());
        tvPickupAddress.setOnClickListener(v -> openPicker());

        FirebaseRepo.users().document(FirebaseRepo.uid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.getString("name") != null) donorName = doc.getString("name");
                    if (doc.getString("phone") != null) donorPhone = doc.getString("phone");
                    tvPickupName.setText(donorName);
                });

        btnPublish.setOnClickListener(v -> {
            String foodName = textOf(etFoodName);
            String quantity = textOf(etQuantity);
            if (TextUtils.isEmpty(foodName)) {
                etFoodName.setError("Food name required");
                etFoodName.requestFocus();
                return;
            }
            int qty;
            try {
                qty = Integer.parseInt(quantity);
            } catch (NumberFormatException e) {
                qty = 0;
            }
            if (qty <= 0) {
                etQuantity.setError("Enter a quantity above 0");
                etQuantity.requestFocus();
                return;
            }
            if (!expiryTimeSet) {
                Toast.makeText(this, "Pick the time the food is good until", Toast.LENGTH_SHORT).show();
                return;
            }
            if (expiry.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Expiry must be in the future", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pickupAddress == null) {
                Toast.makeText(this, "Set the pickup point on the map first", Toast.LENGTH_SHORT).show();
                return;
            }

            String notes = textOf(etNotes);
            if (!dietTags.isEmpty()) {
                String tags = TextUtils.join(", ", dietTags);
                notes = notes.isEmpty() ? tags : tags + ". " + notes;
            }

            Donation d = new Donation();
            d.donorId = FirebaseRepo.uid();
            d.donorName = donorName;
            d.donorPhone = donorPhone;
            d.foodName = foodName;
            d.category = actvCategory.getText().toString();
            d.quantity = String.valueOf(qty);
            d.unit = actvUnit.getText().toString();
            d.prepTime = textOf(etPrepTime);
            d.expiryDate = textOf(etExpiryDate);
            d.expiryTime = textOf(etExpiryTime);
            d.expiresAt = new Timestamp(expiry.getTime());
            d.dietaryNotes = notes;
            d.deliveryMethod = rbSelf.isChecked() ? "Self Delivery" : "NGO Pickup";
            d.pickupAddress = pickupAddress;
            d.latitude = latitude;
            d.longitude = longitude;
            d.status = Donation.STATUS_AVAILABLE;
            d.createdAt = Timestamp.now();

            btnPublish.setEnabled(false);
            btnPublish.setText("Publishing…");
            FirebaseRepo.donations().add(d)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Donation published!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnPublish.setEnabled(true);
                        btnPublish.setText(R.string.publish_donation);
                        Toast.makeText(this, "Publishing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void openPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        if (pickupAddress != null) {
            intent.putExtra(LocationPickerActivity.EXTRA_LAT, latitude);
            intent.putExtra(LocationPickerActivity.EXTRA_LNG, longitude);
        }
        locationPicker.launch(intent);
    }

    /** Delivery method cards behave like a radio group, with the border following the choice. */
    private void selectDelivery(boolean self, RadioButton rbPickup, RadioButton rbSelf,
                                MaterialCardView cardPickup, MaterialCardView cardSelf) {
        rbPickup.setChecked(!self);
        rbSelf.setChecked(self);
        float dp = getResources().getDisplayMetrics().density;
        int primary = ContextCompat.getColor(this, R.color.accent);
        int divider = ContextCompat.getColor(this, R.color.line);
        cardPickup.setStrokeColor(self ? divider : primary);
        cardPickup.setStrokeWidth((int) (dp * (self ? 1 : 2)));
        cardSelf.setStrokeColor(self ? primary : divider);
        cardSelf.setStrokeWidth((int) (dp * (self ? 2 : 1)));
    }

    private void bindDietChip(int id, String tag) {
        TextView chip = findViewById(id);
        Ui.styleChip(chip, false);
        chip.setText("+ " + tag);
        chip.setOnClickListener(v -> {
            boolean selected = !dietTags.contains(tag);
            if (selected) dietTags.add(tag); else dietTags.remove(tag);
            Ui.styleChip(chip, selected);
            chip.setText(selected ? tag : "+ " + tag);
        });
    }

    private static String safetyHint(String category) {
        switch (category) {
            case "Cooked Meals": return "Cooked meals must be consumed within 6 hours for food safety.";
            case "Dairy": return "Keep dairy refrigerated; set expiry within 24 hours if unrefrigerated.";
            case "Bakery": return "Bakery items are best within 24–48 hours.";
            case "Raw Produce": return "Check produce for freshness before listing.";
            default: return "Use the printed best-before date for packaged items.";
        }
    }

    private interface TimeCallback {
        void onTime(Calendar c);
    }

    private void pickTime(Calendar initial, TimeCallback cb) {
        new TimePickerDialog(this, (view, hour, minute) -> {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            cb.onTime(c);
        }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show();
    }

    private void pickDate(TextInputEditText target) {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            expiry.set(year, month, day);
            target.setText(formatDate(expiry));
        }, expiry.get(Calendar.YEAR), expiry.get(Calendar.MONTH), expiry.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private static String formatTime(Calendar c, String prefix) {
        return prefix + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.getTime());
    }

    private static String formatDate(Calendar c) {
        Calendar today = Calendar.getInstance();
        boolean isToday = c.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        String date = new SimpleDateFormat("MMM d", Locale.getDefault()).format(c.getTime());
        return isToday ? "Today, " + date : date;
    }

    private static String textOf(TextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
