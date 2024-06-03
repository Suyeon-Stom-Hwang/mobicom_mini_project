package com.example.humanactivityrecognition;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.Pair;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;

public class ModelManager {
    final static String TAG = "TfLiteModelManager";
    final private Interpreter interpreter;
    final private Activity activity;

    public ModelManager(Activity activity, String modelFileName) {
        this.activity = activity;
        interpreter = createInterpreterFromTfliteModel(modelFileName);
    }

    private Interpreter createInterpreterFromTfliteModel(String modelName) {
        MappedByteBuffer modelData;
        try {
            modelData = loadTfliteModel(activity, modelName);
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

    public int[] getInputShape() {
        return interpreter.getInputTensor(0).shape();
    }

    public Mat inferenceData(float[][][][] rawInput) {
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        float[][][][] rawOutput = new float [outputShape[0]][outputShape[1]][outputShape[2]][outputShape[3]];

        Mat result = new Mat(outputShape[1], outputShape[2], CvType.CV_32F);
        result.put(0, 0, rawOutput[0]);

        return result;
    }
}
