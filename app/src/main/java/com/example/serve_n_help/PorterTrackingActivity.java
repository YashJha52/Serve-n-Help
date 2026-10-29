package com.example.serve_n_help;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Arrays;

/** Live map of a Porter trip: the bike moves from pickup to the NGO as the order progresses. */
public class PorterTrackingActivity extends BaseActivity {

    private static final String EXTRA_DONATION_ID = "donation_id";
    private static final long POLL_MS = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refresh;
    private ListenerRegistration listener;
    private Donation donation;
    private GoogleMap map;
    private Marker bike;
    private boolean routeDrawn;

    static void open(Context context, String donationId) {
        context.startActivity(new Intent(context, PorterTrackingActivity.class).putExtra(EXTRA_DONATION_ID, donationId));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_porter_tracking);
        String id = getIntent().getStringExtra(EXTRA_DONATION_ID);
        if (id == null) {
            finish();
            return;
        }
        findViewById(R.id.btn_back_tracking).setOnClickListener(v -> finish());
        findViewById(R.id.layout_porter_actions).setVisibility(View.GONE);

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_porter);
        fragment.getMapAsync(m -> {
            map = m;
            map.getUiSettings().setMapToolbarEnabled(false);
            View card = findViewById(R.id.frame_porter_card);
            card.post(() -> map.setPadding(0, 0, 0, card.getHeight()));
            refresh();
        });

        listener = FirebaseRepo.donations().document(id).addSnapshotListener((snap, e) -> {
            if (snap == null || !snap.exists()) {
                finish();
                return;
            }
            donation = snap.toObject(Donation.class);
            refresh();
        });
    }

    private void refresh() {
        handler.removeCallbacks(poll);
        if (donation == null || donation.porterOrderId == null || map == null) return;
        Porter.api().trackOrder(donation.porterOrderId).addOnSuccessListener(t -> {
            if (isFinishing()) return;
            PorterCard.bind(this, t, donation.dropAddress);
            draw(t);
            Porter.syncDonation(donation, t);
            boolean finished = Porter.STATUS_ENDED.equals(t.status) || Porter.STATUS_CANCELLED.equals(t.status);
            // The real API pushes webhooks; the sandbox is polled while this screen is open
            if (!finished) handler.postDelayed(poll, POLL_MS);
        });
    }

    private void draw(Porter.Tracking t) {
        if (!routeDrawn) {
            routeDrawn = true;
            map.addMarker(new MarkerOptions().position(t.pickup).title("Pickup")
                    .snippet(donation.donorName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            map.addMarker(new MarkerOptions().position(t.drop).title("Drop")
                    .snippet(donation.ngoName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            map.addPolyline(new PolylineOptions().add(t.pickup, t.drop)
                    .color(ContextCompat.getColor(this, R.color.accent)).width(10f)
                    .pattern(Arrays.asList(new Dash(30f), new Gap(20f))));
            LatLngBounds bounds = LatLngBounds.builder().include(t.pickup).include(t.drop).include(t.driver).build();
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 160));
        }
        if (Porter.STATUS_CANCELLED.equals(t.status)) {
            if (bike != null) bike.remove();
            bike = null;
            return;
        }
        if (bike == null) {
            bike = map.addMarker(new MarkerOptions().position(t.driver).anchor(0.5f, 0.5f)
                    .title(t.driverName).icon(bikeIcon()).zIndex(1f));
        } else {
            bike.setPosition(t.driver);
        }
    }

    /** Accent circle with a white moped, so the bike stands out from the pickup and drop pins. */
    private BitmapDescriptor bikeIcon() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(this, R.color.accent));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_moped).mutate();
        icon.setTint(ContextCompat.getColor(this, R.color.on_accent));
        int pad = size / 5;
        icon.setBounds(pad, pad, size - pad, size - pad);
        icon.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(poll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
