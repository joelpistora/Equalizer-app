package com.example.android.signallab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkAndRequestPermissions()) {
            setupButtons();
        }
    }

    private void setupButtons() {
        Button accButton = findViewById(R.id.mAccelerometerButton);
        Button recButton = findViewById(R.id.mRecorderButton);
        Button vidButton = findViewById(R.id.mVideoButton);

        accButton.setOnClickListener(v -> {
            Intent startAccActivityIntent = new Intent(MainActivity.this,
                    AccelerometerActivity.class);
            startActivity(startAccActivityIntent);
        });

        recButton.setOnClickListener(v -> {
            Intent startRecordActivityIntent = new Intent(MainActivity.this,
                    RecorderActivity.class);
            startActivity(startRecordActivityIntent);
        });

        vidButton.setOnClickListener(v -> {
            Intent startVideoActivityIntent = new Intent(MainActivity.this,
                    VideoActivity.class);
            startActivity(startVideoActivityIntent);
        });
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                setupButtons();
            } else {
                // Permissions not granted, disable buttons
                findViewById(R.id.mAccelerometerButton).setEnabled(false);
                findViewById(R.id.mRecorderButton).setEnabled(false);
                findViewById(R.id.mVideoButton).setEnabled(false);
            }
        }
    }
}
