package com.example.cameraapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.transition.Transition;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String[] PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private TabAdapter myAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUpLogger();

        mTabLayout = findViewById(R.id.tabLayout);
        mViewPager = findViewById(R.id.viewpager);

    }

    void startFragments(){
        myAdapter = new TabAdapter(getSupportFragmentManager());
        myAdapter.addFragment(new CaptureStillImage(), "Image");
        myAdapter.addFragment(new RecordVideo(), "Video");

        mViewPager.setOffscreenPageLimit(0);
        mViewPager.setAdapter(myAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            int currentPosition = 0;
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                FragmentLifecycle fragmentToHide = (FragmentLifecycle)myAdapter.getItem(currentPosition);
                fragmentToHide.onPauseFragment();

                FragmentLifecycle fragmentToShow = (FragmentLifecycle)myAdapter.getItem(position);
                fragmentToShow.onResumeFragment();


                currentPosition = position;

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        mTabLayout.setupWithViewPager(mViewPager);
        mViewPager.setCurrentItem(0);

    }

    private void getPermissionFromUser(){
        List<String>permissionsRequestList = new ArrayList<>();

        for(int i=0 ; i<PERMISSIONS.length;i++){
            if (checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED) {
                permissionsRequestList.add(PERMISSIONS[i]);
            }
        }

        if (permissionsRequestList.size()!=0) {
            requestPermissions(permissionsRequestList.toArray(new String[permissionsRequestList.size()]), PERMISSION_REQUEST_CODE);
        }else {
            Toast.makeText(getApplicationContext(),"All permission granted",Toast.LENGTH_SHORT).show();
//            getSupportFragmentManager().beginTransaction().add(R.id.container,new CaptureStillImage()).addToBackStack(null).commit();
            startFragments();
        }
    }

//@TODO implement content provider
    @Override
    protected void onStart() {
        super.onStart();
        getPermissionFromUser();
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {

            boolean allPermissionsGrated = true;

            for (int i = 0;i<permissions.length;i++){

                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {

                    allPermissionsGrated =false;
                    Toast.makeText(getApplicationContext(),"All permission necessary for app to function",Toast.LENGTH_SHORT).show();
                }
            }
            getPermissionFromUser();
            return;
            /*
            if(!allPermissionsGrated){
                getPermissionFromUser();
                return;
            }*/

        }
    }

    private void setUpLogger(){

        FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                .showThreadInfo(true)  // (Optional) Whether to show thread info or not. Default true
                .methodCount(3)         // (Optional) How many method line to show. Default 2
                .methodOffset(7)        // (Optional) Hides internal method calls up to offset. Default 5
                .tag("*************** ")   // (Optional) Global tag for every log. Default PRETTY_LOGGER
                .build();

        Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy));
    }
}
