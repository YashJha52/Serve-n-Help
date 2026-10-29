package com.example.serve_n_help;

import android.location.Location;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Stand-in for Porter's servers while API access is pending approval, in the spirit of
 * Porter's own UAT simulator. Orders live in the "porterSim" collection; a driver is
 * "assigned" and moves along pickup and drop on a fixed timeline that runs faster than real
 * life so a full trip fits in a demo (about 3 minutes). Every phone derives the same state
 * from the booking time, so the donor and NGO see the bike in the same place.
 */
final class PorterSandbox implements Porter.Api {

    // Trip timeline in seconds (demo speed)
    private static final int SEARCHING = 12;
    private static final int TO_PICKUP = 45;
    private static final int AT_PICKUP = 20;
    private static final int MIN_TO_DROP = 40;
    private static final int MAX_TO_DROP = 120;

    /** Roads are longer than the straight line between two points. */
    private static final double ROAD_FACTOR = 1.3;
    private static final double MAX_SERVICE_KM = 30;

    private static final String[] DRIVERS = {
            "Ramesh Yadav", "Suresh Patil", "Imran Shaikh", "Manoj Kumar", "Prakash Gowda",
            "Arjun Nair", "Vikram Singh", "Sandeep More", "Rahul Pawar", "Naveen Reddy"};
    private static final String[] MODELS = {
            "Honda Activa", "TVS Jupiter", "Hero Splendor", "Bajaj Pulsar", "Suzuki Access"};

    private static CollectionReference orders() {
        return FirebaseRepo.db().collection("porterSim");
    }

    @Override
    public Task<Porter.Quote> getQuote(LatLng pickup, LatLng drop) {
        double km = roadKm(pickup, drop);
        if (km > MAX_SERVICE_KM) {
            return Tasks.forException(new IllegalArgumentException(
                    "Porter can't deliver this far (" + Math.round(km) + " km). Bike deliveries go up to "
                            + (int) MAX_SERVICE_KM + " km."));
        }
        Porter.Quote q = new Porter.Quote();
        q.distanceKm = km;
        q.fare = fare(km);
        q.pickupEtaSeconds = SEARCHING + TO_PICKUP;
        return Tasks.forResult(q);
    }

    @Override
    public Task<String> createOrder(Porter.OrderRequest r) {
        Random random = new Random();
        double km = roadKm(r.pickup, r.drop);
        // The driver starts about 1.2 km from the pickup, in a random direction
        double bearing = random.nextDouble() * 2 * Math.PI;
        double startLat = r.pickup.latitude + 0.011 * Math.cos(bearing);
        double startLng = r.pickup.longitude + 0.011 * Math.sin(bearing) / Math.cos(Math.toRadians(r.pickup.latitude));

        Map<String, Object> order = new HashMap<>();
        order.put("requestId", r.requestId);
        order.put("pickupLat", r.pickup.latitude);
        order.put("pickupLng", r.pickup.longitude);
        order.put("pickupName", r.pickupName);
        order.put("pickupAddress", r.pickupAddress);
        order.put("dropLat", r.drop.latitude);
        order.put("dropLng", r.drop.longitude);
        order.put("dropName", r.dropName);
        order.put("dropAddress", r.dropAddress);
        order.put("driverStartLat", startLat);
        order.put("driverStartLng", startLng);
        order.put("driverName", DRIVERS[random.nextInt(DRIVERS.length)]);
        order.put("vehicleModel", MODELS[random.nextInt(MODELS.length)]);
        order.put("vehicleNumber", plate(r.pickup, random));
        order.put("distanceKm", km);
        order.put("fare", fare(km));
        order.put("bookedAt", FieldValue.serverTimestamp());

        DocumentReference ref = orders().document();
        return ref.set(order).continueWith(t -> {
            if (!t.isSuccessful()) throw t.getException();
            return ref.getId();
        });
    }

    @Override
    public Task<Porter.Tracking> trackOrder(String orderId) {
        return orders().document(orderId).get().continueWith(t -> {
            DocumentSnapshot doc = t.getResult();
            if (doc == null || !doc.exists()) throw new IllegalStateException("Porter order not found");
            return track(doc);
        });
    }

