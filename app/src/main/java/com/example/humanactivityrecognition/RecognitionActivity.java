package com.example.humanactivityrecognition;

import android.annotation.SuppressLint;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
    final long REQUIRED_DATA_FOR_INFERENCE = 90;
    final long INFERENCE_CYCLE_BY_DATA_POINT = 45;
    

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long timestampAtStart;
    private Interpreter activityRecognizer;
    private ArrayDeque<Acceleration> accelData;
    private ArrayList<String> activityLabels;
    private long dataPoint;
    LineChart lineChart;

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

        String modelName = "activity_recognition_model.tflite";
        activityRecognizer = createInterpreterFromTfliteModel(modelName);

        try {
            InputStream is = getResources().openRawResource(R.raw.activity_labels);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            activityLabels = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                activityLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
            }
        } catch (IOException e) {
            Log.e(TAG, "onCreate: Fail to read all activity labels");
        }
    }

    private Interpreter createInterpreterFromTfliteModel(String modelName) {
        MappedByteBuffer modelData;
        try {
            modelData = loadTfliteModel(this, modelName);
            Interpreter.Options options = new Interpreter.Options();
            Interpreter interpreter = new Interpreter(modelData, options);
            Log.i(TAG, "Success to load tflite model");
            Log.i(TAG, "Input shape : " + interpreter.getInputTensorCount());
            Tensor inputTensor = interpreter.getInputTensor(0);
            Log.d(TAG, " name:" + inputTensor.name() + " shape:" +
                    Arrays.toString(inputTensor.shape()) + " dtype:" + inputTensor.dataType());
            Log.i(TAG, "Output shape : " + interpreter.getOutputTensorCount());
            Tensor outputTensor = interpreter.getOutputTensor(0);
            Log.d(TAG, " name:" + outputTensor.name() + " shape:" +
                    Arrays.toString(outputTensor.shape()) + " dtype:" + outputTensor.dataType());

            return interpreter;
        } catch (Exception e) {
            Log.e(TAG, "Fail to load tflite file");
            Log.e(TAG, e.toString());
        }
        return null;
    }

    private MappedByteBuffer loadTfliteModel(Activity activity, String modelName) throws IOException {
        AssetManager am = activity.getAssets();

        AssetFileDescriptor fd = am.openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fd.getStartOffset();
        long declaredLength = fd.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
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
            lineChart.setVisibleXRangeMaximum(2000000000);
            lineChart.moveViewToX(timestampFromStart);
        });
    }

    private void recognizeActivity() {
        int[] inputShape = activityRecognizer.getInputTensor(0).shape();
//         Input dimension = 1 * 90 * 3 * 1
        float[][][][] rawInput = new float [inputShape[0]][inputShape[1]][inputShape[2]][inputShape[3]];
        Iterator<Acceleration> iterator = accelData.iterator();

        for (int i = 0; iterator.hasNext(); i++) {
            Acceleration accel = iterator.next();
            rawInput[0][i][0][0] = accel.x;
            rawInput[0][i][1][0] = accel.y;
            rawInput[0][i][2][0] = accel.z;
        }

        int[] outputShape = activityRecognizer.getOutputTensor(0).shape();
//        Output dimension = 1 * 6
        float[][] rawOutput = new float [outputShape[0]][outputShape[1]];

        activityRecognizer.run(rawInput, rawOutput);
        float maxConfidence = 0;
        int activityClass = 0;
        for (int i = 0; i < 6; i++) {
            Log.i(TAG, "recognizeActivity: " + activityLabels.get(i) + " / " + rawOutput[0][i]);
            if (rawOutput[0][i] > maxConfidence) {
                maxConfidence = rawOutput[0][i];
                activityClass = i;
            }
        }

        int finalActivityClass = activityClass;
        float finalMaxConfidence = maxConfidence;
        runOnUiThread(() -> {
            TextView activityText = findViewById(R.id.activity_text);
            activityText.setText(activityLabels.get(finalActivityClass));

            TextView confidenceText = findViewById(R.id.recognition_confidence_text);
            confidenceText.setText(String.valueOf(finalMaxConfidence));
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

        lineChart.clear();
        lineChart.setData(createData());
        lineChart.invalidate();

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void onEndButtonClicked(View view) {
        sensorManager.unregisterListener(this);
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
