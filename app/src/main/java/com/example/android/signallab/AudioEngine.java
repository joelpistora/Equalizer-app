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
    private volatile boolean isPlaying;
    private int bufferPosition; // Current playback position
    // EQ gains
    private volatile float bassGain = 1.0f;
    private volatile float midGain = 1.0f;
    private volatile float trebleGain = 1.0f;

    private Filter lowPass;
    private Filter bandPass;
    private Filter highPass;

    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    public AudioEngine(Context context) {
        this.context = context;
        initializeAudioTrack();
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

    private void processFrame(float[] buffer){
        short[] processedBuffer = new short[buffer.length];
        for(int i = 0; i < buffer.length; i++){
            float sample = buffer[i];

            float bass = lowPass.process(sample)*bassGain;
            float mid = bandPass.process(sample)*midGain;
            float treble = highPass.process(sample)*trebleGain;

            //output
            float output = bass + mid + treble;
            processedBuffer[i] = (short) (output);

        }

    }
    private void initializeFilter(){
        lowPass = new Filter(SAMPLE_RATE, 200, 0.707, Filter.Type.LOWPASS);
        bandPass = new Filter(SAMPLE_RATE, 1000, 0.707, Filter.Type.BANDPASS);
        highPass = new Filter(SAMPLE_RATE, 3000, 0.707, Filter.Type.HIGHPASS);

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
        if (track == null) {
            initializeAudioTrack(); // create AudioTrack if needed
            Log.d(TAG, "Track is null, need to initialize audio track");
        }
        if (audioTrack == null || audioTrack.isEmpty()) {
            Log.d(TAG, "Nothing to play");
            return; // nothing to play
        }

        short[] buffer = convertToShortArray(audioTrack);

        if (isPlaying) return; // already playing

        isPlaying = true;
        bufferPosition = 0;

        Log.d(TAG, "Starting playback loop");

        track.play(); // start the AudioTrack

        playbackThread = new Thread(() -> {
            int frameSize = 1024; // number of samples per write
            while (isPlaying) {
                // Calculate remaining samples
                int remaining = buffer.length - bufferPosition;
                if (remaining <= 0) {
                    bufferPosition = 0; // loop playback
                    remaining = buffer.length;
                }

                int toWrite = Math.min(frameSize, remaining);

                // Copy frame
                short[] frame = new short[toWrite];
                System.arraycopy(buffer, bufferPosition, frame, 0, toWrite);

                // Apply EQ to frame
                // applyEQ(frame);

                Log.d(TAG, "Playing audio: " + Arrays.toString(frame));

                // Write to AudioTrack
                track.write(frame, 0, toWrite);

                bufferPosition += toWrite;
            }
            track.pause();
            track.flush();
        });
        playbackThread.start();
    }

    public void stopPlaybackLoop() {
        Log.d(TAG, "Stopping playback loop");
        isPlaying = false;
        if (playbackThread != null) {
            try {
                playbackThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error in stopping playback");
            }
            playbackThread = null;
        }
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