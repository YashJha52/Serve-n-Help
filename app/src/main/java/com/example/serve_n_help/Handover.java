package com.example.serve_n_help;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.firebase.firestore.DocumentReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * QR pickup handover. The donor's phone keeps a random one-time code in a private document
 * only the donor can read, and publishes just its SHA-256 hash on the donation. The NGO proves
 * it was physically there by scanning the code; the hash check runs both here and in the
 * Firestore rules, so the status can't be flipped to "picked up" without it.
 */
final class Handover {

    /** No 0/O or 1/I/L, so a code read aloud or typed by hand is unambiguous. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    private static final String QR_PREFIX = "SNH1:";

    private Handover() {
    }

    static DocumentReference privateDoc(String donationId) {
        return FirebaseRepo.donations().document(donationId).collection("private").document("handover");
    }

    static String newCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** Lower-case hex SHA-256 of "donationId:code"; salting with the id makes each hash unique. */
    static String hash(String donationId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((donationId + ":" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "ABCD-EFGH" for reading out loud. */
    static String pretty(String code) {
        return code.length() == CODE_LENGTH ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    /** Uppercases and strips spaces and dashes from a hand-typed code. */
    static String normalise(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "").toUpperCase(Locale.US);
    }

    static String qrPayload(String donationId, String code) {
        return QR_PREFIX + donationId + ":" + code;
    }

    /**
     * Splits a scanned payload into {donationId, code}, or returns null if the QR wasn't made by
     * Serve-N-Help (a menu, a payment code, a website).
     */
    static String[] parse(String payload) {
        if (payload == null || !payload.startsWith(QR_PREFIX)) return null;
        String[] parts = payload.substring(QR_PREFIX.length()).split(":");
        return parts.length == 2 ? parts : null;
    }

    /** Black-on-white QR (never themed: scanners need the contrast, even in dark mode). */
    static Bitmap qrBitmap(String payload, int sizePx) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix m = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) pixels[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565);
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }
}
