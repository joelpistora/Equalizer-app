package com.example.android.signallab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Locale;

public class VideoActivity extends AppCompatActivity {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static final String TAG = "VideoActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private ImageView processedImageView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler mainHandler;
    private int sensorOrientation;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        mainHandler = new Handler(Looper.getMainLooper());
        processedImageView = findViewById(R.id.processedImageView);
    }
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        checkAndRequestPermissions();
    }
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    private void checkAndRequestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
        } else {
            openCamera();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted — open camera");
                openCamera();
            } else {
                Log.e(TAG, "Permission denied");
            }
        }
    }

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted; cannot open camera.");
            return;
        }
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            //Select camera
            String[] cameraIds = cameraManager.getCameraIdList();
            cameraId = null;
            for (String id : cameraIds) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer lensFacing = chars.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null &&
                        lensFacing == CameraCharacteristics.LENS_FACING_BACK) { //Back facing camera
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                Log.e(TAG, "No back-facing camera found");
                return;
            }

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                Log.e(TAG, "StreamConfigurationMap is null");
                return;
            }

            //Print available image size and frame rates
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            for (int i = 0; i < sizes.length; i++) {
                Size size = sizes[i];
                Log.d(TAG, "Supported Preview size[" + i + "]: "
                        + size.getWidth() + " x " + size.getHeight());
            }
            Size[] yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            for (int i = 0; i < yuvSizes.length; i++) {
                Size size = yuvSizes[i];
                Log.d(TAG, "Supported YUV size[" + i + "]: "
                        + size.getWidth() + " x " + size.getHeight());
            }
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            for (Range<Integer> range : fpsRanges) {
                Log.d(TAG, "Supported FPS range: " + range.getLower() + "-" + range.getUpper());
            }

            // Select desired image size
            int desiredIndex = 26; // example: 176x144 on Samsung Galaxy 36A
            Size previewSize = null;
            if (desiredIndex >= 0 && desiredIndex < sizes.length) {
                previewSize = sizes[desiredIndex];
            } else {
                previewSize = sizes[sizes.length - 1];
            }

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 3);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, backgroundHandler);


            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening camera", e);
        }
    }

    private void createCameraCaptureSession() {
        try {
            Surface imageSurface = imageReader.getSurface();
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(imageSurface);

            //Set target frame rate (fps)
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Range<Integer> targetFpsRange = null;
            for (Range<Integer> range : fpsRanges) {
                if (range.getLower() == 30 && range.getUpper() == 30) { //Pick 30fps if available
                    targetFpsRange = range;
                    break;
                }
            }
            if (targetFpsRange != null) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange);
                Log.d(TAG, "FPS set to: " +  targetFpsRange);
            } else {
                Log.w(TAG, "Target FPS range not found, using default.");
            }

            cameraDevice.createCaptureSession(Collections.singletonList(imageSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Error starting camera preview", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating camera capture session", e);
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }


    private void onImageAvailable(ImageReader reader) {
        try (Image image = reader.acquireLatestImage()) {
            if (image != null) {
                processImage(image);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }

    private void processImage(Image image) {
        Bitmap bitmap = yuvToRgbBitmap(image);
        if (bitmap != null) {

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

           //Loop through all pixels in image
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];

                int a = (c >> 24) & 0xFF;
                //int r = 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                int R = 0xFF - r;
                int G = 0xFF - g;
                int B = 0xFF - b;

                int gray =  (r + g + b) / 3;

                // Add code to modify image by changing pixel RGB value her

                pixels[i] = (a << 24) | (gray << 16) | (gray << 8) | gray;
            }

            int squareW = 200;
            int squareH = 200;

            for (int x = width - squareW; x < width; x++) {
                for (int y = 0; y < squareH; y++) {
                    pixels[y * width + x] = (0xFF << 24) | (0 << 16) | (0xFF << 8) | 0;
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            mainHandler.post(() -> processedImageView.setImageBitmap(bitmap));

        }
    }

    private Bitmap yuvToRgbBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int[] out = new int[width * height];

        for (int y = 0; y < height; y++) {
            int yOffset = yRowStride * y;
            int uvOffset = uvRowStride * (y / 2);

            for (int x = 0; x < width; x++) {
                int yValue = yBuffer.get(yOffset + x) & 0xff;

                int uvIndex = uvOffset + (x / 2) * uvPixelStride;
                int uValue = (uBuffer.get(uvIndex) & 0xff) - 128;
                int vValue = (vBuffer.get(uvIndex) & 0xff) - 128;

                int r = (int) (yValue + 1.403f * vValue);
                int g = (int) (yValue - 0.344f * uValue - 0.714f * vValue);
                int b = (int) (yValue + 1.770f * uValue);

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                out[y * width + x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int deviceRotation = ORIENTATIONS.get(rotation);

        int totalRotation = (sensorOrientation + deviceRotation) % 360;

        return rotateBitmap(bitmap, totalRotation);

    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(
                source,
                0,
                0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );
    }
}

