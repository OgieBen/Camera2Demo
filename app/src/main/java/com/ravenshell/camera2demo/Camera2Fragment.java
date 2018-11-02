package com.ravenshell.camera2demo;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.ravenshell.camera2demo.view.PreviewTextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Camera2Fragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Camera2Fragment extends Fragment {


    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private ImageReader imageReader;
    private Semaphore cameraLock = new Semaphore(1);
    private CameraDevice cameraDevice;
    private File mfile;
    private int sensorOrientation;
    private boolean isRotated = false;


    private Size optimzedSize;

    private String mCameraId;

    private PreviewTextureView textureView;

    private final int  CAMERA_PERMISSION = 3;

    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private CaptureRequest previewCaptureRequest;
    private CameraCaptureSession cameraCaptureSession;



    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();

        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for (Size option : choices) {
            // check if maxHeight is greater than optionHeight and
            // if maxWidth is greater than optionWidth
            // check if optionHeight is equal to (optioHeight * aspecRatioHeight) / aspecRatioWidth
            // i.e if option height is going to be equal to newHeight
            // i.e calculate aspect ratio:

            // There is a simple formula for calculating aspect ratios:
            // aspectRatio = ( oldWidth / oldHeight ).
            // For instance if you want to know the new height of an object,
            // you can use: newHeight = ( newWidth / aspectRatio ),
            // and if you need the new width of an object,
            // you can use: newWidth = ( newHeight * aspectRatio )

            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w) {
               // if optionHeight and optionWidth are greater than textureViewWidth(or previewWidth)
                // and textureViewHeight add option to bigEnough array add to notBigEnough
                // array otherwise
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("Preview Error", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice _cameraDevice) {
            cameraLock.release();
            cameraDevice = _cameraDevice;

            // create camera preview session here
            cameraPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice _cameraDevice) {

            // release lock
            cameraLock.release();
            _cameraDevice.close();
            cameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice _cameraDevice, int i) {
            // release lock
            cameraLock.release();
            cameraDevice = null;
            Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    };

    // This callback is triggered when there is a still image to be saved by ImageReader
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            // save new Image on another thread
            mBackgroundHandler.post(new  ImageSaver(imageReader.acquireNextImage(), mfile));
        }
    };


    public Camera2Fragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment Camera2Fragment.
     */
    // TODO: Rename and change types and number of parameters
    public static Camera2Fragment newInstance() {
        Camera2Fragment fragment = new Camera2Fragment();
        return fragment;
    }

    private void startBackground() {
        mBackgroundThread = new HandlerThread("background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackground() {
        mBackgroundThread.quitSafely();

        try {
            // add thread back to pool
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.d("Interup ex", e.getMessage());
        }
    }

    private void cameraSetup(int width, int height) {
        Activity activity = getActivity();
        // Acquire CameraManager through getSystemService() method from current activity
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        // loop throught available cameraId

        // catch Nullpointer and CameraAccessException exceptions
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {

                // Retrieve camera characteristic using cameraId
                CameraCharacteristics cameraCharacteristics = (CameraCharacteristics) cameraManager.getCameraCharacteristics(cameraId);
                // Get current camera facing position
                Integer facingPosition = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                // If this is from camera move to the next camera
                if (facingPosition != null && facingPosition == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                /*  SCALER_STREAM_CONFIGURATION_MAP

                    The available stream configurations that this camera device supports;
                    also includes the minimum frame durations and the stall durations
                    for each format/size combination.

                    StreamConfigurationMap
                    Immutable class to store the available stream configurations to set up Surfaces
                    for creating a capture session with CameraDevice.createCaptureSession(SessionConfiguration).

                    This is the authoritative list for all output formats (and sizes respectively for that format)
                    that are supported by a camera device.

                    This also contains the minimum frame durations and stall durations for each format/size
                    combination that can be used to calculate effective frame rate when submitting multiple captures.
                */


                // This is the authoritative list for all output formats (and sizes respectively for that format)
                // that are supported by a camera device.
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // if map is null move on to the next camera
                // i.e if there is no available stream configuration
                // for this device.
                if (null == map) {
                    continue;
                }


                /** Size
                 * Immutable class for describing width and height dimensions in pixels.
                 * */
                // create an Arraylist to hold all sizes for ImageData.JPEG
                Size largest =
                        Collections.max(
                                /*
                                        public Size[] getOutputSizes (int format)

                                        Get a list of sizes compatible with the requested image format.
                                        The format should be a supported format (one of the formats returned by getOutputFormats()).
                                        As of API level 23, the getHighResolutionOutputSizes(int) method can be used on devices that support the BURST_CAPTURE capability
                                        to get a list of high-resolution output sizes that cannot operate at the preferred 20fps rate.
                                        This means that for some supported formats, this method will return an empty list,
                                        if all the supported resolutions operate at below 20fps. For devices that do not support the BURST_CAPTURE capability,
                                        all output resolutions are listed through this method.
                                        */
                                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());


                // ImageReader
                //
                // The ImageReader class allows direct application access to image data rendered into a Surface
                // Create a new reader for images of the desired size and format.
                //
                // The maxImages parameter determines the maximum number of Image objects
                // that can be be acquired from the ImageReader simultaneously. Requesting more
                // buffers will use up more memory, so it is important to use only the minimum number necessary for the use case.
                // The valid sizes and formats depend on the source of the image data.
                // If the format is PRIVATE, the created ImageReader will produce images
                // that are not directly accessible by the application. The application can still acquire images
                // from this ImageReader, and send them to the camera for reprocessing via ImageWriter interface.
                // However, the getPlanes() will return an empty array for PRIVATE format images.
                // The application can check if an existing reader's format by calling getImageFormat().
                // PRIVATE format ImageReaders (https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                //  are more efficient to use when application access to image
                // data is not necessary, compared to ImageReaders using other format such as YUV_420_888.

                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getWidth(), ImageFormat.JPEG, 2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, mBackgroundHandler);


                onRotationSwap(activity, cameraCharacteristics, map, largest, width, height);

                mCameraId = cameraId;

            }
        } catch (NullPointerException e) {

        } catch (CameraAccessException e) {

        }
    }


    private void onRotationSwap(Activity activity, CameraCharacteristics cameraCharacteristics, StreamConfigurationMap map, Size largest, int width, int height){
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        switch(rotation){
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if(sensorOrientation == 90 || sensorOrientation == 270){
                    isRotated = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if(sensorOrientation == 0 || sensorOrientation == 180){
                    isRotated = false;
                }
                break;
            default: Log.e("Rotation Error", "Invalid Rotation");
        }

        setDimensions(activity, map, width, height, largest);

    }

    public void setDimensions(Activity activity, StreamConfigurationMap map, int width, int height, Size largest){
        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

        int previewHeight = height;
        int previewWidth = width;

        int maxPreviewHeight = displaySize.y;
        int maxPreviewWidth = displaySize.x;

        // if screen is rotated
        // width becomes height
        // height becomes width

        if (isRotated){
            previewHeight = width;
            previewWidth = height;
            maxPreviewHeight = maxPreviewWidth;
            maxPreviewWidth = maxPreviewHeight;
        }

        if( maxPreviewHeight > MAX_PREVIEW_HEIGHT){
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        if(maxPreviewHeight > MAX_PREVIEW_WIDTH){
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        optimzedSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                previewWidth, previewHeight, maxPreviewWidth,
                maxPreviewHeight, largest);

        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE){
            // set Texture aspoect Ratio Here
            textureView.setAspectRatio(optimzedSize.getWidth(), optimzedSize.getHeight());
        }else{
            // swap height for width and width for height
            textureView.setAspectRatio(optimzedSize.getHeight(), optimzedSize.getWidth());
        }


    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == textureView || null == optimzedSize|| null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, optimzedSize.getHeight(), optimzedSize.getWidth());

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        // rotate matrix accordinly
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

            float scale = Math.max(
                    (float) viewHeight / optimzedSize.getHeight(),
                    (float) viewWidth / optimzedSize.getWidth());

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);

        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void requestCameraPermission(){
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getActivity(), "Error get Camera permission", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void openCamera(int width, int height){

        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestCameraPermission();
            return;
        }

        Activity activity = getActivity();
        cameraSetup(width, height);
        configureTransform(width, height);

        try{

            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

            if(!cameraLock.tryAcquire(2000, TimeUnit.MILLISECONDS)){
                throw new RuntimeException("Timedout waiting for lock");
            }
            manager.openCamera(mCameraId, cameraDeviceCallback, mBackgroundHandler);

        }catch(CameraAccessException e){
            Log.e("Camera Error", "Could not open camera "+ e.getMessage());
        }catch (InterruptedException e){
            Log.e("Camera Error", "Could not open camera "+ e.getMessage());
        }


    }

    private void cameraPreview(){

        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) {
                return;
            }

            texture.setDefaultBufferSize(optimzedSize.getWidth(), optimzedSize.getHeight());

            Surface surface = new Surface(texture);

            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewCaptureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession _cameraCaptureSession) {
                    cameraCaptureSession = _cameraCaptureSession;

                    try{

                        previewCaptureRequest = previewCaptureRequestBuilder.build();
                        cameraCaptureSession.setRepeatingRequest(previewCaptureRequest, new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        }, mBackgroundHandler);

                    }catch (CameraAccessException e){
                        displayToastMsg("Could not access camera for capture session");

                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    displayToastMsg("Failed to create capture session");
                }
            }, null);





        }catch (CameraAccessException e){
            displayToastMsg("Could not create camera preview");
        }
    }

    private void displayToastMsg(String msg){
        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public void onResume() {
        super.onResume();
        startBackground();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera2, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textureView = view.findViewById(R.id.texture_view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mfile = new File(getActivity().getExternalFilesDir(null), "preview.jpg");
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            // calculate area then return signum of the result
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

}
