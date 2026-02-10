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

    private short[] recording;

    // Constructor
    public AudioEngine(Context context) {
        this.context = context;
        recording = [];
    }

    public insertAudio(short[] buffer) {
        recording += buffer;
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