    @Override
    public Task<Void> cancelOrder(String orderId, String reason) {
        DocumentReference ref = orders().document(orderId);
        return ref.get().continueWithTask(t -> {
            Porter.Tracking tracking = track(t.getResult());
            if (!tracking.cancellable()) {
                return Tasks.forException(new IllegalStateException(
                        "The driver already has the food on board, so this order can't be cancelled."));
            }
            Map<String, Object> update = new HashMap<>();
            update.put("cancelledAt", FieldValue.serverTimestamp());
            update.put("cancelReason", reason);
            return ref.update(update);
        });
    }

    /** Works out where the trip is from how long ago it was booked. */
    private static Porter.Tracking track(DocumentSnapshot doc) {
        Porter.Tracking t = new Porter.Tracking();
        t.orderId = doc.getId();
        t.driverName = doc.getString("driverName");
        t.vehicleModel = doc.getString("vehicleModel");
        t.vehicleNumber = doc.getString("vehicleNumber");
        t.pickup = new LatLng(num(doc, "pickupLat"), num(doc, "pickupLng"));
        t.drop = new LatLng(num(doc, "dropLat"), num(doc, "dropLng"));
        LatLng start = new LatLng(num(doc, "driverStartLat"), num(doc, "driverStartLng"));
        t.distanceKm = num(doc, "distanceKm");
        Long fare = doc.getLong("fare");
        t.fare = fare == null ? 0 : fare.intValue();

        // Just after booking the server time is still pending; use the local estimate
        Timestamp bookedAt = doc.getTimestamp("bookedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        long booked = bookedAt == null ? System.currentTimeMillis() : bookedAt.toDate().getTime();
        double s = Math.max(0, (System.currentTimeMillis() - booked) / 1000.0);

        int toDrop = (int) Math.max(MIN_TO_DROP, Math.min(MAX_TO_DROP, t.distanceKm * 12));
        double assigned = SEARCHING;
        double reached = assigned + TO_PICKUP;
        double leaves = reached + AT_PICKUP;
        double arrives = leaves + toDrop;

        if (doc.get("cancelledAt") != null) {
            t.status = Porter.STATUS_CANCELLED;
            t.driver = start;
        } else if (s < assigned) {
            t.status = Porter.STATUS_OPEN;
            t.driver = start;
            t.etaSeconds = (int) (reached - s);
        } else if (s < reached) {
            t.status = Porter.STATUS_ACCEPTED;
            t.driver = lerp(start, t.pickup, (s - assigned) / TO_PICKUP);
            t.etaSeconds = (int) (reached - s);
        } else if (s < leaves) {
            t.status = Porter.STATUS_ACCEPTED;
            t.atPickup = true;
            t.driver = t.pickup;
        } else if (s < arrives) {
            t.status = Porter.STATUS_LIVE;
            t.driver = lerp(t.pickup, t.drop, (s - leaves) / toDrop);
            t.etaSeconds = (int) (arrives - s);
        } else {
            t.status = Porter.STATUS_ENDED;
            t.driver = t.drop;
        }
        return t;
    }

    /** Roughly Porter's two-wheeler pricing: a base fare plus a per-km charge. */
    private static int fare(double km) {
        return (int) Math.max(49, Math.round(30 + 11 * km));
    }

    private static double roadKm(LatLng a, LatLng b) {
        float[] metres = new float[1];
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, metres);
        return Math.round(metres[0] * ROAD_FACTOR / 100.0) / 10.0;
    }

    private static LatLng lerp(LatLng a, LatLng b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f);
    }

    /** A plausible number plate for the city: Karnataka around Bengaluru, Maharashtra otherwise. */
    private static String plate(LatLng near, Random r) {
        boolean karnataka = near.latitude > 11.5 && near.latitude < 15.5 && near.longitude > 74 && near.longitude < 78.6;
        return String.format(Locale.US, "%s %02d %c%c %04d", karnataka ? "KA" : "MH", 1 + r.nextInt(50),
                (char) ('A' + r.nextInt(26)), (char) ('A' + r.nextInt(26)), 1000 + r.nextInt(9000));
    }

    private static double num(DocumentSnapshot doc, String field) {
        Double v = doc.getDouble(field);
        return v == null ? 0 : v;
    }
}
