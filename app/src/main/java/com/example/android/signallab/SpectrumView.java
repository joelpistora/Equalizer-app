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

    private Paint axisPaint = new Paint();
    private Paint textPaint = new Paint();

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

        axisPaint.setColor(Color.WHITE);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(26f);
        textPaint.setAntiAlias(true);
    }

    public void updateSpectrum(float[] spectrum) {
        this.spectrum = spectrum;
        invalidate(); // triggers redraw
    }

    private float freqToXLog(float freq, float canvasWidth, float minFreq, float maxFreq) {
        freq = Math.max(freq, minFreq); // avoiding log10(0)
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

        float plotLeft = 80f;
        float plotRight = width - 20f;
        float plotTop = 20f;
        float plotBottom = height - 60f;
        float plotWidth = plotRight - plotLeft;

        float minDb = -60f;
        float maxDb = 0f;

        float sampleRate = 44100f;       // or whatever your AudioEngine uses
        int fftSize = spectrum.length * 2; // assuming real+imaginary FFT bins are half
        for (int i = 0; i < spectrum.length; i++) {
            float freq = i * sampleRate / fftSize; // bin to Hz
            float x = plotLeft + freqToXLog(freq, plotWidth, 20f, 20000f);

            float normalized = (spectrum[i] - minDb) / (maxDb - minDb);
            normalized = Math.max(0f, Math.min(1f, normalized));
            float barHeight = normalized * (plotBottom - plotTop);

            canvas.drawLine(x, plotBottom, x, plotBottom - barHeight, paint);
        }
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, axisPaint); // X axis
        canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axisPaint);     // Y axis

        // X ticks (Hz) - same log mapping
        float[] xTicks = new float[]{50, 100, 200, 500, 1000, 2000, 5000, 10000};
        for (float hz : xTicks) {
            float x = plotLeft + freqToXLog(hz, plotWidth, 20f, 20000f);
            canvas.drawLine(x, plotBottom, x, plotBottom + 10f, axisPaint);

            String label = (hz >= 1000) ? ((int)(hz / 1000)) + "k" : String.valueOf((int)hz);
            canvas.drawText(label, x - 16f, height - 15f, textPaint);
        }

        // Y ticks (dB) - using your minDb/maxDb
        float[] yTicks = new float[]{0f, -20f, -40f, -60f};
        for (float db : yTicks) {
            float t = (db - minDb) / (maxDb - minDb);
            t = Math.max(0f, Math.min(1f, t));
            float y = plotBottom - t * (plotBottom - plotTop);

            canvas.drawLine(plotLeft - 10f, y, plotLeft, y, axisPaint);
            canvas.drawText(((int)db) + " dB", 10f, y + 8f, textPaint);
        }
    }
}

