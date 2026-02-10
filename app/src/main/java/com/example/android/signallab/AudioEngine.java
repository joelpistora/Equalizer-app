package com.example.android.signallab;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.se.omapi.Session;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioEngine {

    // This class should handle all audio logic and is controlled by the UI activities
    // It takes care of preprocessing the audio from the recorded audio and the mp3 file
    // It handles the single audio buffer that is used throughout the app
    // It hosts methods and functions the process the audio and apply EQ
    // It applies EQ using the filter class
    // Decides all constants and parameters used in the audio processing
    private static final String TAG = "AudioEngine";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private final Context context;
    // private final Log log;
    private AudioRecord record;
    private AudioTrack track;
    private volatile boolean isRecording = false;
    private Thread audioThread;

    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 1024;
    private final byte[] inBuffer = new byte[BUFFER_SIZE];
    private final byte[] outBuffer = new byte[BUFFER_SIZE];
    private final short[] shortBuffer = new short[BUFFER_SIZE / 2];

    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE / 50; // 48000/50 = 960 samples
    private final short[] audioBuffer = new short[AUDIO_BUFFER_SIZE];
    private int audioBufferIndex = 0;
    private volatile boolean isDecoding = false;
    private Thread decodingThread;
    private volatile Session ffmpegSession;
    private Thread recordingThread;
    private long recordingStartTimeMs = 0;
    private static final long MAX_RECORDING_MS = 60_000; //max recording duration 1 min
    private float volumeFactor = 1.0f;

    private static final double TARGET_RMS = 0.25;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 4.0;
    private static final double epsilon = 1e-9;

    // EQ gains
    private volatile float bassGain = 1.0f;
    private volatile float midGain = 1.0f;
    private volatile float trebleGain = 1.0f;

    // Audio decoding
    private MediaExtractor extractor;
    private MediaCodec decoder;

    // Chosen parameters
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Constructor
    public AudioEngine(Context context) {
        this.context = context;
        initializeAudio();
    }
    private void initializeAudio(){
        try {
            // Setup Input Audio
            int minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AUDIO_FORMAT,
                    CHANNEL_CONFIG
            );

            record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(minBufferSize)
                    .build();

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize");
                record = null;
                return;
            }

            int trackBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
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

        } catch (SecurityException e) {
            Log.e(TAG, "Permission missing: " + e.getMessage());
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
        recordingStartTimeMs = android.os.SystemClock.elapsedRealtime();
        record.startRecording();
        recordingThread = new Thread(this::recordLoop);
        recordingThread.start();
    }

    private void recordLoop() {
        while (isRecording) {
            if (android.os.SystemClock.elapsedRealtime() - recordingStartTimeMs >= MAX_RECORDING_MS) {
                stopRecording();
                break;
            }
            int read = record.read(inBuffer, 0, BUFFER_SIZE);
            if (read <= 0) continue;
            int samplesRead = read / 2;
            ByteBuffer.wrap(inBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, samplesRead);
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

    private void stopRecording() {
        isRecording = false;
        if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
            record.stop();
        if (recordingThread != null && Thread.currentThread() != recordingThread) {
            try {
                recordingThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        recordingThread = null;
    }


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
            ffmpegSession = FFmpegKit.execute(command);
            Log.d(TAG, "Decode finished, returnCode = " + ffmpegSession.getReturnCode());
            ffmpegSession = null;
            if (!isDecoding) return;
            if (!outputFile.exists()) return;
            Log.d(TAG, "PCM file path = " + outputFile.getAbsolutePath());
            feedPcmFileToFrames(outputFile);
        }
        catch (Exception e) {
            Log.e(TAG, "decodeMp3ToPcmFile error", e);
        }
    }

    private void feedPcmFileToFrames(@NonNull java.io.File pcmFile) {
        final int frameShorts = AUDIO_BUFFER_SIZE * 2;   // 960 * 2ch = 1920 short
        final int frameBytes  = frameShorts * 2;         // 1920 * 2 bytes = 3840 bytes
        byte[] pcmBytes = new byte[frameBytes];
        try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(pcmFile))) {
            while (isDecoding) {
                int total = 0;
                while (total < frameBytes) {
                    int n = in.read(pcmBytes, total, frameBytes - total);
                    if (n < 0) { // EOF
                        isDecoding = false;
                        return;
                    }
                    total += n;
                }
                ByteBuffer.wrap(pcmBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(audioBuffer, 0, frameShorts);
                onFrameReady(audioBuffer);
            }
        } catch (Exception e) {
            Log.e(TAG, "feedPcmFileToFrames failed", e);
        }
    }

    private void stopDecoding() {
        isDecoding = false;
        audioBufferIndex = 0;
        if (ffmpegSession != null) {
            FFmpegKit.cancel(ffmpegSession.getSessionId());
            ffmpegSession = null;
        }
        decodingThread = null;
    }

    private void onFrameReady(short[] audioBuffer) {
        Log.d(TAG, "Frame ready: " + audioBuffer[0]);
    }
    public void setBassGain(float gain){
        bassGain = gain;
    }
    public void midGain(float gain){
        midGain = gain;
    }
    public void trebleGain(float gain){
        trebleGain = gain;
    }
    
}