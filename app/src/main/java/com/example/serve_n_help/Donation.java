package com.example.serve_n_help;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

/** Firestore model for a document in the "donations" collection. */
public class Donation {

    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_PICKED_UP = "picked_up";
    public static final String STATUS_DELIVERED = "delivered";

    @DocumentId
    public String id;

    public String donorId;
    public String donorName;
    public String donorPhone;
    public String foodName;
    public String category;
    public String quantity;
    public String unit;
    public String prepTime;
    public String expiryDate;
    public String expiryTime;
    public Timestamp expiresAt;
    public String dietaryNotes;
    public String deliveryMethod;
    public String pickupAddress;
    public double latitude;
    public double longitude;
    public String imageUrl;
    public String status = STATUS_AVAILABLE;
    public String ngoId;
    public String ngoName;
    public String ngoPhone;
    public Timestamp createdAt;
    public Timestamp acceptedAt;
    public Timestamp pickedUpAt;
    public Timestamp deliveredAt;

    public Donation() {
        // Required by Firestore
    }

    @Exclude
    public String shortId() {
        return id == null ? "" : "#SNH-" + id.substring(0, Math.min(4, id.length())).toUpperCase();
    }

    /** True once the expiry time has passed and nobody has claimed the food. */
    @Exclude
    public boolean isExpired() {
        return STATUS_AVAILABLE.equals(status) && expiresAt != null
                && expiresAt.toDate().getTime() < System.currentTimeMillis();
    }

    /** 0 = published, 1 = accepted, 2 = picked up, 3 = delivered. */
    @Exclude
    public int stepIndex() {
        if (STATUS_ACCEPTED.equals(status)) return 1;
        if (STATUS_PICKED_UP.equals(status)) return 2;
        if (STATUS_DELIVERED.equals(status)) return 3;
        return 0;
    }

    /** Rough number of people fed, used for the impact numbers. */
    @Exclude
    public int mealsEstimate() {
        int qty = quantityNumber();
        if (unit == null) return qty;
        String u = unit.toLowerCase();
        if (u.startsWith("kg")) return qty * 2;
        if (u.startsWith("litre")) return qty * 2;
        return qty;
    }

    /** Approximate weight in kg (about 0.5 kg per portion). */
    @Exclude
    public double weightEstimateKg() {
        int qty = quantityNumber();
        if (unit != null && unit.toLowerCase().startsWith("kg")) return qty;
        if (unit != null && unit.toLowerCase().startsWith("litre")) return qty;
        return qty * 0.5;
    }

    @Exclude
    public int quantityNumber() {
        try {
            return Integer.parseInt(quantity.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static String statusLabel(String status) {
        if (STATUS_ACCEPTED.equals(status)) return "Accepted";
        if (STATUS_PICKED_UP.equals(status)) return "Picked Up";
        if (STATUS_DELIVERED.equals(status)) return "Delivered";
        return "Available";
    }
}
