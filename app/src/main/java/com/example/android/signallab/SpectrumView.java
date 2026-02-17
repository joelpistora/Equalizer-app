package com.example.android.signallab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {

    private Paint paint = new Paint();
    private float[] spectrum;

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(Color.parseColor("#24a335")); // nice orange
        paint.setStrokeWidth(4f);
        paint.setAntiAlias(true);
    }

    public void updateSpectrum(float[] spectrum) {
        this.spectrum = spectrum;
        invalidate(); // triggers redraw
    }

    private float freqToXLog(float freq, float canvasWidth, float minFreq, float maxFreq) {
        float logMin = (float) Math.log10(minFreq);
        float logMax = (float) Math.log10(maxFreq);
        float logF = (float) Math.log10(freq);
        return ((logF - logMin) / (logMax - logMin)) * canvasWidth;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (spectrum == null) return;

        float width = getWidth();
        float height = getHeight();

        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(24f);

        float minDb = -60f;
        float maxDb = 0f;

        // Draw Y-axis labels
        for (int db = 0; db >= minDb; db -= 10) {
            float y = height * (1 - (db - minDb) / (maxDb - minDb));
            canvas.drawText(db + "dB", 0, y, labelPaint);
        }

        // X-axis labels (linear for now)
        int[] freqs = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};

        for (int f : freqs) {
            float x = freqToXLog(f, width, 20f, 20000f);
            canvas.drawText(f + "Hz", x, height, labelPaint);
        }

        float sampleRate = 44100f;       // or whatever your AudioEngine uses
        int fftSize = spectrum.length * 2; // assuming real+imaginary FFT bins are half
        for (int i = 0; i < spectrum.length; i++) {
            float freq = i * sampleRate / fftSize; // bin to Hz
            float x = freqToXLog(freq, width, 20f, 20000f);

            float normalized = (spectrum[i] - minDb) / (maxDb - minDb);
            normalized = Math.max(0f, Math.min(1f, normalized));
            float barHeight = normalized * height;

            canvas.drawLine(x, height, x, height - barHeight, paint);
        }
    }
}

