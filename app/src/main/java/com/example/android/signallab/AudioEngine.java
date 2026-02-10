package com.example.android.signallab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private final Context context;
    private AudioTrack track;
    private Thread audioThread;
    // EQ gains
    private volatile float bassGain = 1.0f;
    private volatile float midGain = 1.0f;
    private volatile float trebleGain = 1.0f;

    // Audio decoding
    private MediaExtractor extractor;
    private MediaCodec decoder;

    // Chosen parameters
    private static final int SAMPLE_RATE = 44100; // target sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    public AudioEngine(Context context) {
        this.context = context;
        initAudioTrack();
    }
    private void initAudioTrack(){
        int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AUDIO_FORMAT,
                CHANNEL_CONFIG
        );
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
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
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
    
}
