package com.example.android.signallab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.List;

public class AudioEngine {
    private static AudioEngine instance;
    private final Context context;
    private List<Float> audioTrack = new ArrayList<>(); // Stores the recorded audio
    private Thread audioThread;
    // EQ gains
    private volatile float bassGain = 1.0f;
    private volatile float midGain = 1.0f;
    private volatile float trebleGain = 1.0f;

    public AudioEngine(Context context) {
        this.context = context;
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