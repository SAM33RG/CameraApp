package com.example.cameraapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.orhanobut.logger.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class PreviewCamera extends Fragment implements Handler.Callback, View.OnClickListener{
    private static final int MSG_CAMERA_SURFACE_CREATED = 0;
    private static final int MSG_CAMERA_DEVICE_OPENED = 1;
    private static final int MSG_SURFACE_SIZE_FOUND = 2;

    private Handler mHandler =  new Handler(this);

    private AutoFitSurFaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Surface mCameraSurface;
    private boolean mIsCameraSurfaceCreated = false;

    private CameraManager mCameraManager;
    private ArrayList<String> mCameraIdList;

    private ArrayList<Size>mCameraSizeList;
    ArrayList<String> mCameraSizeStringList = new ArrayList<>();

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraStateCallBack;
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;
    private CameraCaptureSession mCameraCaptureSession;



    private Spinner mCameraIdSpinner;
    private Spinner mCameraResSpinner;


    private boolean CAMERA_CONFIGURED = false;
    private String SELECTED_CAMERA_ID = null;
    private Size SELECTED_RESOLUTION = null;

    private ImageReader mCaptureImageReader = null;

    private Button mCaptureButton;


    public PreviewCamera() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


    }
    private  ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener (){
        @Override
        public void onImageAvailable(ImageReader reader) {
            saveRawImage(reader);
        }
    };

    private void saveRawImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        byte[] bytes = getJpegData(image); // step 8: get the jpeg data
        writeBytesToFile(bytes);
    }


    private void writeBytesToFile(byte[] input) {
        android.util.Log.e ("************************************","writeImageToFile");
        File file = getOutputFile();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(input);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    private byte[] getJpegData(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return byteArray;
    }

    private File getOutputFile() { // step 9: make the output file name
        File dir = new File(Environment.getExternalStorageDirectory().toString(), "MyPictures");
        if (!dir.exists()) {
            dir.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File imageFile = new File (dir.getPath() + File.separator + "PIC_"+timeStamp+".jpg");

        Log.d("*********************************","imagefilename="+imageFile.getAbsolutePath());

        return imageFile;
    }

    private void setupImageReader(int height, int width){
        mCaptureImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 3);
        mCaptureImageReader.setOnImageAvailableListener(onImageAvailableListener, mHandler);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mRootView = inflater.inflate(R.layout.fragment_preview_camera, container, false);

        mSurfaceView = mRootView.findViewById(R.id.surface_view);
        mCameraIdSpinner = mRootView.findViewById(R.id.spinner_camera_id);
        mCameraResSpinner = mRootView.findViewById(R.id.spinner_camera_resolution);
        mCaptureButton = mRootView.findViewById(R.id.capture_image_bt);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleCaptureImage();

            }
        });




        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                mCameraSurface = surfaceHolder.getSurface();
                mIsCameraSurfaceCreated = true;
                mHandler.sendEmptyMessage(MSG_CAMERA_SURFACE_CREATED);

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                mIsCameraSurfaceCreated = false;

            }
        });
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraIdList =  new ArrayList<String>(Arrays.asList(mCameraManager.getCameraIdList()));
            populateCameraIdSpinner();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return mRootView;

    }


    private void populateCameraIdSpinner() {

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),R.layout.support_simple_spinner_dropdown_item, mCameraIdList);
        mCameraIdSpinner.setAdapter(adapter);
        mCameraIdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SELECTED_CAMERA_ID = mCameraIdList.get(position);

                Logger.d("Camera id spinner selected id: "+ SELECTED_CAMERA_ID) ;
                populateCameraResSpinner(SELECTED_CAMERA_ID);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void populateCameraResSpinner(final String cameraId) {

        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mCameraSizeList =  new ArrayList<Size>(Arrays.asList(configs.getOutputSizes(SurfaceTexture.class))) ;
            mCameraSizeStringList.clear();
            for(Size s : mCameraSizeList){
//                Logger.d(""+s.getHeight()+"X"+s.getWidth());
                mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),R.layout.support_simple_spinner_dropdown_item, mCameraSizeStringList);
            mCameraResSpinner.setAdapter(adapter);
            mCameraResSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Logger.d("Camera res spinner current resolution : "+mCameraSizeStringList.get(position)+" of camera id: "+ cameraId);
                    setupImageReader(mCameraSizeList.get(position).getHeight(),mCameraSizeList.get(position).getWidth());
                    SELECTED_RESOLUTION = mCameraSizeList.get(position);
                    CAMERA_CONFIGURED =false;
                    closeCamera();
                    initializeCamera();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }



    private void closeCamera(){
        if(mCameraCaptureSession!=null)
            mCameraCaptureSession.close();
        if(mCameraDevice!=null)
            mCameraDevice.close();
    }



    @SuppressLint("MissingPermission")
    private void initializeCamera() {

        mCameraStateCallBack = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                Logger.d("camera state onOpened");

                mCameraDevice = cameraDevice;

                mHandler.sendEmptyMessage(MSG_CAMERA_DEVICE_OPENED);


            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {

                Logger.d("camera state onDisconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {

            }
        };

        try {
            mCameraManager.openCamera(SELECTED_CAMERA_ID, mCameraStateCallBack, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }
    private void handleCaptureImage() {
        if (mCameraDevice != null) {
            if (mCameraCaptureSession != null) {
                try {
                    CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureRequestBuilder.addTarget(mCaptureImageReader.getSurface());
                    mCameraCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                        }

                        @Override
                        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                            super.onCaptureProgressed(session, request, partialResult);
                        }

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                        }
                    }, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }



    private void startCamera() {

        mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                mCameraCaptureSession = cameraCaptureSession;

                try {
                    CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                    previewRequestBuilder.addTarget(mCameraSurface);

                    mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

            }
        };

        List<Surface> surfaceList = new ArrayList<Surface>();
        surfaceList.add(mCameraSurface);
        surfaceList.add(mCaptureImageReader.getSurface());

        try {
            mCameraDevice.createCaptureSession(surfaceList, mCameraCaptureSessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        switch (message.what) {
            case MSG_CAMERA_DEVICE_OPENED:
            case MSG_CAMERA_SURFACE_CREATED:
                if (mIsCameraSurfaceCreated && mCameraDevice != null && !CAMERA_CONFIGURED) {
//                    startCamera();
                    CAMERA_CONFIGURED =true;
                    mSurfaceHolder.setFixedSize(SELECTED_RESOLUTION.getWidth(),SELECTED_RESOLUTION.getHeight());
                    mSurfaceView.setAspectRatio(SELECTED_RESOLUTION.getHeight(),SELECTED_RESOLUTION.getWidth());
                     mHandler.sendEmptyMessage(MSG_SURFACE_SIZE_FOUND);

                }
                break;
            case MSG_SURFACE_SIZE_FOUND:
                startCamera();
                break;
        }
        return true;

    }


    @Override
    public void onClick(View view) {

        int id  = view.getId();
        switch (id){
            case R.id.capture_image_bt:
                break;
        }
    }
}
