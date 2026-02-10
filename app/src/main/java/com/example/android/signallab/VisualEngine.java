package com.example.android.signallab;

import org.jtransforms.fft.FloatFFT_1D;

public class VisualEngine {
    private static final int FFT_SIZE = 1024;
    private FloatFFT_1D fft;
    private float[] fft_buffer;
    private float[] spectrum;
    private float[] sampleBuffer;

    public VisualEngine() {
        fft = new FloatFFT_1D(FFT_SIZE);
        fft_buffer = new float[1024];
        spectrum = new float[FFT_SIZE / 2];
        sampleBuffer = new float[FFT_SIZE];

    }

    private void computeFFT() {
        for (int i = 0; i < FFT_SIZE; i++) {
            float window = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / FFT_SIZE);
            fft_buffer[2 * i] = sampleBuffer[i] * window;     // Real part.
            fft_buffer[2 * i + 1] = 0f;                     //Imaginary part. Real audio IM =0
        }
        fft.complexForward(fft_buffer);
        for (int i = 0; i < FFT_SIZE; i++) {
            spectrum[i] = (float) Math.sqrt(fft_buffer[2 * i] * fft_buffer[2 * i]);


        }
    }
}
