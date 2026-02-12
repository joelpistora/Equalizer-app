package com.example.android.signallab;

import android.content.Context;
import android.util.Log;

import org.jtransforms.fft.FloatFFT_1D;

import java.util.Arrays;

public class VisualEngine {
    private static final String TAG = "VisualEngine";

    public interface SpectrumListener {
        void onSpectrumReady(float[] spectrum);
    }

    private SpectrumListener listener;
    private static VisualEngine instance;

    private static final int FFT_SIZE = 1024;
    private FloatFFT_1D fft;
    private float[] fft_buffer;
    private float[] spectrum;

    public VisualEngine() {
        fft = new FloatFFT_1D(FFT_SIZE);
        fft_buffer = new float[FFT_SIZE * 2];
        spectrum = new float[FFT_SIZE / 2];
    }

    public static VisualEngine getInstance() {
        if (instance == null) {
            instance = new VisualEngine();
        }
        return instance;
    }

    public void processFrame(float[] buffer) {

        float[] padded = new float [FFT_SIZE];
        int n = Math.min(buffer.length, FFT_SIZE);
        System.arraycopy(buffer, 0, padded, 0, n);

        float[] fftResult = computeFFT(padded);

        if (listener != null) {
            Log.d(TAG, "Spectrum ready: " + Arrays.toString(fftResult));
            listener.onSpectrumReady(fftResult);
        }
    }


    private float[] computeFFT(float[] sampleBuffer) {
        //sampleBuffer is 1024 (zero-pad is done in processFrame)
        for (int i = 0; i < FFT_SIZE; i++) {
            float window = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (FFT_SIZE - 1) ); //Hanning
            fft_buffer[2 * i] = sampleBuffer[i] * window;     // Real part.
            fft_buffer[2 * i + 1] = 0f;                     //Imaginary part. Real audio IM =0
        }
        fft.complexForward(fft_buffer);
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            spectrum[i] = (float) Math.sqrt(fft_buffer[2 * i] * fft_buffer[2 * i]
                    + fft_buffer[2 * i + 1] * fft_buffer[2 * i + 1]);
        }

        // Temporary fake FFT for testing
//        float[] fakeSpectrum = new float[64];
//        for (int i = 0; i < fakeSpectrum.length; i++) {
//            fakeSpectrum[i] = (float) Math.random();
//        }
//        return fakeSpectrum;
        return spectrum.clone();
    }

    public void setSpectrumListener(SpectrumListener listener) {
        this.listener = listener;
    }
}
