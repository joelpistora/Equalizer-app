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
import java.nio.ShortBuffer;
import java.util.Arrays;

import android.widget.TextView;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.Session;

import android.content.Intent;

public class AudioCapture extends AppCompatActivity {
    private static final String TAG = "AudioCapture";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final long MAX_RECORDING_MS = 60_000; //max recording duration 1 min
    private TextView recordingIndicator;
    private Button startButton;
    private Button stopButton;
    private Button MP3Button;
    private Button EQButton;
    private AudioRecord record;
    private volatile boolean isRecording = false;
    private Thread recordingThread;
    private ActivityResultLauncher<String> mp3Picker;

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private final byte[] inBuffer = new byte[BUFFER_SIZE];//byte
    private final short[] shortBuffer = new short[BUFFER_SIZE/2];//1sample=2bytes, 512 samples
    //50 frames per second (1000/20ms)
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE / 50; //20ms mono frame
    private final short[] audioBuffer = new short[AUDIO_BUFFER_SIZE]; //mono
    private int audioBufferIndex = 0;
    private volatile boolean isDecoding = false;
    private Thread decodingThread;
    private volatile Session ffmpegSession;
    private long recordingStartTimeMs = 0;

    private AudioEngine audioEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_capture);

        recordingIndicator = findViewById(R.id.recordingIndicator); // Recording status text
        startButton = findViewById(R.id.record);                    // Start Button
        stopButton = findViewById(R.id.stop);                       // Stop Button
        MP3Button = findViewById(R.id.importMp3);                   // MP3 Button
        EQButton = findViewById(R.id.cont);                         // EQ Button

        startButton.setOnClickListener(v -> startRecording());

        stopButton.setOnClickListener(v -> {
            stopRecording();
            stopDecoding();//allows user to manually stop Mp3 decoding
        });

        EQButton.setOnClickListener(v -> {
            Intent intent = new Intent(AudioCapture.this,
                    EQActivity.class);
            startActivity(intent);
        });

        audioEngine = AudioEngine.getInstance(this);

        mp3Picker = registerForActivityResult(//when user selects a file, onMp3Selected is called
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    onMp3Selected(uri);//starts decoding pipeline(FFmped-PCMframes)
                }
        );

        MP3Button.setOnClickListener(v -> {//stops any active audio source before importing a new one
            stopRecording();
            stopDecoding();
            mp3Picker.launch("audio/mpeg");
        });

        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {//rechecking mic permissions & then AudioRecord
        super.onResume();
        checkAndRequestPermissions();
        resetUI();
    }

    private void resetUI() {
        recordingIndicator.setText(getResources().getText(R.string.title_screen));
        recordingIndicator.setTextColor(getResources().getColor(R.color.colorText));
    }

        @Override
    protected void onPause() {//stoping all audio work
        stopRecording();
        stopDecoding();
        releaseAudio();
        super.onPause();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
        stopDecoding();
        releaseAudio();
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

    private void initializeAudio() {//creates and configures AudioRecord for mic capture
        if (record != null) return;
        try {//asking android for minimum safe buffer size
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()//building AudioRecord with the required audio format (44,1kHz, PCM16, mono)
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(minBuffer)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {//verifying initialization succeeded
                Log.e(TAG, "AudioRecord failed to initialize");
                record = null;
            }
        } catch (SecurityException se) {//checking permissions just to be safe
            Log.e(TAG, "Permission missing: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing audio: " + e.getMessage());
        }
    }

    private void releaseAudio() {
        if (record != null) {//releases AudioRecord resources when activity stops or pauses
            record.release();
            record = null;
        }
    }

    private void startRecording() {//initializing AudioRecord
        if (isRecording) return;

        if (record == null) {
            initializeAudio();
            if (record == null) return;
        }

        audioBufferIndex = 0;
        audioEngine.clear(); //clearing the recording

        isRecording = true;
        recordingStartTimeMs = android.os.SystemClock.elapsedRealtime();//saving start time, max 1min recording
        record.startRecording();//capturing audio from mic
        recordingThread = new Thread(this::recordLoop);//background loop that reads audio continuously
        recordingThread.start();

        recordingIndicator.setText(getResources().getText(R.string.recording_indicator));
        recordingIndicator.setTextColor(getResources().getColor(R.color.colorAccent));

        startButton.setEnabled(false);
        startButton.setAlpha(0.5f);

        EQButton.setEnabled(false);
        EQButton.setAlpha(0.5f);

        MP3Button.setEnabled(false);
        MP3Button.setAlpha(0.5f);

        stopButton.setEnabled(true);
        stopButton.setAlpha(1);

        Log.d(TAG, "Started recording");
    }

    private void recordLoop() {

        // Create reusable ByteBuffer + ShortBuffer once
        ByteBuffer byteBuffer = ByteBuffer
                .wrap(inBuffer)
                .order(ByteOrder.LITTLE_ENDIAN);

        ShortBuffer shortView = byteBuffer.asShortBuffer();

        while (isRecording) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - recordingStartTimeMs;
            if (elapsed >= MAX_RECORDING_MS) {
                isRecording = false;
                break;
            }

            int read = record.read(inBuffer, 0, BUFFER_SIZE);   //reading raw audio bytes from mic
            if (read <= 0) continue;

            int samplesRead = read / 2; //converting bytes to samples (number of samples=bytes/2)

            // Reset buffer position before reading
            byteBuffer.position(0);
            shortView.position(0);
            shortView.get(shortBuffer, 0, samplesRead);

            int offset = 0;

            while (offset < samplesRead) {
                int remaining = audioBuffer.length - audioBufferIndex; //space left until full frame
                int toCopy = Math.min(remaining, samplesRead - offset);

                System.arraycopy(
                        shortBuffer,
                        offset,
                        audioBuffer,
                        audioBufferIndex,
                        toCopy
                );

                audioBufferIndex += toCopy;
                offset += toCopy;

                if (audioBufferIndex >= audioBuffer.length) {  //frame complete
                    // Pass a copy so buffer reuse if safe
                    short[] frameCopy = audioBuffer.clone();
                    Log.d(TAG, "Read frame : " + Arrays.toString(frameCopy));
                    onFrameReady(frameCopy);    //one full 20ms mono frame
                    audioBufferIndex = 0;       //resetting for next frame
                }
            }
        }
    }

    private void stopRecording() {
        audioBufferIndex=0;
        isRecording = false;//turning recording off
        if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
            record.stop();//stopping AudioRecord capturing from mic if its still recording
        if (recordingThread != null && Thread.currentThread() != recordingThread) {
            try {
                recordingThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        Log.d(TAG, "Stopped recording");
        recordingThread = null;

        recordingIndicator.setText(getResources().getText(R.string.after_recording));
        recordingIndicator.setTextColor(getResources().getColor(R.color.colorGreen));

        startButton.setEnabled(true);
        startButton.setAlpha(1);

        stopButton.setEnabled(false);
        stopButton.setAlpha(0.5f);

        EQButton.setEnabled(true);
        EQButton.setAlpha(1);

        MP3Button.setEnabled(true);
        MP3Button.setAlpha(1);
    }


    private void onMp3Selected(@NonNull Uri uri) {//called after user selects an Mp3 from file picker
        Log.d(TAG, "MP3 selected: " + uri);
        startMp3DecodeToPcm(uri);//starting decoding mp3 to PCM
    }

    private void startMp3DecodeToPcm(@NonNull Uri uri) {
        stopRecording();//ensuring only one audio source runs at a time
        stopDecoding();
        recordingIndicator.setText(getResources().getText(R.string.loading));
        recordingIndicator.setTextColor(getResources().getColor(R.color.colorRed));
        isDecoding = true;//enabling decoding loop
        decodingThread = new Thread(() -> decodeMp3ToPcmFile(uri));//background thread for decoding
        decodingThread.start();
        audioBufferIndex = 0;
        audioEngine.clear();
    }

    private void decodeMp3ToPcmFile(@NonNull Uri uri) {
        try {
            java.io.File inputFile = new java.io.File(getCacheDir(), "input.mp3");//FFmpegKit works easiest with file paths
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(inputFile)) {
                byte[] buffer = new byte[8192];//temporary buffer for copying Mp3 stream (8KB is standard IO size)
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            //-f s16le is for raw PCM 16-bit little-endian
            //-ar 44100 is for sample rate
            //-ac 1 is for MONO
            java.io.File outputFile = new java.io.File(getCacheDir(), "decoded.pcm");//decoded raw PCM
            String command = "-y -i " + inputFile.getAbsolutePath() +
                    " -f s16le -acodec pcm_s16le -ar 44100 -ac 1 " + outputFile.getAbsolutePath();
            Log.d(TAG, "Running FFmpeg: " + command);
            ffmpegSession = FFmpegKit.execute(command);
            Log.d(TAG, "Decode finished, returnCode = " + ffmpegSession.getReturnCode());
            ffmpegSession = null;
            if (!isDecoding) return;//if user pressed stop during decode- exit
            if (!outputFile.exists()) return;//if decode failed -exit
            Log.d(TAG, "PCM file path = " + outputFile.getAbsolutePath());//read decoded PCM and feed frames into onFrameReady()
            feedPcmFileToFrames(outputFile);
        }
        catch (Exception e) {
            Log.e(TAG, "decodeMp3ToPcmFile error", e);
        }
    }

    private void feedPcmFileToFrames(@NonNull java.io.File pcmFile) {
        final int frameShorts = AUDIO_BUFFER_SIZE; //882
        final int frameBytes  = frameShorts * 2; //882 * 2 bytes = 1764 bytes
        byte[] pcmBytes = new byte[frameBytes];
        try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(pcmFile))) {
            while (isDecoding) {
                int total = 0;
                while (total < frameBytes) {//reading full frame(1764 bytes)
                    int n = in.read(pcmBytes, total, frameBytes - total);
                    if (n < 0) { //file ended
                        isDecoding = false;
                        recordingIndicator.setText(getResources().getText(R.string.ready));
                        recordingIndicator.setTextColor(getResources().getColor(R.color.colorGreen));
                        return;
                    }
                    total += n;
                }
                ByteBuffer.wrap(pcmBytes)//converting raw PCM bytes to PCM16 short samples
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(audioBuffer, 0, frameShorts);
                onFrameReady(audioBuffer);//full frame (same output format as mic)
            }
        } catch (Exception e) {
            Log.e(TAG, "feedPcmFileToFrames failed", e);
        }
    }

    private void stopDecoding() {
        isDecoding = false;
        audioBufferIndex = 0;
        if (ffmpegSession != null) {//if FFmpeg is running - canceling it
            FFmpegKit.cancel(ffmpegSession.getSessionId());
            ffmpegSession = null;
        }
        decodingThread = null;

    }

    private void onFrameReady(short[] audioBuffer) {
        audioEngine.appendBuffer(audioBuffer); // same format for MIC and MP3
    }
    private void updateEQButton() {
    }

}
