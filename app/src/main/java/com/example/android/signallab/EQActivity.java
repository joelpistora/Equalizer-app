package com.example.android.signallab;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

public class EQActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO_FILE = 2001;
    private static final String TAG = "EQEngine";
    private AudioEngine audioEngine;

    private SeekBar bassBar, midBar, trebleBar;
    private Button playButton, stopButton, selectFileButton;
    private AudioTrack track;

    private Uri selectedAudioUri;
    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eq);


        // Connect to UI later, I've only done bassbar. The UI is ugly so I gave up for now
        bassBar = findViewById(R.id.bassBar);
        midBar = findViewById(R.id.midBar);
        trebleBar = findViewById(R.id.trebleBar);


        //Play, select, stop buttons are not ready.
        playButton = findViewById(R.id.play);
        stopButton = findViewById(R.id.stop);

        audioEngine = AudioEngine.getInstance(this);

        playButton.setOnClickListener(v -> audioEngine.startPlaybackLoop());
        stopButton.setOnClickListener(v -> audioEngine.stopPlaybackLoop());
    }

}