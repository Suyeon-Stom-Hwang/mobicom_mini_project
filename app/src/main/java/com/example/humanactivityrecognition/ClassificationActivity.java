package com.example.humanactivityrecognition;

import static org.opencv.imgproc.Imgproc.threshold;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ClassificationActivity extends AppCompatActivity {
    final String TAG = "ClassificationActivity";
    String[] PERMISSIONS = new String[]{android.Manifest.permission.CAMERA};

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageView capturedImage;
    private ModelManager textDetection;
    private ModelManager textRecognition;
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

        textDetection = new ModelManager(this, "craft_float_640.tflite");
        textRecognition = new ModelManager(this, "crnn_dr.tflite");
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
        Log.i(TAG, "onCaptureButtonClicked: " + frameBuffer.getWidth() + "," + frameBuffer.getHeight());
        Bitmap preprocessedBitmap = cropAndRotateBitmap(frameBuffer, 0, 0, 0,
                frameBuffer.getWidth(), frameBuffer.getHeight());
        capturedImage.setImageBitmap(preprocessedBitmap);
        Log.i(TAG, "onCaptureButtonClicked: " + preprocessedBitmap.getWidth() + "," + preprocessedBitmap.getHeight());

        int[] inputShape = textDetection.getInputShape();
        Mat normalizeMat = resizeAndNormalizeMat(preprocessedBitmap, inputShape[2], inputShape[3]);
        Log.i(TAG, "onCaptureButtonClicked: " + Arrays.toString(inputShape));
        Log.i(TAG, "onCaptureButtonClicked: " + normalizeMat);

//        Input dimension = 1 * 3 * 640 * 416
        float[][][][] rawInput = new float[inputShape[0]][inputShape[1]][inputShape[2]][inputShape[3]];

        for (int i = 0; i < inputShape[2]; i++) {
            for (int j = 0; j < inputShape[3]; j++) {
                double[] current = normalizeMat.get(j, i);
                rawInput[0][0][i][j] = (float) current[0];
                rawInput[0][1][i][j] = (float) current[1];
                rawInput[0][2][i][j] = (float) current[2];
            }
        }

//        0 -> text score, 1 -> link score
        Mat result = textDetection.inferenceData(rawInput);
        Log.i(TAG, "onCaptureButtonClicked: " + result);

        runOnUiThread(() -> {
        });
    }

    public static List<MatOfPoint> getDetBoxes(Mat textMap, Mat linkMap, double textThreshold, double linkThreshold, double lowText) {
        // Prepare data
        Mat linkmapCopy = linkMap.clone();
        Mat textmapCopy = textMap.clone();
        int imgH = textMap.rows();
        int imgW = textMap.cols();

        // Labeling method
        Mat textScore = new Mat();
        Core.compare(textMap, new Scalar(lowText), textScore, Core.CMP_GT);
        Mat linkScore = new Mat();
        Core.compare(linkMap, new Scalar(linkThreshold), linkScore, Core.CMP_GT);
        Mat textScoreComb = new Mat();
        Core.add(textScore, linkScore, textScoreComb);
        Core.minMaxLoc(textScoreComb);

        // Connected components
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int connectivity = 4;
        int nLabels = Imgproc.connectedComponentsWithStats(textScoreComb, labels, stats, centroids, connectivity);

        List<MatOfPoint> det = new ArrayList<>();
        for (int k = 1; k < nLabels; k++) {
            // Size filtering
            double size = stats.get(k, Imgproc.CC_STAT_AREA)[0];
            if (size < 10) continue;

            // Thresholding
            double maxTextScore = Core.minMaxLoc(textMap.submat(labels).reshape(1)).maxVal;
            if (maxTextScore < textThreshold) continue;

            // Make segmentation map
            Mat segmap = new Mat(textMap.size(), textMap.type(), Scalar.all(0));
            segmap.setTo(new Scalar(255), labels == k);
            Core.bitwise_and(segmap, linkScore, segmap);
            Core.dilate(segmap, segmap, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));

            // Make box
            List<Point> npContours = new ArrayList<>();
            Core.findNonZero(segmap, npContours);
            MatOfPoint contour = new MatOfPoint();
            contour.fromList(npContours);
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            RotatedRect rectangle = Imgproc.minAreaRect(contour2f);
            Point[] boxPoints = new Point[4];
            rectangle.points(boxPoints);
            MatOfPoint box = new MatOfPoint(boxPoints);

            // Align diamond shape
            double w = Core.norm(box.get(0, 0), box.get(1, 0));
            double h = Core.norm(box.get(1, 0), box.get(2, 0));
            double boxRatio = Math.max(w, h) / (Math.min(w, h) + 1e-5);
            if (Math.abs(1 - boxRatio) <= 0.1) {
                double l = Core.min(contour2f.get(0, 0)).x;
                double r = Core.max(contour2f.get(0, 0)).x;
                double t = Core.min(contour2f.get(0, 1)).y;
                double b = Core.max(contour2f.get(0, 1)).y;
                box = new MatOfPoint(new Point(l, t), new Point(r, t), new Point(r, b), new Point(l, b));
            }

            // Make clockwise order
            int startIdx = (int) Core.minMaxLoc(box.reshape(1)).minLoc.x;
            Core.rotate(box, box, 4 - startIdx);
            det.add(box);
        }

        return det;
    }

    private Bitmap cropAndRotateBitmap(Bitmap frameBuffer, int degree, int x, int y, int width, int height) {
        Matrix matrix = new Matrix();
//        matrix.postRotate(degree);
        return Bitmap.createBitmap(
                frameBuffer,
                x,
                y,
                width,
                height,
                matrix,
                false);
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
