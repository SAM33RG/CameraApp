package com.example.cameraapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
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
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.DownloadListener;
import com.androidnetworking.interfaces.DownloadProgressListener;
import com.androidnetworking.interfaces.JSONObjectRequestListener;
import com.androidnetworking.interfaces.UploadProgressListener;
import com.orhanobut.logger.Logger;

import org.json.JSONObject;

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
import java.util.Set;



public class CaptureStillImage extends Fragment implements Handler.Callback,FragmentLifecycle{
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

    private ImageReader mCaptureImageReader = null;
    private static final String mDirectory = "CustomCamera";
    private String mRecentImageName = null;
    private String mRecentImageWithoutExtension = null;
    private File imageFile;

    private Button mCaptureButton;
    private Button mOpenGalleryButton;
    private Button mLowLightButton;

    private View mCameraSettingLayout;


    public CaptureStillImage() {
        // Required empty public constructor
    }



    private  ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener (){
        @Override
        public void onImageAvailable(ImageReader reader) {
            saveRawImage(reader);
        }
    };

    private void saveRawImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        byte[] bytes = getJpegData(image);
        rotateAndSaveImage(bytes);
    }


    private void rotateAndSaveImage(byte[] input) {
        Bitmap sourceBitmap = BitmapFactory.decodeByteArray(input, 0, input.length);
        Matrix m = new Matrix();
        float orientation = 90;
        try {
            orientation = mCameraManager.getCameraCharacteristics(SELECTED_CAMERA_ID).get(CameraCharacteristics.SENSOR_ORIENTATION);
            Logger.d("camera orientaion :"+ orientation);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        m.setRotate(orientation, sourceBitmap.getWidth(), sourceBitmap.getHeight());
        Bitmap rotatedBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), m, true);

        File f = getOutputFile();

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(f);
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        openGallery();
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOpenGalleryButton.setVisibility(View.VISIBLE);
                mCameraSettingLayout.setVisibility(View.VISIBLE);
                mLowLightButton.setVisibility(View.VISIBLE);

            }
        });
    }

    private byte[] getJpegData(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return byteArray;
    }

    private File getOutputFile() {
        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(sdDir, mDirectory);
        if (!dir.exists()) {
            dir.mkdir();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        imageFile = new File (dir.getPath() + File.separator + "PIC_"+timeStamp+".jpg");
        mRecentImageName = imageFile.getName();
        mRecentImageWithoutExtension = "PIC_"+timeStamp;

        Logger.d("name "+mRecentImageName);
        Logger.d("dir "+dir.getAbsolutePath());


        Logger.d("file saved : "+imageFile.getAbsolutePath());
        Toast.makeText(getContext(),"Picture saved in "+mDirectory+".", Toast.LENGTH_SHORT).show();

        return imageFile;
    }

    private void setupImageReader(int height, int width){
        mCaptureImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 3);
        mCaptureImageReader.setOnImageAvailableListener(onImageAvailableListener, mHandler);
    }

    private void openGallery(){

        if(mRecentImageName==null){
            Toast.makeText(getContext(),"NO Image captured",Toast.LENGTH_SHORT).show();
            return;
        }
        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(sdDir, mDirectory);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.withAppendedPath(Uri.fromFile(file),mRecentImageName), "image/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
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
        mOpenGalleryButton = mRootView.findViewById(R.id.open_gallery_bt);
        mCameraSettingLayout = mRootView.findViewById(R.id.camera_settings_layout);
        mLowLightButton = mRootView.findViewById(R.id.low_light_button);

        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleCaptureImage();

            }
        });

        mOpenGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(mRecentImageName==null)
                    return;
                openGallery();
            }
        });

        mLowLightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doUploadPhoto(imageFile);
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
        return mRootView;

    }

    @Override
    public void onPauseFragment() {
        closeCamera();
    }

    @Override
    public void onResumeFragment() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraIdList =  new ArrayList<String>(Arrays.asList(mCameraManager.getCameraIdList()));
            populateCameraIdSpinner();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        onResumeFragment();
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
                if(matchAspectRatio(s,new Size(4,3))){
                    mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth()+" 3:4");
                    mCameraSizeListKnownAsRatio.add(s);

                }else if(matchAspectRatio(s,new Size(1,1))){
                    mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth()+" 1:1");
                    mCameraSizeListKnownAsRatio.add(s);

                }else if(matchAspectRatio(s,new Size(16,9))){
                    mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth()+" 9:16");
                    mCameraSizeListKnownAsRatio.add(s);

                }

