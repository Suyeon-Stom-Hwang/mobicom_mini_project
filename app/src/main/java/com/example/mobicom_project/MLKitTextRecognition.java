package com.example.mobicom_project;

import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.ExperimentalGetImage;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
//import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.util.concurrent.ExecutionException;

public class MLKitTextRecognition extends AppCompatActivity {
    final String TAG = "MLKitTextRecognition";

//    private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final TextRecognizer recognizerKor;
//    private ProcessCameraProvider cameraProvider;
    private boolean recognitionRequested;

    MLKitTextRecognition() {
        Log.i(TAG, "MLKitTextRecognition: Contructor");
        recognizerKor = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        recognitionRequested = false;
    }

//    MLKitTextRecognition(PreviewView previewView) {
//        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
//        cameraProviderFuture.addListener(() -> {
//            try {
//                cameraProvider = cameraProviderFuture.get();
//                bindPreview(cameraProvider, previewView);
//            } catch (ExecutionException | InterruptedException e) {
//                Log.e(TAG, "Fail to get camera provider");
//            }
//        }, ContextCompat.getMainExecutor(this));
//
//        recognizerKor = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
//    }

//    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider, PreviewView previewView) {
//        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
//                .build();
//        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
//            @OptIn(markerClass = ExperimentalGetImage.class) Image snapshot = imageProxy.getImage();
//            if (snapshot != null && recognitionRequested) {
//                InputImage image = InputImage.fromMediaImage(snapshot, imageProxy.getImageInfo().getRotationDegrees());
//                Log.i(TAG, "MLkit version: " + image.getWidth() + "," + image.getHeight());
//                Task<Text> result = recognizerKor.process(image)
//                        .addOnSuccessListener(visionText -> {
//                            Log.i(TAG, "onSuccess: " +  visionText.getText());
//                        })
//                        .addOnFailureListener(e -> {
//                            Log.i(TAG, "onFailure: " +  e);
//                        })
//                        .addOnCompleteListener(task -> {
//                            Log.i(TAG, "onComplete: " +  task);
//                            imageProxy.close();
//                        });
//                recognitionRequested = false;
//            } else {
//                imageProxy.close();
//            }
//        });
//
////        Initialize Preview
//        Preview preview = new Preview.Builder().build();
//        preview.setSurfaceProvider(previewView.getSurfaceProvider());
//
////        Initialize CameraSelector
//        CameraSelector cameraSelector = new CameraSelector.Builder()
//                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                .build();
//
////        Bind life cycle
//        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
//    }

    public void requestTextRecognition() {
        recognitionRequested = true;
    }

    public Task<Text> recognizeTextFromImage(Image snapshot, int rotation) {
        InputImage image = InputImage.fromMediaImage(snapshot, rotation);
        return recognizerKor.process(image)
                .addOnSuccessListener(visionText -> {
                    Log.i(TAG, "onSuccess: " +  visionText.getText());
                })
                .addOnFailureListener(e -> {
                    Log.i(TAG, "onFailure: " +  e);
                })
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete: " +  task);
                });
    }
}
