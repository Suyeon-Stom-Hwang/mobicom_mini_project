package com.example.mobicom_project;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

public class MLKitTextRecognition extends AppCompatActivity {
    final String TAG = "MLKitTextRecognition";
    final private ImageView canvasView;
    private Bitmap canvasBitmap;
    private Canvas boundingBoxCanvas;

    private final TextRecognizer recognizerKor;

    MLKitTextRecognition(ImageView canvas) {
        Log.i(TAG, "MLKitTextRecognition: Constructor");
        recognizerKor = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        canvasView = canvas;

        this.canvasView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Log.e(TAG, "MLKitTextRecognition: Constructor - W:" + canvasView.getWidth() + ", H:" + canvasView.getHeight());

                canvasBitmap = Bitmap.createBitmap(canvasView.getWidth(), canvasView.getHeight(), Bitmap.Config.ARGB_8888);
                boundingBoxCanvas = new Canvas(canvasBitmap);
                boundingBoxCanvas.drawColor(Color.TRANSPARENT);
                canvasView.setImageBitmap(canvasBitmap);

                canvasView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    public Task<Text> recognizeTextFromImage(Image snapshot, int rotation) {
        InputImage image = InputImage.fromMediaImage(snapshot, rotation);
        return recognizerKor.process(image)
                .addOnSuccessListener(visionText -> {
                    Log.i(TAG, "onSuccess: " +  visionText.getText());
                    drawBoundingBox(image.getWidth(), image.getHeight(), visionText);
                })
                .addOnFailureListener(e -> {
                    Log.i(TAG, "onFailure: " +  e);
                })
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete: " +  task);
                });
    }

    private void drawBoundingBox(int imageW, int imageH, Text output) {
        canvasBitmap.eraseColor(Color.TRANSPARENT);

        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(Color.RED);
        boxPaint.setStrokeWidth(10);
        for (Text.TextBlock block : output.getTextBlocks()) {
            Rect boundingBox = block.getBoundingBox();

            Log.d(TAG, "drawBoundingBox: " + boundingBox);

            if (boundingBox != null) {
                Rect fixedRect = rearrangePosition(imageW, imageH, boundingBox);
                boundingBoxCanvas.drawRect(fixedRect, boxPaint);
            }
        }
    }

    private Rect rearrangePosition(int imageW, int imageH, Rect boundingBox) {
        float ratioW = (float) canvasView.getWidth() / imageW;
        float ratioH = (float) canvasView.getHeight() / imageH;

        return new Rect(
                (int) (boundingBox.left * ratioW),
                (int) (boundingBox.top * ratioH),
                (int) (boundingBox.right * ratioW),
                (int) (boundingBox.bottom * ratioH));
    }
}
