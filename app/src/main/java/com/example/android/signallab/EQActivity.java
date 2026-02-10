package com.example.android.signallab;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

public class EQActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO_FILE = 2001;

    private AudioEngine audioEngine;

    private SeekBar bassBar, midBar, trebleBar;
    private Button playButton, stopButton, selectFileButton;

    private Uri selectedAudioUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eq);

        // Connect to UI later, I've only done bassbar. The UI is ugly so I gave up for now
        bassBar = findViewById(R.id.bassBar);
        //midBar = findViewById(R.id.midBar);
        //trebleBar = findViewById(R.id.trebleBar);


           //Play, select, stop buttons are not ready.
        //playButton = findViewById(R.id.play);
        //stopButton = findViewById(R.id.stop);
        //selectFileButton = findViewById(R.id.selectFile);

    }
}
