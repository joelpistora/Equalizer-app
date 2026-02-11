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
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(4f);
        paint.setAntiAlias(true);
    }

    public void updateSpectrum(float[] spectrum) {
        this.spectrum = spectrum;
        invalidate(); // triggers redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (spectrum == null) return;

        float width = getWidth();
        float height = getHeight();

        float barWidth = width / spectrum.length;

        for (int i = 0; i < spectrum.length; i++) {
            float value = spectrum[i] * height;
            canvas.drawLine(
                    i * barWidth,
                    height,
                    i * barWidth,
                    height - value,
                    paint
            );
        }
    }
}

