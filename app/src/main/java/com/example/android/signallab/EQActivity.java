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

import java.util.Arrays;

public class EQActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO_FILE = 2001;
    private static final String TAG = "EQEngine";
    private AudioEngine audioEngine;
    private VisualEngine visualEngine;

    private SeekBar bassBar, midBar, trebleBar;
    private Button playButton, stopButton, selectFileButton;
    private AudioTrack track;

    private Uri selectedAudioUri;
    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private SpectrumView spectrumView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eq);


        // Connect to UI later, I've only done bassbar. The UI is ugly so I gave up for now
        bassBar = findViewById(R.id.bassBar);
        midBar = findViewById(R.id.midBar);
        trebleBar = findViewById(R.id.trebleBar);

        audioEngine = AudioEngine.getInstance(this);
        visualEngine = VisualEngine.getInstance();

        spectrumView = findViewById(R.id.spectrumView);

        //Play, select, stop buttons are not ready.
        playButton = findViewById(R.id.play);
        stopButton = findViewById(R.id.stop);
        // TODO: Add go back to record button

        playButton.setOnClickListener(v -> audioEngine.startPlaybackLoop());
        stopButton.setOnClickListener(v -> audioEngine.stopPlaybackLoop());

        visualEngine.setSpectrumListener(spectrum -> {
            Log.d(TAG, "Received spectrum: " + Arrays.toString(spectrum));
            runOnUiThread(() -> {
                spectrumView.updateSpectrum(spectrum);
            });
        });
    }

    private void seekBarListener() {

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int idx = seekBar.getId();

                if (idx == R.id.bassBar) {
                    audioEngine.setBassGain(progress);
                    Log.d(TAG, "Bass gain: " + progress);

                } else if (idx == R.id.midBar) {
                    audioEngine.setMidGain(progress);

                } else if (idx == R.id.trebleBar) {
                    audioEngine.setTrebleGain(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        // Attach listener to all bars
        bassBar.setOnSeekBarChangeListener(listener);
        midBar.setOnSeekBarChangeListener(listener);
        trebleBar.setOnSeekBarChangeListener(listener);
    }

    private void resetBars(){

    }
}