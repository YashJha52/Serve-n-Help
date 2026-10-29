package com.example.serve_n_help;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Fills the shared card_porter layout from a tracking response. */
final class PorterCard {

    private PorterCard() {
    }

    static void bind(Activity a, Porter.Tracking t, String dropAddress) {
        ((TextView) a.findViewById(R.id.tv_porter_status)).setText(Porter.headline(t));

        boolean hasDriver = !Porter.STATUS_OPEN.equals(t.status) && !Porter.STATUS_CANCELLED.equals(t.status);
        a.findViewById(R.id.layout_porter_driver).setVisibility(hasDriver ? View.VISIBLE : View.GONE);
        ((TextView) a.findViewById(R.id.tv_porter_driver_avatar)).setText(Ui.initials(t.driverName));
        ((TextView) a.findViewById(R.id.tv_porter_driver)).setText(t.driverName);
        ((TextView) a.findViewById(R.id.tv_porter_vehicle)).setText(t.vehicleModel + " · " + t.vehicleNumber);
        // Porter masks driver numbers, and sandbox drivers have none to call
        a.findViewById(R.id.btn_porter_call).setOnClickListener(v -> Toast.makeText(a,
                "Calls go through Porter's masked line. Not available in the sandbox.", Toast.LENGTH_SHORT).show());

        ((TextView) a.findViewById(R.id.tv_porter_trip)).setText(String.format(Locale.getDefault(),
                "2 Wheeler · %.1f km · %s · Order %s", t.distanceKm, Porter.rupees(t.fare),
                t.orderId.substring(0, Math.min(6, t.orderId.length())).toUpperCase(Locale.US)));
        ((TextView) a.findViewById(R.id.tv_porter_drop)).setText("Drop: " + (dropAddress == null ? "NGO" : dropAddress));
    }
}
