package com.example.cameraapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
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
import android.widget.Toast;

import com.orhanobut.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 */
public class RecordVideo extends Fragment implements Handler.Callback, FragmentLifecycle {

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

    private ArrayList<Size> mCameraSizeListKnownAsRatio;
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

    private boolean mIsRecordingVideo;

    private MediaRecorder mMediaRecorder;

    private static final String mDirectory = "CustomCamera";
    private String mRecentVideoName = null;

    private Button mVideoRecordButton;
    private Button mOpenGalleryButton;

    private View mCameraSettings;

    public RecordVideo() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mRootView = inflater.inflate(R.layout.fragment_record_video, container, false);
        mSurfaceView = mRootView.findViewById(R.id.surface_view);
        mCameraIdSpinner = mRootView.findViewById(R.id.spinner_camera_id);
        mCameraResSpinner = mRootView.findViewById(R.id.spinner_camera_resolution);
        mVideoRecordButton = mRootView.findViewById(R.id.capture_image_bt);
        mOpenGalleryButton = mRootView.findViewById(R.id.open_gallery_bt);
        mCameraSettings = mRootView.findViewById(R.id.camera_settings_layout);


        mOpenGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(mRecentVideoName==null)
                    return;
                openGallery();
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

        mVideoRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsRecordingVideo) {
                    startVideoRecording();
                    mCameraSettings.setVisibility(View.GONE);
                } else {
                    stopVideoRecording();
                    mCameraSettings.setVisibility(View.VISIBLE);

                }
            }
        });



        return mRootView;

    }

    @Override
    public void onPauseFragment() {
        closeCamera();
    }

    @Override
    public void onResumeFragment() {

        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraIdList =  new ArrayList<String>(Arrays.asList(mCameraManager.getCameraIdList()));
            populateCameraIdSpinner();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    private void startVideoRecording() {

        if (mCameraDevice != null) {

            closeCameraOnlySession();

            setupMediaRecorder();


            final CaptureRequest.Builder recordingBuilder;
            try {
                List<Surface> surfaceList = new ArrayList<Surface>();

                recordingBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

                surfaceList.add(mCameraSurface);
                recordingBuilder.addTarget(mCameraSurface);

                surfaceList.add(mMediaRecorder.getSurface());
                recordingBuilder.addTarget(mMediaRecorder.getSurface());

                mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        mCameraCaptureSession = session;

                        try {
                            mCameraCaptureSession.setRepeatingRequest(recordingBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        Thread mediaRecorderThread = new Thread() {
                            @Override
                            public void run() {
                                mMediaRecorder.start();
                            }
                        };
                        mediaRecorderThread.start();


                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoRecordButton.setText("stop");
                                mIsRecordingVideo = true;
                            }
                        });
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }
                }, mHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    private void stopVideoRecording() {
        closeCameraOnlySession();
        startCameraPreview();
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mOpenGalleryButton.setVisibility(View.VISIBLE);
                mVideoRecordButton.setText("Record");
                mIsRecordingVideo = false;
            }
        });
    }

    private void openGallery(){

        if(mRecentVideoName==null){
            Toast.makeText(getContext(),"NO Image captured",Toast.LENGTH_SHORT).show();
            return;
        }
        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(sdDir, mDirectory);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.withAppendedPath(Uri.fromFile(file),mRecentVideoName), "video/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
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


    private boolean matchAspectRatio(Size s,Size aspectRatio){
        float f  = Math.abs(((float)s.getWidth()/(float) s.getHeight())-((float)aspectRatio.getWidth()/(float)aspectRatio.getHeight()));
        if(f<0.000001){
            return true;
        }
        return false;
    }

    private void populateCameraResSpinner(final String cameraId) {

        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            ArrayList<Size> allSizeList = new ArrayList<Size>(Arrays.asList(configs.getOutputSizes(SurfaceTexture.class))) ;
            mCameraSizeListKnownAsRatio =  new ArrayList<>() ;
            mCameraSizeStringList.clear();
            for(Size s : allSizeList){
                if(matchAspectRatio(s,new Size(16,9)) && s.getHeight()<=1080){
                    mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth()+" 9:16");
                    mCameraSizeListKnownAsRatio.add(s);

                }

//                Logger.d("h "+s.getHeight()+"X w "+s.getWidth()+" "+(float)s.getWidth()/ (float)s.getHeight());
//                mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),R.layout.support_simple_spinner_dropdown_item, mCameraSizeStringList);
            mCameraResSpinner.setAdapter(adapter);
            mCameraResSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Logger.d("Camera res spinner current resolution : "+mCameraSizeStringList.get(position)+" of camera id: "+ cameraId);

                    SELECTED_RESOLUTION = mCameraSizeListKnownAsRatio.get(position);
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

    private File getOutputFile() {
        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(sdDir, mDirectory);
        if (!dir.exists()) {
            dir.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File videoFile = new File (dir.getPath() + File.separator + "VID_"+timeStamp+".mp4");
        mRecentVideoName = videoFile.getName();

        Logger.d("file saved : "+videoFile.getAbsolutePath());
        Toast.makeText(getContext(),"Video saving in "+mDirectory+".", Toast.LENGTH_SHORT).show();

        return videoFile;
    }

    private void setupMediaRecorder() {

        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
            int orientation = 90;
            try {
                orientation = mCameraManager.getCameraCharacteristics(SELECTED_CAMERA_ID).get(CameraCharacteristics.SENSOR_ORIENTATION);
                Logger.d("camera orientaion :" + orientation);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            mMediaRecorder.setOutputFile(getOutputFile().getAbsolutePath());

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            CamcorderProfile profile = null;

            if(SELECTED_RESOLUTION.getHeight()==1080){
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            }else if(SELECTED_RESOLUTION.getHeight()==720){
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            }else{
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            }

            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
            mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
            mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
            mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);


            try {
                mMediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }



    private void closeCamera(){
        if(mCameraCaptureSession!=null){

            mCameraCaptureSession.close();

        }
        if(mCameraDevice!=null)
            mCameraDevice.close();
    }
    private void closeCameraOnlySession(){
        if(mCameraCaptureSession!=null){
            try {
                mCameraCaptureSession.abortCaptures();
                mCameraCaptureSession.stopRepeating();

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraCaptureSession.close();

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

    private void startCameraPreview() {

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
                    CAMERA_CONFIGURED =true;
                    setOptimalResolutionForSurfaceView();
                }
                break;
            case MSG_SURFACE_SIZE_FOUND:
                startCameraPreview();
                break;
        }
        return true;

    }

    private void setOptimalResolutionForSurfaceView(){
        boolean sizeFound =false;
        if(SELECTED_RESOLUTION.getWidth()<=1500){
            mSurfaceView.setAspectRatio(SELECTED_RESOLUTION.getHeight(),SELECTED_RESOLUTION.getWidth());
            mSurfaceHolder.setFixedSize(SELECTED_RESOLUTION.getWidth(),SELECTED_RESOLUTION.getHeight());
            sizeFound =true;

        }else{
            for(Size s: mCameraSizeListKnownAsRatio){
                float diff =  Math.abs(((float)s.getWidth()/(float) s.getHeight()-(float)SELECTED_RESOLUTION.getWidth()/(float) SELECTED_RESOLUTION.getHeight()));
                if(diff<0.000001 && s.getHeight()<=1500){
                    Logger.d("h "+s.getHeight()+" w "+s.getWidth()+" "+(float)s.getWidth()/ (float)s.getHeight());
                    Logger.d("h "+SELECTED_RESOLUTION.getHeight()+" w "+SELECTED_RESOLUTION.getWidth()+" "+(float)SELECTED_RESOLUTION.getWidth()/(float) SELECTED_RESOLUTION.getHeight());

                    mSurfaceHolder.setFixedSize(s.getWidth(),s.getHeight());
                    mSurfaceView.setAspectRatio(s.getHeight(),s.getWidth());


                    sizeFound =true;
                    break;
                }
            }
        }

        if(sizeFound){
            mHandler.sendEmptyMessage(MSG_SURFACE_SIZE_FOUND);
        }else {
            Toast.makeText(getContext(),"Please Change Resolution.\nUnable to find resolution match",Toast.LENGTH_SHORT).show();
        }
    }


}
