package com.example.cameraapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String[] PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    private void getPermissionFromUser(){
        List<String>permissionsRequestList = new ArrayList<>();

        for(int i=0 ; i<PERMISSIONS.length;i++){
            if (checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED) {
                permissionsRequestList.add(PERMISSIONS[i]);
            }
        }

        if (permissionsRequestList!=null) {
            requestPermissions(permissionsRequestList.toArray(new String[permissionsRequestList.size()]), PERMISSION_REQUEST_CODE);
        }else {
            Toast.makeText(getApplicationContext(),"All permission granted",Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        getPermissionFromUser();

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
            if(!allPermissionsGrated){
                getPermissionFromUser();
                return;
            }

        }
    }
}
