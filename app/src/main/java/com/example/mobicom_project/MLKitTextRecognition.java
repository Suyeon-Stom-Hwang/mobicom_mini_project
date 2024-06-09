package com.example.mobicom_project;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.Image;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.CircularArray;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

public class MLKitTextRecognition extends AppCompatActivity {
    final String TAG = "MLKitTextRecognition";
    final private ImageView canvasView;
    final private ImageView capturedView;
    private TextView textScrollView;
    private Bitmap canvasBitmap;
    private Canvas boundingBoxCanvas;

    private final TextRecognizer recognizerKor;

    MLKitTextRecognition(ImageView canvas, ImageView capture, TextView textScroll) {
        Log.i(TAG, "MLKitTextRecognition: Constructor");
        recognizerKor = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        canvasView = canvas;
        capturedView = capture;
        textScrollView = textScroll;

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
        Log.i(TAG, "recognizeTextFromImage: rotation - " + rotation);
        InputImage image = InputImage.fromMediaImage(snapshot, rotation);
        capturedView.setImageBitmap(image.getBitmapInternal());
        return recognizerKor.process(image)
                .addOnSuccessListener(visionText -> {
                    Log.i(TAG, "onSuccess: " +  visionText.getText());
                    runOnUiThread(() -> drawBoundingBox(image.getWidth(), image.getHeight(), visionText));
                })
                .addOnFailureListener(e -> {
                    Log.i(TAG, "onFailure: " +  e);
                })
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete: " +  task);
                });
    }

    public Task<Text> recognizeTextFromImage(InputImage image) {
        capturedView.setImageBitmap(image.getBitmapInternal());
        return recognizerKor.process(image)
                .addOnSuccessListener(visionText -> {
                    Log.i(TAG, "onSuccess: " +  visionText.getText());
                    runOnUiThread(() -> drawBoundingBox(image.getWidth(), image.getHeight(), visionText));
                })
                .addOnFailureListener(e -> {
                    Log.i(TAG, "onFailure: " +  e);
                })
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete: " +  task);
                });
    }

    private void drawBoundingBox(int imageW, int imageH, Text output) {
        Log.i(TAG, "drawBoundingBox: imageW = " + imageW + ", imageH = " + imageH);

        int[] colors = {Color.RED, Color.BLUE, Color.BLACK, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.GRAY};
        int colorIndex = 0;
        int spannableIndex = 0;

        Paint bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.argb(0.6f, 1.0f, 1.0f, 1.0f));

        Paint strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.RED);
        strokePaint.setStrokeWidth(8);

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(140);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        SpannableStringBuilder builder = new SpannableStringBuilder(output.getText());
        for (Text.TextBlock block : output.getTextBlocks()) {
            Rect boundingBox = block.getBoundingBox();
            if (boundingBox != null) {
                Log.d(TAG, "drawBoundingBox: " + boundingBox);
                Rect fixedRect = rearrangePosition(imageW, imageH, boundingBox);
                boundingBoxCanvas.drawRect(fixedRect, bgPaint);
                strokePaint.setColor(colors[colorIndex]);
                boundingBoxCanvas.drawRect(fixedRect, strokePaint);
//                for (Text.Line line : block.getLines()) {
//                    Rect fixedLineRect = rearrangePosition(imageW, imageH, line.getBoundingBox());
//                    boundingBoxCanvas.drawText(line.getText(), fixedLineRect.centerX(), fixedLineRect.centerY() + 70, textPaint);
//
//                }
                builder.setSpan(new ForegroundColorSpan(colors[colorIndex]), spannableIndex, spannableIndex + block.getText().length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                spannableIndex += block.getText().length() + 1;
                colorIndex = (colorIndex + 1) % colors.length;
            }
        }
        textScrollView.setText(builder);
        canvasView.invalidate();
    }

    private Rect rearrangePosition(int imageW, int imageH, Rect boundingBox) {
        Log.i(TAG, "rearrangePosition: canvas = " + canvasView);
        float ratioW = (float) canvasView.getWidth() / imageW;
        float ratioH = (float) canvasView.getHeight() / imageH;
        final int padding = 20;
        return new Rect(
                (int) (boundingBox.left * ratioW) - padding,
                (int) (boundingBox.top * ratioH) - padding,
                (int) (boundingBox.right * ratioW) + padding,
                (int) (boundingBox.bottom * ratioH) + padding);
    }

    public void clearBoundingBox() {
        Log.i(TAG, "clearBoundingBox: ");
        canvasBitmap.eraseColor(Color.TRANSPARENT);
        capturedView.setImageBitmap(canvasBitmap);
    }
}