//                Logger.d(""+s.getHeight()+"X"+s.getWidth()+" "+(float)s.getWidth()/ (float)s.getHeight());
//                mCameraSizeStringList.add(""+s.getHeight()+"X"+s.getWidth());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),R.layout.support_simple_spinner_dropdown_item, mCameraSizeStringList);
            mCameraResSpinner.setAdapter(adapter);
            mCameraResSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Logger.d("Camera res spinner current resolution : "+mCameraSizeStringList.get(position)+" of camera id: "+ cameraId);
                    setupImageReader(mCameraSizeListKnownAsRatio.get(position).getHeight(), mCameraSizeListKnownAsRatio.get(position).getWidth());
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
    private CameraCaptureSession.CaptureCallback mCaptureCallback =  new CameraCaptureSession.CaptureCallback() {
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
    };

    private void handleCaptureImage() {
        if (mCameraDevice != null) {
            if (mCameraCaptureSession != null) {
                try {
                    mCameraSettingLayout.setVisibility(View.GONE);
                    CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                    captureRequestBuilder.addTarget(mCaptureImageReader.getSurface());
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    Logger.d(       "flash info"+         mCameraManager.getCameraCharacteristics(SELECTED_CAMERA_ID).get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
//                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);


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

                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
//                    previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);


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


                    /*mSurfaceHolder.setFixedSize(SELECTED_RESOLUTION.getWidth(),SELECTED_RESOLUTION.getHeight());
                    mSurfaceView.setAspectRatio(SELECTED_RESOLUTION.getHeight(),SELECTED_RESOLUTION.getWidth());
                    */


                    setOptimalResolutionForSurfaceView();
//                    mHandler.sendEmptyMessage(MSG_SURFACE_SIZE_FOUND);

                }
                break;
            case MSG_SURFACE_SIZE_FOUND:
                startCamera();
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

    private void doUploadPhoto(File photoUpload) {
        final ProgressDialog pd  = new ProgressDialog(getActivity());
        pd.setMessage("uploading");
        pd.show();
        AndroidNetworking.upload("http://192.168.0.152:3000/api/v1/uploadll")
                .addMultipartFile("image",photoUpload)
                .addMultipartParameter("email","sameer@gmail.com")
                .setTag("uploadTest")
                .setPriority(Priority.HIGH)
                .build()
                .setUploadProgressListener(new UploadProgressListener() {
                    @Override
                    public void onProgress(long bytesUploaded, long totalBytes) {
                        // do anything with progress
                      //  progress.setText(""+(bytesUploaded*100)/totalBytes);
                        Logger.d(""+(bytesUploaded*100)/totalBytes);
                    }
                })
                .getAsJSONObject(new JSONObjectRequestListener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // do anything with response
                        Logger.d(response);
                        pd.dismiss();
                        doDownloadPhoto();
                      //  isVisible =true;
                        //printBtn.setVisibility(View.VISIBLE);
                    }
                    @Override
                    public void onError(ANError error) {
                        // handle error
                        Logger.e(error.toString());
                    }
                });
    }

    private void doDownloadPhoto(){

        final ProgressDialog pd  = new ProgressDialog(getActivity());
        pd.setMessage("working");
        pd.show();

        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(sdDir, mDirectory);

        AndroidNetworking.download("http://192.168.0.152:3000/api/v1/downloadll",dir.getAbsolutePath(),mRecentImageWithoutExtension+"_low_light_enhanced.jpg")
                .addHeaders("email", "sameer@gmail.com")
                .setTag("downloadTest")
                .setPriority(Priority.MEDIUM)
                .build()
                .setDownloadProgressListener(new DownloadProgressListener() {
                    @Override
                    public void onProgress(long bytesDownloaded, long totalBytes) {
                        // do anything with progress
                        Logger.d(""+(bytesDownloaded*100)/totalBytes);

                    }
                })
                .startDownload(new DownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        // do anything after completion
                        pd.dismiss();
                        Toast.makeText(getContext(),"low_light enhanced version downloaded",Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(ANError error) {
                        // handle error
                    }
                });
    }


}
