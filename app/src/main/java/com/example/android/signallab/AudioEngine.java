package com.example.android.signallab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static AudioEngine instance;
    private final Context context;
    private List<Float> audioTrack = new ArrayList<>(); // Stores the recorded audio

    private AudioTrack track;
    private Thread playbackThread;
    private VisualEngine visualEngine;
    private volatile boolean isPlaying;
    private volatile boolean isPaused;
    private final Object pauseLock = new Object();
    private volatile int bufferPosition; // Current playback position
    // Class fields
    private short[] buffer;       // audio buffer
    private int totalSamples;     // total length of buffer
    // EQ gains
    private volatile float bassGain = 1.0f;
    private volatile float midGain = 1.0f;
    private volatile float trebleGain = 1.0f;

    private Filter lowPass;
    private Filter bandPass;
    private Filter bandPass2;
    private Filter highPass;

    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    public synchronized void clear() {
        audioTrack.clear();
        track = null;
        buffer = null;
        bufferPosition = 0;
    }
    //WHITE NOISE FOR TESTING
    public synchronized void generateWhiteNoise(float seconds, float amplitude) {
        clear();

        int totalSamples = (int)(SAMPLE_RATE * seconds);

        for (int i = 0; i < totalSamples; i++) {
            float sample = (float)(Math.random() * 2.0 - 1.0);
            sample *= amplitude;
            audioTrack.add(sample);
        }
    }


    public AudioEngine(Context context) {
        this.context = context;
        initializeAudioTrack();
        initializeFilter();
        Log.d(TAG, "AudioEngine initialized");
        visualEngine = VisualEngine.getInstance();
    }

    public static synchronized AudioEngine getInstance(Context context) {
        if (instance == null) {
            instance = new AudioEngine(context.getApplicationContext());
        }

        return instance;
    }

    // Adds audio samples to audioTrack
    public synchronized void appendBuffer(short[] buffer) {
        for (short value : buffer) {
            float normalized = value / 32768f;  // Convert PCM16 to float
            audioTrack.add(normalized);
        }
    }

    private float[] processFrame(float[] buffer){
        float[] processedBuffer = new float[buffer.length];
        for(int i = 0; i < buffer.length; i++){
            float sample = buffer[i];

            float bass = lowPass.process(sample);
            //float mid = bandPass.process(sample);
            float treble = highPass.process(sample);

            float mid = bandPass.process(bandPass.process(sample));
            // cascade

            // cascade again

            float bass_bass = (bass)*bassGain;
            float mid_mid = (mid)*midGain;
            float treble_treble = (treble)*trebleGain;


//            float bass_bass = lowPass.process(bass)*bassGain;
//            float mid_mid = bandPass.process(mid)*midGain;
//            float treble_treble = highPass.process(treble)*trebleGain;

            processedBuffer[i] = (float)Math.tanh(bass_bass + mid_mid + treble_treble);
        }
        return processedBuffer;
    }
    private void initializeFilter(){
        lowPass = new Filter(SAMPLE_RATE, 200, 0.707, Filter.Type.LOWPASS);
        bandPass = new Filter(SAMPLE_RATE, 1000, 1, Filter.Type.BANDPASS);
        bandPass2 = new Filter(SAMPLE_RATE, 1000, 1, Filter.Type.BANDPASS);
        highPass = new Filter(SAMPLE_RATE, 6000, 0.707, Filter.Type.HIGHPASS);



        Log.d(TAG, "Filters initialized: LP=200Hz, BP=1000Hz, HP=3000Hz");
    }
    private void resetFilters() {
        if (lowPass != null) lowPass.reset();
        if (bandPass != null) bandPass.reset();
        if (highPass != null) highPass.reset();
    }
    private void initializeAudioTrack() {
        // Setup Output Audio
        int trackBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
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
    }

    private short[] convertToShortArray(List<Float> audio) {
        short[] array = new short[audio.size()];
        for (int i = 0; i < audio.size(); i++) {
            float sample = audio.get(i);
            // clip to -1..1 just in case
            sample = Math.max(-1f, Math.min(1f, sample));
            array[i] = (short) (sample * 32767); // convert float [-1,1] → PCM16
        }
        return array;
    }

    public void startPlaybackLoop() {
        synchronized (pauseLock) {
            isPaused = false;
            pauseLock.notifyAll();
        }

        // Initialize track if needed
        if (track == null) {
            initializeAudioTrack();
        }

        if (audioTrack == null || audioTrack.isEmpty()) {
            Log.d(TAG, "Nothing to play");
            return; // nothing to play
        }

        // Initialize buffer if not done yet
        if (buffer == null || buffer.length == 0) {
            buffer = convertToShortArray(audioTrack);
            if (buffer == null || buffer.length == 0) {
                Log.d(TAG, "Buffer is empty after conversion, cannot play");
                return;
            }
            totalSamples = buffer.length;
            bufferPosition = 0;
            Log.d(TAG, "Buffer initialized, totalSamples=" + totalSamples);
        }

        // Already playing? just resume
        if (isPlaying) {
            if (isPaused) {
                isPaused = false;
                synchronized (pauseLock) {
                    pauseLock.notifyAll();
                }
            }
            return; // thread already running
        }

        isPlaying = true;

        resetFilters();
        Log.d(TAG, "Starting playback loop");

        track.play();

        playbackThread = new Thread(() -> {
            int frameSize = 1024;
            while (isPlaying) {

                synchronized (pauseLock) {
                    while (isPaused) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                int remaining = buffer.length - bufferPosition;
                if (remaining <= 0) {
                    bufferPosition = 0; // loop
                    remaining = buffer.length;
                }

                int toWrite = Math.min(frameSize, remaining);
                short[] frame = new short[toWrite];
                System.arraycopy(buffer, bufferPosition, frame, 0, toWrite);

                // Process
                float[] floatFrame = new float[toWrite];
                for (int i = 0; i < toWrite; i++) floatFrame[i] = frame[i] / 32768f;

                float[] processedFrame = processFrame(floatFrame);
                visualEngine.processFrame(processedFrame, bufferPosition, totalSamples);

                for (int i = 0; i < toWrite; i++) {
                    float sample = Math.max(-1f, Math.min(1f, processedFrame[i]));
                    frame[i] = (short) (sample * 32767);
                }

                track.write(frame, 0, toWrite);
                bufferPosition += toWrite;
            }

            track.pause();
            track.flush();
        });

        playbackThread.start();
    }

    public void pausePlaybackLoop() {
        Log.d(TAG, "Pausing playback loop");
        isPaused = true;
    }

    public void stopPlaybackLoop() {
        Log.d(TAG, "Stopping playback loop");

        isPlaying = false;

        synchronized (pauseLock) {
            isPaused = false;
            pauseLock.notifyAll();   // wake thread if waiting
        }

        if (playbackThread != null) {
            try {
                playbackThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }

        resetFilters();
    }

    public void setBassGain(float gain){
        Log.d(TAG, "setBassGain: " + gain);
        bassGain = gain;
    }
    public void setMidGain(float gain){
        Log.d(TAG, "setMidGain: " + gain);
        midGain = gain;
    }
    public void setTrebleGain(float gain){
        Log.d(TAG, "setTrebleGain: " + gain);
        trebleGain = gain;
    }
}
