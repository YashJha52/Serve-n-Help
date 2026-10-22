package com.example.serve_n_help;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lets a donor drag the map under a fixed pin to choose the pickup location. */
public class LocationPickerActivity extends BaseActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_ADDRESS = "address";

    // Bengaluru, used until we know where the user is
    static final LatLng DEFAULT_LOCATION = new LatLng(12.9716, 77.5946);

    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
    private GoogleMap map;
    private TextView tvAddress;
    private String currentAddress;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (hasLocationPermission()) {
                    moveToMyLocation();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        tvAddress = findViewById(R.id.tv_picker_address);
        findViewById(R.id.btn_back_picker).setOnClickListener(v -> finish());
        findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                moveToMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        findViewById(R.id.btn_confirm_location).setOnClickListener(v -> confirm());

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_picker);
        fragment.getMapAsync(googleMap -> {
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            // Keep the zoom buttons and Google logo above the bottom card
            View card = findViewById(R.id.card_picker);
            card.post(() -> map.setPadding(0, 0, 0, card.getHeight() + card.getPaddingBottom() + 48));

            Intent in = getIntent();
            boolean hasStart = in.hasExtra(EXTRA_LAT);
            LatLng start = hasStart
                    ? new LatLng(in.getDoubleExtra(EXTRA_LAT, 0), in.getDoubleExtra(EXTRA_LNG, 0))
                    : DEFAULT_LOCATION;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 16f));
            map.setOnCameraIdleListener(() -> reverseGeocode(map.getCameraPosition().target));

            if (!hasStart && hasLocationPermission()) moveToMyLocation();
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void moveToMyLocation() {
        if (map == null) return;
        map.setMyLocationEnabled(true);
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(), location.getLongitude()), 17f));
                    } else {
                        Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void reverseGeocode(LatLng target) {
        currentAddress = null;
        tvAddress.setText("Finding address…");
        geocodeExecutor.execute(() -> {
            String text;
            try {
                List<Address> results = new Geocoder(this, Locale.getDefault())
                        .getFromLocation(target.latitude, target.longitude, 1);
                text = results != null && !results.isEmpty()
                        ? results.get(0).getAddressLine(0)
                        : formatLatLng(target);
            } catch (Exception e) {
                text = formatLatLng(target);
            }
            String finalText = text;
            runOnUiThread(() -> {
                // Ignore stale results if the map moved again meanwhile
                if (map != null && map.getCameraPosition().target.equals(target)) {
                    currentAddress = finalText;
                    tvAddress.setText(finalText);
                }
            });
        });
    }

    private static String formatLatLng(LatLng p) {
        return String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude);
    }

    private void confirm() {
        if (map == null || currentAddress == null) {
            Toast.makeText(this, "Wait for the address to load", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng target = map.getCameraPosition().target;
        Intent data = new Intent()
                .putExtra(EXTRA_LAT, target.latitude)
                .putExtra(EXTRA_LNG, target.longitude)
                .putExtra(EXTRA_ADDRESS, currentAddress);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocodeExecutor.shutdownNow();
    }
}
