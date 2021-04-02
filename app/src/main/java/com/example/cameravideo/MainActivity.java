package com.example.cameravideo;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    private int REQUEST_VIDEO = 99;
    private Button btn_main;


    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_main = findViewById(R.id.btn_main);


        btn_main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
                if (PermissionsUtils.getInstance().chekPermissions(MainActivity.this, permissions, permissionsResult)) {
                    IntentUtils.startActivityForResult(MainActivity.this, VideoRecordActivity.class, null, REQUEST_VIDEO);
                }
            }
        });
    }


    //创建监听权限的接口对象
    PermissionsUtils.IPermissionsResult permissionsResult = new PermissionsUtils.IPermissionsResult() {
        @Override
        public void passPermissons() {
            Log.e("TAG", "passPermissons");

        }

        @Override
        public void forbitPermissons() {
            Log.e("TAG", "forbitPermissons");
        }

    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionsUtils.getInstance().onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        Log.e("TAG", "onRequestPermissionsResult" + Arrays.toString(grantResults));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            if (requestCode == REQUEST_VIDEO) {
                String path = data.getStringExtra("path");
                String imagePath = data.getStringExtra("imagePath");
                int type = data.getIntExtra("type", 0);
                Log.e("TAG", "MainActivity_onActivityResult: " + path + "==" + imagePath + "==" + type);
                //type值：0表示摄像  1表示拍照
            }
        }
    }
}
