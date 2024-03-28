package com.example.humanactivityrecognition;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class RecognitionActivity extends AppCompatActivity implements SensorEventListener {
    String TAG = "RecognitionActivity";

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ArrayList<Entry> yEntries = new ArrayList<>();
    private ArrayList<Entry> zEntries = new ArrayList<>();
    private Thread thread;
    private long timestampAtStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "OnCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

        Toolbar toolbar = findViewById(R.id.recognition_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        LineChart lineChart = findViewById(R.id.accel_chart);

//        xEntries.add(new Entry(0, 0));
//        yEntries.add(new Entry(0, 0));
//        zEntries.add(new Entry(0, 0));

        // 라인 차트 설정
        lineChart.setData(createData());
        lineChart.getDescription().setEnabled(false); // 차트 설명 비활성화
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long timeStampInMs = (long)value / 1000000;
                long timeStampInSec = timeStampInMs / 1000;
                long milliSecond = timeStampInMs % 1000;
                long minute = timeStampInSec / 60;
                long second = timeStampInSec % 60;
                return String.format("%d:%d:%d", minute, second, milliSecond);
            }
        });
        lineChart.animateXY(50, 50);
        lineChart.invalidate();

        String modelName = "activity_recognition_model.tflite";
        MappedByteBuffer recognitionModel;
        try {
            recognitionModel = loadModelFile(this, modelName);
            Log.i(TAG, "Success to load tflite model");
        } catch (Exception e) {
            Log.e(TAG, "Fail to load tflite");
        }
    }

    private LineData createData() {
        return new LineData(
                createDataSet("Accelerometer X", Color.RED),
                createDataSet("Accelerometer Y", Color.BLUE),
                createDataSet("Accelerometer Z", Color.GREEN)
        );
    }

    private LineDataSet createDataSet(String label, int color) {
        LineDataSet dataSet = new LineDataSet(null, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);

        return dataSet;
    }

    public void onStartButtonClicked(View view) {
        timestampAtStart = SystemClock.elapsedRealtimeNanos();
        sensorManager.registerListener(this, accelerometer, 50000);
        LineChart lineChart = findViewById(R.id.accel_chart);

        LineData lineData = lineChart.getLineData();
        lineData.getDataSetByIndex(0).clear();
        lineData.getDataSetByIndex(1).clear();
        lineData.getDataSetByIndex(2).clear();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestampFromStart = event.timestamp - timestampAtStart;
        long timeStampInMs = timestampFromStart / 1000000;
        long timeStampInSec = timeStampInMs / 1000;
        long milliSecond = timeStampInMs % 1000;
        long minute = timeStampInSec / 60;
        long second = timeStampInSec % 60;
        Log.d(TAG, String.format("accel data = t-%d:%d:%d, x-%f, y-%f, z-%f", minute, second, milliSecond, event.values[0], event.values[1], event.values[2]));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LineChart lineChart = findViewById(R.id.accel_chart);

                LineData lineData = lineChart.getLineData();
                lineData.addEntry(new Entry(timestampFromStart, event.values[0]), 0);
                lineData.addEntry(new Entry(timestampFromStart, event.values[1]), 1);
                lineData.addEntry(new Entry(timestampFromStart, event.values[2]), 2);
                lineData.notifyDataChanged();

                lineChart.notifyDataSetChanged();
                lineChart.setVisibleXRangeMaximum(1000000000);
                lineChart.moveViewToX(timestampFromStart);
            }
        });
    }

    public void onEndButtonClicked(View view) {
        sensorManager.unregisterListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                Log.i(TAG, "Back button on toolbar is clicked.");
                finish();
                return true;
            }
            default:
                Log.i(TAG, "Un-handled event");
        }
        return super.onOptionsItemSelected(item);
    }

    private MappedByteBuffer loadModelFile(Activity activity, String modelName) throws IOException {
        AssetManager am = activity.getAssets();

        AssetFileDescriptor fd = am.openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fd.getStartOffset();
        long declaredLength = fd.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
