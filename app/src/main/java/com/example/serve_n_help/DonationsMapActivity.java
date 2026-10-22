package com.example.serve_n_help;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

/** Shows every available donation as a marker so NGOs/volunteers can find food nearby. */
public class DonationsMapActivity extends BaseActivity {

    public static final String EXTRA_FOCUS_ID = "focus_id";

    private GoogleMap map;
    private ListenerRegistration listener;
    private TextView tvCount;
    private boolean cameraFitted;
    private String focusId;
    private LatLng myLocation;
    private final List<Donation> available = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> enableMyLocation());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donations_map);

        tvCount = findViewById(R.id.tv_map_count);
        focusId = getIntent().getStringExtra(EXTRA_FOCUS_ID);
        findViewById(R.id.btn_back_map).setOnClickListener(v -> finish());

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_donations);
        fragment.getMapAsync(googleMap -> {
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LocationPickerActivity.DEFAULT_LOCATION, 12f));
            map.setOnInfoWindowClickListener(marker -> DonationDetailsActivity.open(this, (String) marker.getTag()));

            if (hasLocationPermission()) {
                enableMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
            listenForDonations();
        });
    }

    private void listenForDonations() {
        listener = FirebaseRepo.donations()
                .whereEqualTo("status", Donation.STATUS_AVAILABLE)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null || map == null) return;
                    map.clear();
                    LatLngBounds.Builder bounds = LatLngBounds.builder();
                    int count = 0;
                    available.clear();
                    for (Donation d : snap.toObjects(Donation.class)) {
                        if ((d.latitude == 0 && d.longitude == 0) || d.isExpired()) continue;
                        available.add(d);
                        LatLng pos = new LatLng(d.latitude, d.longitude);
                        Marker marker = map.addMarker(new MarkerOptions()
                                .position(pos)
                                .title(d.foodName + " • " + d.quantity + " " + d.unit)
                                .snippet(Ui.expiryText(d) + " — tap for details")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                        if (marker != null) marker.setTag(d.id);
                        bounds.include(pos);
                        count++;
                    }
                    updateCount();
                    if (focusId != null) {
                        showFocused();
                        return;
                    }
                    // With a known location the camera stays on the user; otherwise show every donation
                    if (count > 0 && !cameraFitted && myLocation == null) {
                        cameraFitted = true;
                        if (count == 1) {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.build().getCenter(), 15f));
                        } else {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120));
                        }
                    }
                });
    }

    /** "5 available • 2 within 10 km" once the user's location is known. */
    private void updateCount() {
        int total = available.size();
        String text = total == 0 ? "No donations available" : total + (total == 1 ? " donation available" : " donations available");
        if (myLocation != null && total > 0) {
            int near = 0;
            float[] dist = new float[1];
            for (Donation d : available) {
                Location.distanceBetween(myLocation.latitude, myLocation.longitude, d.latitude, d.longitude, dist);
                if (dist[0] <= 10_000) near++;
            }
            text = total + " available • " + near + " within 10 km";
        }
        tvCount.setText(text);
    }

    /** Centres on one donation (which may already be accepted, so it is fetched directly). */
    private void showFocused() {
        FirebaseRepo.donations().document(focusId).get().addOnSuccessListener(doc -> {
            Donation d = doc.toObject(Donation.class);
            if (d == null || map == null) return;
            LatLng pos = new LatLng(d.latitude, d.longitude);
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(d.foodName)
                    .snippet(d.pickupAddress)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            if (marker != null) {
                marker.setTag(d.id);
                marker.showInfoWindow();
            }
            cameraFitted = true;
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (map == null || !hasLocationPermission()) return;
        map.setMyLocationEnabled(true);
        LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) return;
                    myLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    updateCount();
                    if (focusId == null) {
                        cameraFitted = true;
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12f));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
