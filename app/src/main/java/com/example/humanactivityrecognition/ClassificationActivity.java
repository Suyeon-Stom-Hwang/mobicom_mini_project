package com.example.humanactivityrecognition;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.ExecutionException;

public class ClassificationActivity extends AppCompatActivity {
    final String TAG = "ClassificationActivity";
    final String MODEL_NAME = "classification_model.tflite";
    String[] PERMISSIONS = new String[]{android.Manifest.permission.CAMERA};

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageView capturedImage;
    private ModelManager objectClassifier;
    private Bitmap frameBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "OnCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);

//        Toolbar setup
        Toolbar toolbar = findViewById(R.id.classification_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

//        ImageView setup
        capturedImage = findViewById(R.id.captured_image);

//        Camera preview setup
        previewView = findViewById(R.id.camera_preview);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Fail to get camera provider");
            }
        }, ContextCompat.getMainExecutor(this));
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1);

        objectClassifier = new ModelManager(this, MODEL_NAME, R.raw.imagenet_labels);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindPreview(cameraProvider);
                Toast.makeText(getApplicationContext(), "Allow", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
            }
        }
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
//        Initialize ImangeAnalysis
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            if (frameBuffer == null) {
                frameBuffer = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            }

            frameBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
            image.close();
        });

//        Initialize Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

//        Initialize CameraSelector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

//        Bind life cycle
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
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

    public void onCaptureButtonClicked(View view) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap preprocessedBitmap = cropAndRotateBitmap(frameBuffer, 90, 80, 0,
                frameBuffer.getHeight(), frameBuffer.getHeight());
        capturedImage.setImageBitmap(preprocessedBitmap);

        int[] inputShape = objectClassifier.getInputShape();
        Mat normalizeMat = resizeAndNormalizeMat(preprocessedBitmap, inputShape[1], inputShape[2]);

//        Input dimension = 1 * 224 * 224 * 3
        float[][][][] rawInput = new float[inputShape[0]][inputShape[1]][inputShape[2]][inputShape[3]];

        for (int i = 0; i < inputShape[1]; i++) {
            for (int j = 0; j < inputShape[2]; j++) {
                double[] current = normalizeMat.get(i, j);
                rawInput[0][i][j][0] = (float) current[0];
                rawInput[0][i][j][1] = (float) current[1];
                rawInput[0][i][j][2] = (float) current[2];
            }
        }

        final Pair<String, Float> result = objectClassifier.inferenceData(rawInput);
        Log.i(TAG, "onCaptureButtonClicked: " + result.first + " / " + result.second);

        runOnUiThread(() -> {
            TextView objectClassText = findViewById(R.id.object_class_text);
            objectClassText.setText(result.first);

            TextView confidenceText = findViewById(R.id.classification_confidence_text);
            confidenceText.setText(String.format("%.5f", result.second));
        });
    }

    private Bitmap cropAndRotateBitmap(Bitmap frameBuffer, int degree, int x, int y, int width, int height) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(
                frameBuffer,
                x,
                y,
                width,
                height,
                matrix,
                true);
    }

    private Mat resizeAndNormalizeMat(Bitmap frameBuffer, int width, int height) {
        Mat mat = new Mat();
        Utils.bitmapToMat(frameBuffer, mat);

        Mat resizedMat = new Mat();
        Imgproc.resize(mat, resizedMat, new Size(width, height));

        Mat normalizedMat = new Mat();
        resizedMat.convertTo(normalizedMat, CvType.CV_32FC4, 1 / 255., 0);

        Mat rescaledMat = new Mat();
        normalizedMat.convertTo(rescaledMat, CvType.CV_32FC4, 2, -1);

        return rescaledMat;
    }
}
