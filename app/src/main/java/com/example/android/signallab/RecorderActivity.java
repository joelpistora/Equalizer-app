package com.example.android.signallab;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.provider.MediaStore;

import android.widget.TextView;
import java.util.Locale;

public class RecorderActivity extends AppCompatActivity {
    private static final String TAG = "RecorderActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView recordingIndicator;



    private OutputStream recordingOutputStream;
    private AudioRecord record;
    private AudioTrack track;
    private volatile boolean isRecording = false;
    private Thread recordingThread;

    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 1024;
    private final byte[] inBuffer = new byte[BUFFER_SIZE];
    private final byte[] outBuffer = new byte[BUFFER_SIZE];
    private final short[] shortBuffer = new short[BUFFER_SIZE / 2];

    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE / 50; // 48000/50 = 960 samples
    private final short[] audioBuffer = new short[AUDIO_BUFFER_SIZE];
    private int audioBufferIndex = 0;
    private float volumeFactor = 1.0f;

    private static final double TARGET_RMS = 0.25;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 4.0;
    private static final double epsilon = 1e-9;

    private double currentScale = 1.0;

    private TextView audioStatusText;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);

        Button startButton = findViewById(R.id.record);
        Button stopButton = findViewById(R.id.stop);

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());

        recordingIndicator = findViewById(R.id.recordingIndicator);

        audioStatusText = findViewById(R.id.audioStatusText);

        checkAndRequestPermissions();
    }
    @Override
    protected void onResume() {
        super.onResume();
        checkAndRequestPermissions();
    }

    @Override
    protected void onPause() {
        stopRecording();
        releaseAudio();
        super.onPause();
    }
    private void checkAndRequestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            initializeAudio();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted — initializing audio");
                initializeAudio();
            } else {
                Log.e(TAG, "Permission denied");
            }
        }
    }
    private void initializeAudio() {
        if (record != null && track != null) return;

        try {
            // Setup Input Audio
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(minBuffer)
                    .build();

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize");
                record = null;
                return;
            }

            // Setup Output Audio
            int trackBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(trackBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialize");
                track = null;
                return;
            }
            Log.d(TAG, "Audio initialized successfully");

        } catch (SecurityException se) {
            Log.e(TAG, "Permission missing: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing audio: " + e.getMessage());
        }
    }
    private void releaseAudio() {
        if (record != null) { record.release(); record = null; }
        if (track != null) { track.release(); track = null; }
    }

    private void startRecording() {
        if (record == null || track == null) {
            initializeAudio();
            if (record == null || track == null) return;
        }

        isRecording = true;
        record.startRecording();
        track.play();

        recordingThread = new Thread(this::recordLoop);
        recordingThread.start();

        runOnUiThread(() -> {
            recordingIndicator.setVisibility(View.VISIBLE);
        });
        startNewRecordingFile();
    }


    private void recordLoop() {
        while (isRecording) {
            int read = record.read(inBuffer, 0, BUFFER_SIZE);
            if (read <= 0) continue;


            // Convert byte buffer to short buffer
            int samplesRead = read / 2;
            ByteBuffer.wrap(inBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, samplesRead);

            // Modify sample by sample
            for (int i = 0; i < samplesRead; i++) {
                short s = shortBuffer[i];

                // Example: volume change
                float vf = volumeFactor;
                float value = s * vf;
                //Clipping protection
                if (value > 32767f) value = 32767f;
                if (value < -32768f) value = -32768f;
                s = (short) value;

                shortBuffer[i] = s;
            }


            // Store samples in AudioBuffer
            int offset = 0;
            while (offset < samplesRead) {
                int remaining = AUDIO_BUFFER_SIZE - audioBufferIndex;
                int toCopy = Math.min(remaining, samplesRead  - offset);
                System.arraycopy(shortBuffer, offset, audioBuffer, audioBufferIndex, toCopy);
                audioBufferIndex += toCopy;
                offset += toCopy;
                // Call updateCoefficients once the buffer is full
                if (audioBufferIndex >= AUDIO_BUFFER_SIZE) {
                    updateCoefficients(audioBuffer);
                    audioBufferIndex = 0;
                }
            }

            for (int i = 0; i < samplesRead; i++) {
                short s = shortBuffer[i];
                outBuffer[i * 2] = (byte)(s & 0xFF);
                outBuffer[i * 2 + 1] = (byte)((s >> 8) & 0xFF);
            }

            // Play back audio
            track.write(outBuffer, 0, read);


            // Write audio (raw PCM16) to file
            try {
                if (recordingOutputStream != null) recordingOutputStream.write(inBuffer, 0, read);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write audio to file", e);
            }
        }
    }
    private void stopRecording() {
        isRecording = false;

        if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
        if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();

        if (recordingThread != null) {
            try { recordingThread.join(); } catch (InterruptedException ignored) {}
            recordingThread = null;
        }

        if (recordingOutputStream != null) {
            try {
                recordingOutputStream.flush();
                recordingOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close recording output stream", e);
            }
            recordingOutputStream = null;
        }
        runOnUiThread(() -> {
            recordingIndicator.setVisibility(View.GONE);
        });

    }
    private void startNewRecordingFile() {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "signal_lab_recording_" + System.currentTimeMillis() + ".raw");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

        Uri recordingUri = resolver.insert(uri, values);

        if (recordingUri == null) {
            Log.e(TAG, "Failed to create file in Downloads");
            return;
        }

        try {
            recordingOutputStream = resolver.openOutputStream(recordingUri, "w");
        } catch (IOException e) {
            Log.e(TAG, "Failed to open recording output stream", e);
        }
    }
    private void updateCoefficients(short[] frame) {
        double sumSquares = 0.0;

        for (short sample : frame) {
            double s = sample;  // Convert to double
            sumSquares += s * s;
        }

        double rms = Math.sqrt(sumSquares / frame.length);

        double rmsNormalized = rms / 32768.0;

        double newScale;
        if (rmsNormalized > epsilon) {
            newScale = TARGET_RMS / rmsNormalized;
        } else {
            newScale = MAX_SCALE;
        }

        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));

        volumeFactor = (float)(0.9 * volumeFactor + 0.1 * newScale);

//        Log.d(TAG, "volumeFactor: " + volumeFactor + " rms: " + rmsNormalized);

        runOnUiThread(() -> {
            audioStatusText.setText(
                    String.format(
                            "VolumeFactor: %.2f\nRMS: %.3f",
                            volumeFactor,
                            rmsNormalized
                    )
            );
        });

    }



}
