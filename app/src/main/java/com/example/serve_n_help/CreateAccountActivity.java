package com.example.serve_n_help;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class CreateAccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        // Find views
        ImageButton btnBack = findViewById(R.id.btn_back_create_account);
        TextView tvSignIn = findViewById(R.id.tv_navigate_signin);
        MaterialButton btnSubmit = findViewById(R.id.btn_create_account_submit);

        // Go back to login
        btnBack.setOnClickListener(v -> finish());
        tvSignIn.setOnClickListener(v -> finish());

        // Create account and go to Dashboard
        btnSubmit.setOnClickListener(v -> {
            Intent intent = new Intent(CreateAccountActivity.this, RestaurantDashboardActivity.class);
            startActivity(intent);
            finish(); // Close registration screen
        });
    }
}
