package com.example.serve_n_help;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live camera (CameraX) feeding frames to ML Kit's on-device barcode model. Returns the first
 * QR it reads in {@link #EXTRA_PAYLOAD}; the caller checks it against the donation. Returns
 * {@link #EXTRA_MANUAL} instead when the user would rather type the code.
 */
public class HandoverScanActivity extends BaseActivity {

    static final String EXTRA_PAYLOAD = "payload";
    static final String EXTRA_MANUAL = "manual";
    private static final String TAG = "HandoverScan";

    private final AtomicBoolean handled = new AtomicBoolean(false);
    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private Camera camera;
    private boolean torchOn;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showPermissionNeeded();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handover_scan);
        // Camera screen is dark whatever the app theme, so the bars go dark with light icons
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);

        findViewById(R.id.btn_back_scan).setOnClickListener(v -> finish());
        findViewById(R.id.btn_scan_manual).setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_MANUAL, true));
            finish();
        });
        findViewById(R.id.btn_scan_torch).setOnClickListener(v -> toggleTorch());

        analysisExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build());

        if (hasCameraPermission()) startCamera();
        else permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        findViewById(R.id.btn_scan_permission).setVisibility(View.GONE);
        findViewById(R.id.view_scan_frame).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_scan_title)).setText("Point at the donor's QR code");
        ((TextView) findViewById(R.id.tv_scan_hint)).setText(
                "The donor opens this donation and taps Show pickup code. It scans on its own, no button needed.");

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                PreviewView previewView = findViewById(R.id.preview_scan);
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Only the newest frame is analysed; older ones are dropped so scanning never lags
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyse);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                if (camera.getCameraInfo().hasFlashUnit()) findViewById(R.id.btn_scan_torch).setVisibility(View.VISIBLE);
            } catch (Exception e) {
                Log.e(TAG, "Camera failed to start", e);
                ((TextView) findViewById(R.id.tv_scan_title)).setText("Camera unavailable");
                ((TextView) findViewById(R.id.tv_scan_hint)).setText("Type the code the donor reads out instead.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyse(ImageProxy frame) {
        if (frame.getImage() == null || handled.get()) {
            frame.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(frame.getImage(), frame.getImageInfo().getRotationDegrees());
        scanner.process(image)
                .addOnSuccessListener(codes -> {
                    for (Barcode code : codes) {
                        String raw = code.getRawValue();
                        if (raw != null && handled.compareAndSet(false, true)) {
                            deliver(raw);
                            return;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Frame not processed", e))
                .addOnCompleteListener(t -> frame.close());
    }

    private void deliver(String payload) {
        runOnUiThread(() -> {
            findViewById(R.id.preview_scan).performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PAYLOAD, payload));
            finish();
        });
    }

    private void toggleTorch() {
        if (camera == null) return;
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        ImageButton torch = findViewById(R.id.btn_scan_torch);
        torch.setContentDescription(torchOn ? "Turn off torch" : "Turn on torch");
        torch.setAlpha(torchOn ? 1f : 0.7f);
    }

    /** Explains why the camera is needed and offers a way out instead of a dead screen. */
    private void showPermissionNeeded() {
        findViewById(R.id.view_scan_frame).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.tv_scan_title)).setText("Camera access is off");
        ((TextView) findViewById(R.id.tv_scan_hint)).setText(
                "Serve-N-Help only uses the camera to read the donor's pickup code. You can also type the code.");
        MaterialButton allow = findViewById(R.id.btn_scan_permission);
        allow.setVisibility(View.VISIBLE);
        allow.setOnClickListener(v -> {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionLauncher.launch(Manifest.permission.CAMERA);
            } else {
                // "Don't ask again" was chosen, so only the system settings can turn it back on
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from system settings with the permission now granted
        if (camera == null && hasCameraPermission() && findViewById(R.id.btn_scan_permission).getVisibility() == View.VISIBLE) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) scanner.close();
        if (analysisExecutor != null) analysisExecutor.shutdown();
    }
}
