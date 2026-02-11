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

    private Filter lowPass;
    private Filter bandPass;
    private Filter highPass;

    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


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
    public void setBassGain(float gain){
        bassGain = gain;
    }
    public void setMidGain(float gain){
        midGain = gain;
    }
    public void setTrebleGain(float gain){
        trebleGain = gain;
    }
    public void release() {
        if(track != null){
            track.stop();
            track.release();
            track = null;
        }
    }
}