package com.example.humanactivityrecognition;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayDeque;
import java.util.Iterator;

class Acceleration {
    public float x;
    public float y;
    public float z;

    public Acceleration(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

public class RecognitionActivity extends AppCompatActivity implements SensorEventListener {
    final String TAG = "RecognitionActivity";
    final String MODEL_NAME = "activity_recognition_model.tflite";
    final long REQUIRED_DATA_FOR_INFERENCE = 90;
    final long INFERENCE_CYCLE_BY_DATA_POINT = 45;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long timestampAtStart;
    private ModelManager activityRecognizer;
    private ArrayDeque<Acceleration> accelData;
    private long dataPoint;
    private LineChart lineChart;
    private TextView activityText;
    private TextView confidenceText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "OnCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

//        Toolbar setup
        Toolbar toolbar = findViewById(R.id.recognition_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

//        View setup
        activityText = findViewById(R.id.activity_text);
        confidenceText = findViewById(R.id.recognition_confidence_text);

//        Sensor setup
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

//        Chart setup by MPAndroidChart
        lineChart = findViewById(R.id.accel_chart);
        lineChart.getDescription().setEnabled(false);
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @SuppressLint("DefaultLocale")
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
        lineChart.invalidate();

        activityRecognizer = new ModelManager(this, MODEL_NAME, R.raw.activity_labels);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestampFromStart = event.timestamp - timestampAtStart;
        long timestampInMs = timestampFromStart / 1000000;
        long timeStampInSec = timestampInMs / 1000;
        long milliSecond = timestampInMs % 1000;
        long minute = timeStampInSec / 60;
        long second = timeStampInSec % 60;
        Log.d(TAG, String.format("accel data = t-%d:%d:%d, x-%f, y-%f, z-%f", minute, second, milliSecond, event.values[0], event.values[1], event.values[2]));

        if (accelData.size() == REQUIRED_DATA_FOR_INFERENCE) {
            accelData.remove();
        }

        accelData.add(new Acceleration(
                event.values[0],
                event.values[1],
                event.values[2]
        ));

        dataPoint++;
        if (dataPoint % INFERENCE_CYCLE_BY_DATA_POINT == 0 && accelData.size() == REQUIRED_DATA_FOR_INFERENCE) {
            Log.d(TAG, "onSensorChanged: Inference at data points - " + dataPoint);
            recognizeActivity();
        }

        runOnUiThread(() -> {
            LineData lineData = lineChart.getLineData();
            lineData.addEntry(new Entry(timestampFromStart, event.values[0]), 0);
            lineData.addEntry(new Entry(timestampFromStart, event.values[1]), 1);
            lineData.addEntry(new Entry(timestampFromStart, event.values[2]), 2);
            lineData.notifyDataChanged();

            lineChart.notifyDataSetChanged();
            lineChart.setVisibleXRange(2000000000, 2000000000);
            lineChart.moveViewToX(timestampFromStart);
        });
    }

    private void recognizeActivity() {
        int[] inputShape = activityRecognizer.getInputShape();
//         Input dimension = 1 * 90 * 3 * 1
        float[][][][] rawInput = new float [inputShape[0]][inputShape[1]][inputShape[2]][inputShape[3]];
        Iterator<Acceleration> iterator = accelData.iterator();

        for (int i = 0; iterator.hasNext(); i++) {
            Acceleration accel = iterator.next();
            rawInput[0][i][0][0] = accel.x;
            rawInput[0][i][1][0] = accel.y;
            rawInput[0][i][2][0] = accel.z;
        }

        final Pair<String, Float> result = activityRecognizer.inferenceData(rawInput);
        runOnUiThread(() -> {
            activityText.setText(result.first);
            confidenceText.setText(String.format("%.5f", result.second));

            ImageView activityImage = findViewById(R.id.activity_image);
//            getResources().(R.raw.downstairs);
//            activityImage.setImageURI(R.raw.downstairs);
        });
    }

    public void onStartButtonClicked(View view) {
        if (activityRecognizer == null) {
            Log.e(TAG, "onStartButtonClicked: interpreter is null");
            return;
        }

        Log.i(TAG, "onStartButtonClicked: Start to inference");
        dataPoint = 0;
        timestampAtStart = SystemClock.elapsedRealtimeNanos();
        accelData = new ArrayDeque<>();

        lineChart.setData(createData());
        lineChart.invalidate();

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void onEndButtonClicked(View view) {
        sensorManager.unregisterListener(this);

        lineChart.resetZoom();
        lineChart.resetViewPortOffsets();
        lineChart.clear();
        lineChart.invalidate();

        activityText.setText(R.string.default_result);
        confidenceText.setText("0");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Log.i(TAG, "onOptionsItemSelected: Back button on toolbar is clicked");
            finish();
            return true;
        } else {
            Log.i(TAG, "onOptionsItemSelected: Unhandled event - " + item.getItemId());
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged: " + accuracy);
    }
}
