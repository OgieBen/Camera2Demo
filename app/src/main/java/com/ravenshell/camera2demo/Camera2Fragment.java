package com.ravenshell.camera2demo;


import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Camera2Fragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Camera2Fragment extends Fragment {


    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private Semaphore cameraLock = new Semaphore(1);
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice _cameraDevice) {
            cameraLock.release();
            cameraDevice = _cameraDevice;

            // create camera preview session here

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

    private void cameraSetup() {
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

                ImageReader reader = ImageReader.newInstance(largest.getWidth(), largest.getWidth(), ImageFormat.JPEG, 2);


            }
        } catch (NullPointerException e) {

        } catch (CameraAccessException e) {

        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera2, container, false);
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

}
