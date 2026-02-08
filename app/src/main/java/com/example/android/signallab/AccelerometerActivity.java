package com.example.android.signallab;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Locale;

public class AccelerometerActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LineChart lineChart;
    private TextView xVal, yVal, zVal;
    private LineData lineData;
    private Button startButton;
    private boolean collectValues = false;
    private int counter = 0;
    private int sampleCounter = 0;
    private long firstTimestamp = 0;
    private static final int SAMPLE_WINDOW = 100;

    private float prevGravityX, prevGravityY, prevGravityZ = 0;

    private float alpha = 0.8F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accelerometer);

        // --- UI references---
        lineChart = findViewById(R.id.lineChart);
        xVal = findViewById(R.id.xValueView);
        yVal = findViewById(R.id.yValueView);
        zVal = findViewById(R.id.zValueView);
        startButton = findViewById(R.id.startButton);
        //---UI listener----
        startButton.setOnClickListener(v -> {
            collectValues = !collectValues;
            startButton.setText(collectValues ? R.string.stopButton : R.string.startButton);
        });

        // --- Sensor setup ---
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);

        // --- Chart setup ---
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setDragEnabled(false);
        lineChart.setScaleEnabled(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMaximum(20f); // accelerometer range
        leftAxis.setAxisMinimum(-20f);
        lineChart.getAxisRight().setEnabled(false);

        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);

        // --- Initialize datasets ---
        LineDataSet setX = createDataSet("X", Color.RED);
        LineDataSet setY = createDataSet("Y", Color.GREEN);
        LineDataSet setZ = createDataSet("Z", Color.BLUE);

        lineData = new LineData(setX, setY, setZ);
        lineChart.setData(lineData);
    }
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!collectValues) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        long ts = event.timestamp;
        counter++;

//        if (sampleCounter == 0) {
//            prevGravityX = 0;
//            prevGravityY = 0;
//            prevGravityZ = 0;
//        }

        prevGravityX = alpha * prevGravityX + (1 - alpha) * x;
        prevGravityY = alpha * prevGravityY + (1 - alpha) * y;
        prevGravityZ = alpha * prevGravityZ + (1 - alpha) * z;

        float updatedX = x - prevGravityX;
        float updatedY = y - prevGravityY;
        float updatedZ = z - prevGravityZ;

        // Update TextViews
        xVal.setText(String.format(Locale.US,"%.2f", updatedX));
        yVal.setText(String.format(Locale.US,"%.2f", updatedY));
        zVal.setText(String.format(Locale.US,"%.2f", updatedZ));

        // Add entries to chart
        addEntry(updatedX, updatedY, updatedZ);

        // Estimate sample rate
        if (sampleCounter == 0) {
            firstTimestamp = ts; // Start time in ns
        }
        sampleCounter++;
        if (sampleCounter == SAMPLE_WINDOW) {
            long deltaNs = ts - firstTimestamp;             // elapsed time
            float deltaSec = deltaNs / 1_000_000_000f;       // ns → s
            float samplingRateHz = SAMPLE_WINDOW / deltaSec;
            Log.d("AccelerometerActivity", "Sampling rate: " + samplingRateHz + " Hz");
            sampleCounter = 0;
        }

    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
    }

    private LineDataSet createDataSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        return set;
    }
    private void addEntry(float x, float y, float z) {
        // Add to respective datasets via LineData
        lineData.addEntry(new Entry(counter, x), 0); // dataset 0 = X
        lineData.addEntry(new Entry(counter, y), 1); // dataset 1 = Y
        lineData.addEntry(new Entry(counter, z), 2); // dataset 2 = Z

        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();

        // Show max 150 points, scroll along X-axis
        lineChart.setVisibleXRangeMaximum(150);
        lineChart.moveViewToX(lineData.getEntryCount());
    }
}

