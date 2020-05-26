package com.example.cameraapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Message;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class PreviewCamera extends Fragment implements Handler.Callback{
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mRootView = inflater.inflate(R.layout.fragment_preview_camera, container, false);

        mSurfaceView = mRootView.findViewById(R.id.surface_view);
        mCameraIdSpinner = mRootView.findViewById(R.id.spinner_camera_id);
        mCameraResSpinner = mRootView.findViewById(R.id.spinner_camera_resolution);

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
                    SELECTED_RESOLUTION = mCameraSizeList.get(position);
                    CAMERA_CONFIGURED =false;
                    if(mCameraCaptureSession!=null)
                        mCameraCaptureSession.close();
                    if(mCameraDevice!=null)
                        mCameraDevice.close();
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


}
