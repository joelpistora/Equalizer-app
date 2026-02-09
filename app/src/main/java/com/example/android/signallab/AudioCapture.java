package com.example.android.signallab;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.widget.TextView;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.Session;

public class AudioCapture extends AppCompatActivity {
    private static final String TAG = "AudioCapture";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView recordingIndicator;
    private AudioRecord record;
    private volatile boolean isRecording = false;
    private Thread recordingThread;
    private ActivityResultLauncher<String> mp3Picker;

    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 1024;
    private final byte[] inBuffer = new byte[BUFFER_SIZE];
    private final short[] shortBuffer = new short[BUFFER_SIZE/2];

    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE / 50; // 48000/50 = 960 samples
    private final short[] audioBuffer = new short[AUDIO_BUFFER_SIZE * 2];
    private int audioBufferIndex = 0;

    private volatile boolean isDecoding = false;
    private Thread decodingThread;



    private void onMp3Selected(@NonNull Uri uri) {
        Log.d(TAG, "MP3 selected: " + uri);
        startMp3DecodeToPcm(uri);
    }

    private void startMp3DecodeToPcm(@NonNull Uri uri) {
        stopRecording();
        stopDecoding();
        isDecoding = true;
        decodingThread = new Thread(() -> decodeMp3ToPcmFile(uri));
        decodingThread.start();
    }

    private void decodeMp3ToPcmFile(@NonNull Uri uri) {

        try {

            java.io.File inputFile = new java.io.File(getCacheDir(), "input.mp3");

            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(inputFile)) {

                byte[] buffer = new byte[8192];
                int read;

                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }

            java.io.File outputFile = new java.io.File(getCacheDir(), "decoded.pcm");

            String command =
                    "-y -i " + inputFile.getAbsolutePath() +
                            " -f s16le -acodec pcm_s16le -ar 48000 -ac 2 " +
                            outputFile.getAbsolutePath();

            Log.d(TAG, "Running FFmpeg: " + command);

            Session session = FFmpegKit.execute(command);

            Log.d(TAG, "Decode finished, returnCode = " + session.getReturnCode());
            Log.d(TAG, "PCM file path = " + outputFile.getAbsolutePath());

        }
        catch (Exception e) {
            Log.e(TAG, "decodeMp3ToPcmFile error", e);
        }
    }

    private void stopDecoding() {
        isDecoding = false;
        if (decodingThread != null) {
            try { decodingThread.join(); } catch (InterruptedException ignored) {}
            decodingThread = null;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_capture);
        Session session = FFmpegKit.execute("-version");
        Log.d(TAG, "FFmpeg test returnCode = " + session.getReturnCode());

        Button startButton = findViewById(R.id.record);
        Button stopButton = findViewById(R.id.stop);

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());

        recordingIndicator = findViewById(R.id.recordingIndicator);

        mp3Picker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    onMp3Selected(uri);
                }
        );

        Button importButton = findViewById(R.id.importMp3);
        importButton.setOnClickListener(v -> {
            stopRecording();
            mp3Picker.launch("audio/mpeg"); // mp3
        });

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
        stopDecoding();
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
        if (record != null) return;

        try {
            // Setup Input Audio
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build())
                    .setBufferSizeInBytes(minBuffer)
                    .build();

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize");
                record = null;
                return;
            }


        } catch (SecurityException se) {
            Log.e(TAG, "Permission missing: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing audio: " + e.getMessage());
        }
    }

    private void releaseAudio() {
        if (record != null) {
            record.release();
            record = null;
        }
    }

    private void startRecording() {
        if (record == null) {
            initializeAudio();
            if (record == null) return;
        }

        isRecording = true;
        record.startRecording();

        recordingThread = new Thread(this::recordLoop);
        recordingThread.start();

        runOnUiThread(() -> {
            recordingIndicator.setVisibility(View.VISIBLE);
        });
    }


    private void recordLoop() {
        while (isRecording) {
            int read = record.read(inBuffer, 0, BUFFER_SIZE);
            if (read <= 0) continue;


            // Convert byte buffer to short buffer
            int samplesRead = read / 2;
            ByteBuffer.wrap(inBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, samplesRead);

            // Store samples in AudioBuffer
            int offset = 0;
            while (offset < samplesRead) {
                int remaining = (AUDIO_BUFFER_SIZE * 2) - audioBufferIndex;
                int toCopy = Math.min(remaining, samplesRead - offset);
                System.arraycopy(shortBuffer, offset, audioBuffer, audioBufferIndex, toCopy);
                audioBufferIndex += toCopy;
                offset += toCopy;
                // Call updateCoefficients once the buffer is full
                if (audioBufferIndex >= AUDIO_BUFFER_SIZE*2) {
                    //updateCoefficients(audioBuffer);
                    onFrameReady(audioBuffer);
                    audioBufferIndex = 0;
                }
            }
        }
    }

    private void onFrameReady(short[] audioBuffer) {
        //visualisation in AudioEngine and etc
    }

    private void stopRecording() {
        isRecording = false;

        if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
            record.stop();

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException ignored) {
            }
            recordingThread = null;
        }

        runOnUiThread(() -> {
            recordingIndicator.setVisibility(View.GONE);
        });

    }
}
