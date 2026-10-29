package com.example.serve_n_help;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Porter delivery integration. {@link Api} mirrors Porter's partner API contract
 * (Get Quote, Create Order, Track Order, Cancel Order). Until Porter approves our API access,
 * {@link #api()} returns {@link PorterSandbox}, which plays the part of Porter's servers;
 * swapping in a real HTTP client only means implementing the same interface.
 */
final class Porter {

    /** Order states, named as in Porter's API. */
    static final String STATUS_OPEN = "open";           // looking for a driver
    static final String STATUS_ACCEPTED = "accepted";   // driver assigned, heading to pickup
    static final String STATUS_LIVE = "live";           // food on board, heading to drop
    static final String STATUS_ENDED = "ended";         // delivered
    static final String STATUS_CANCELLED = "cancelled";

    /** Porter's API currently books two-wheelers only, which carry about 20 kg. */
    static final double MAX_LOAD_KG = 20;

    private static Api instance;

    private Porter() {
    }

    static Api api() {
        if (instance == null) instance = new PorterSandbox();
        return instance;
    }

    interface Api {
        /** Fare and pickup ETA between two points; fails if either point is unserviceable. */
        Task<Quote> getQuote(LatLng pickup, LatLng drop);

        /** Books a two-wheeler. Returns the Porter order id. */
        Task<String> createOrder(OrderRequest request);

        Task<Tracking> trackOrder(String orderId);

        /** Only possible until the driver has the food on board. */
        Task<Void> cancelOrder(String orderId, String reason);
    }

    static final class Quote {
        int fare;
        double distanceKm;
        int pickupEtaSeconds;
        String vehicle = "2 Wheeler";
    }

    static final class OrderRequest {
        String requestId;      // our donation id, so Porter can de-duplicate retries
        LatLng pickup;
        String pickupName;
        String pickupAddress;
        LatLng drop;
        String dropName;
        String dropAddress;
    }

    static final class Tracking {
        String orderId;
        String status;
        boolean atPickup;
        String driverName;
        String vehicleNumber;
        String vehicleModel;
        LatLng driver;
        LatLng pickup;
        LatLng drop;
        /** Seconds until the next milestone (driver at pickup, or delivery). */
        int etaSeconds;
        int fare;
        double distanceKm;

        boolean cancellable() {
            return STATUS_OPEN.equals(status) || STATUS_ACCEPTED.equals(status);
        }
    }

    /** One-line description of where the order is, for the details card and tracking screen. */
    static String headline(Tracking t) {
        switch (t.status) {
            case STATUS_OPEN:
                return "Finding a Porter driver near the pickup…";
            case STATUS_ACCEPTED:
                return t.atPickup ? t.driverName + " has reached the pickup point"
                        : t.driverName + " is heading to the pickup · " + eta(t.etaSeconds);
            case STATUS_LIVE:
                return "Food on board, heading to the NGO · " + eta(t.etaSeconds);
            case STATUS_ENDED:
                return "Delivered by " + t.driverName;
            default:
                return "This Porter booking was cancelled";
        }
    }

    static String eta(int seconds) {
        if (seconds < 60) return Math.max(seconds, 1) + " s away";
        return String.format(Locale.getDefault(), "%d min away", (seconds + 30) / 60);
    }

    static String rupees(int amount) {
        return "₹" + amount;
    }

    /**
     * Porter, not the NGO, moves the food once a bike is booked, so the donation follows the
     * order: on board = picked up, ended = delivered. Whichever phone sees the change first
     * writes it; the update is the same from either side.
     */
    static void syncDonation(Donation d, Tracking t) {
        String target = null;
        String timeField = null;
        if (STATUS_ENDED.equals(t.status) && !Donation.STATUS_DELIVERED.equals(d.status)) {
            target = Donation.STATUS_DELIVERED;
            timeField = "deliveredAt";
        } else if (STATUS_LIVE.equals(t.status) && Donation.STATUS_ACCEPTED.equals(d.status)) {
            target = Donation.STATUS_PICKED_UP;
            timeField = "pickedUpAt";
        }
        if (target == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("status", target);
        update.put(timeField, FieldValue.serverTimestamp());
        // A delivery that skipped "picked up" (nobody had the app open) still gets both times
        if (Donation.STATUS_DELIVERED.equals(target) && d.pickedUpAt == null) {
            update.put("pickedUpAt", FieldValue.serverTimestamp());
        }
        FirebaseRepo.donations().document(d.id).update(update);
        String finalTarget = target;
        FirebaseRepo.pickupRequests().whereEqualTo("donationId", d.id).get()
                .addOnSuccessListener(snap -> snap.getDocuments()
                        .forEach(doc -> doc.getReference().update("status", finalTarget)));
    }
}
