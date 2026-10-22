package com.example.serve_n_help;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

/** Central place for Firebase references and Firestore collection names. */
public final class FirebaseRepo {

    public static final String USERS = "users";
    public static final String DONATIONS = "donations";
    public static final String PICKUP_REQUESTS = "pickupRequests";

    public static final String ROLE_DONOR = "donor";
    public static final String ROLE_NGO = "ngo";

    private FirebaseRepo() {
    }

    public static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    public static CollectionReference users() {
        return db().collection(USERS);
    }

    public static CollectionReference donations() {
        return db().collection(DONATIONS);
    }

    public static CollectionReference pickupRequests() {
        return db().collection(PICKUP_REQUESTS);
    }

    public static String uid() {
        return auth().getCurrentUser() == null ? null : auth().getCurrentUser().getUid();
    }
}
