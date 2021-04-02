package com.example.cameravideo;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;


public class VideoRecordActivity extends AppCompatActivity {
    SurfaceView mSurfaceview;
    Button mBtnRecord;
    Button mBtnPlay;
    Button mBtnCancle;
    Button mBtnSubmit;
    LinearLayout mLlRecordOp;
    LinearLayout mLlRecordBtn;
    MaterialProgressBar mProgress;


    public int TYPE_VIDEO = 0;  //视频模式
    public int TYPE_IMAGE = 1;  //拍照模式
    private boolean mStartedFlag = false;//录像中标志
    private boolean mPlayFlag = false;
    private MediaRecorder mRecorder;
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private MediaPlayer mMediaPlayer;
    private String dirPath;  //目标文件夹地址
    private String path;  //最终视频路径
    private String imgPath;  //缩略图 或 拍照模式图片位置
    private int timer = 0; //计时器
    private int maxSec = 10; //视频总时长
    private long startTime = 0L; //起始时间毫秒
    private long stopTime = 0L;  //结束时间毫秒
    private boolean cameraReleaseEnable = true;  //回收摄像头
    private boolean recorderReleaseEnable = false;  //回收recorder
    private boolean playerReleaseEnable = false; //回收palyer

    private int mType = TYPE_VIDEO; //默认为视频模式
    //用于记录视频录制时长
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mBtnPlay.setVisibility(View.INVISIBLE);
            mLlRecordOp.setVisibility(View.VISIBLE);
        }
    };
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            timer++;
//            Log.d("计数器","$timer")
            if (timer < 100) {
                // 之所以这里是100 是为了方便使用进度条
                mProgress.setProgress(timer);
                //之所以每一百毫秒增加一次计时器是因为：总时长的毫秒数 / 100 即每次间隔延时的毫秒数 为 100
                handler.postDelayed(this, maxSec * 10L);
            } else {
                //停止录制 保存录制的流、显示供操作的ui
                Log.d("到最大拍摄时间", "");
                stopRecord();
                System.currentTimeMillis();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);


        mSurfaceview = findViewById(R.id.mSurfaceview);
        mBtnRecord = findViewById(R.id.mBtnRecord);
        mBtnPlay = findViewById(R.id.mBtnPlay);
        mBtnCancle = findViewById(R.id.mBtnCancle);
        mBtnSubmit = findViewById(R.id.mBtnSubmit);
        mLlRecordOp = findViewById(R.id.mLlRecordOp);
        mLlRecordBtn = findViewById(R.id.mLlRecordBtn);
        mProgress = findViewById(R.id.mProgress);


        initView();
    }


    private void initView() {
        mMediaPlayer = new MediaPlayer();
        SurfaceHolder hold = mSurfaceview.getHolder();
        mBtnRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRecord();
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    stopRecord();
                }
                return true;
            }
        });
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playRecord();
            }
        });
        mBtnCancle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mType == TYPE_VIDEO) {
                    stopPlay();
                    File videoFile = new File(path);
                    if (videoFile.exists() && videoFile.isFile()) {
                        videoFile.delete();
                    }
                } else {
                    //拍照模式
                    File imgFile = new File(imgPath);
                    if (imgFile.exists() && imgFile.isFile()) {
                        imgFile.delete();
                    }
                }
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        mBtnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlay();
                Intent intent = new Intent();
                intent.putExtra("path", path);
                intent.putExtra("imagePath", imgPath);
                intent.putExtra("type", mType);
                if (mType == TYPE_IMAGE) {
                    //删除一开始创建的视频文件
                    File videoFile = new File(path);
                    if (videoFile.exists() && videoFile.isFile()) {
                        videoFile.delete();
                    }
                }
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        hold.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    mSurfaceHolder = holder;
                    //使用后置摄像头
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                    mCamera.setDisplayOrientation(90);//旋转90度
                    mCamera.setPreviewDisplay(holder);
                    Camera.Parameters params = mCamera.getParameters();
                    Pair<Integer, Integer> size = getPreviewSize();
                    params.setPictureSize(size.first, size.second);
                    params.setJpegQuality(100);
                    params.setPictureFormat(PixelFormat.JPEG);
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//1连续对焦
                    mCamera.setParameters(params);

                } catch (Exception e) {
                    //Camera.open() 在摄像头服务无法连接时可能会抛出 RuntimeException
                    finish();
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (holder != null)
                    mSurfaceHolder = holder;
                mCamera.startPreview();
                mCamera.cancelAutoFocus();
                // 关键代码 该操作必须在开启预览之后进行（最后调用），
                // 否则会黑屏，并提示该操作的下一步出错
                // 只有执行该步骤后才可以使用MediaRecorder进行录制
                // 否则会报 MediaRecorder(13280): start failed: -19
                mCamera.unlock();
                cameraReleaseEnable = true;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                handler.removeCallbacks(runnable);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPlayFlag) {
            stopPlay();
        }
        if (mStartedFlag) {
            Log.d("页面stop", "");
            stopRecord();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recorderReleaseEnable) mRecorder.release();
        if (cameraReleaseEnable) {
            mCamera.stopPreview();
            mCamera.release();
        }
        if (playerReleaseEnable) {
            mMediaPlayer.release();
        }
    }

    private void startRecord() {
        timer = 0;
        if (!mStartedFlag) {
            mStartedFlag = true;
            mLlRecordOp.setVisibility(View.INVISIBLE);
            mBtnPlay.setVisibility(View.INVISIBLE);
            mLlRecordBtn.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.VISIBLE); //进度条可见
            //开始计时
            handler.postDelayed(runnable, maxSec * 10L);
            recorderReleaseEnable = true;
            mRecorder = new MediaRecorder();

            mRecorder.reset();
            mRecorder.setCamera(mCamera);
            // 设置音频源与视频源 这两项需要放在setOutputFormat之前
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            //设置输出格式
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //这两项需要放在setOutputFormat之后 IOS必须使用ACC
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); //音频编码格式
            //使用MPEG_4_SP格式在华为P20 pro上停止录制时会出现
            //MediaRecorder: stop failed: -1007
            //java.lang.RuntimeException: stop failed.
            // at android.media.MediaRecorder.stop(Native Method)
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); //视频编码格式
            //设置最终出片分辨率
            mRecorder.setVideoSize(640, 480);
            mRecorder.setVideoFrameRate(30);
            mRecorder.setVideoEncodingBitRate(3 * 1024 * 1024);
            mRecorder.setOrientationHint(90);
            //设置记录会话的最大持续时间（毫秒）
            mRecorder.setMaxDuration(30 * 1000);
            path = Environment.getExternalStorageDirectory().getPath() + File.separator + "VideoRecorder";
            if (path != null) {
                File dir = new File(path);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                dirPath = dir.getAbsolutePath();
                path = dir.getAbsolutePath() + "/" + getDate() + ".mp4";
                Log.d("zjj", "文件路径： $path");
                mRecorder.setOutputFile(path);
                try {
                    mRecorder.prepare();
                    mRecorder.start();
                    startTime = System.currentTimeMillis(); //记录开始拍摄时间
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void stopRecord() {

        if (mStartedFlag) {
            mStartedFlag = false;
            mBtnRecord.setEnabled(false);
            mBtnRecord.setClickable(false);

            mLlRecordBtn.setVisibility(View.INVISIBLE);
            mProgress.setVisibility(View.INVISIBLE);

            handler.removeCallbacks(runnable);
            stopTime = System.currentTimeMillis();
//          方法1 ： 延时确保录制时间大于1s
//            if (stopTime-startTime<1100) {
//                Thread.sleep(1100+startTime-stopTime)
//            }
//            mRecorder.stop()
//            mRecorder.reset()
//            mRecorder.release()
//            recorderReleaseEnable = false
//            mCamera.lock()
//            mCamera.stopPreview()
//            mCamera.release()
//            cameraReleaseEnable = false
//            mBtnPlay.visibility = View.VISIBLE
//            MediaUtils.getImageForVideo(path) {
//                //获取到第一帧图片后再显示操作按钮
//                Log.d(TAG,"获取到了第一帧")
//                imgPath=it.absolutePath
//                mLlRecordOp.visibility = View.VISIBLE
//            }


//          方法2 ： 捕捉异常改为拍照
            try {
                mRecorder.stop();
                mRecorder.reset();
                mRecorder.release();
                recorderReleaseEnable = false;
                mCamera.lock();
                mCamera.stopPreview();
                mCamera.release();
                cameraReleaseEnable = false;
                mBtnPlay.setVisibility(View.VISIBLE);
                MediaUtils.getImageForVideo(path, new MediaUtils.OnLoadVideoImageListener() {
                    @Override
                    public void onLoadImage(File file) {
                        imgPath = file.getAbsolutePath();
                        mLlRecordOp.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                //当catch到RE时，说明是录制时间过短，此时将由录制改变为拍摄
                mType = TYPE_IMAGE;
                Log.e("拍摄时间过短", "");
                mRecorder.reset();
                mRecorder.release();
                recorderReleaseEnable = false;
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        if (data != null) {
                            saveImage(data);
                        }
                    }
                });
            }
        }
    }

    private void playRecord() {
//修复录制时home键切出再次切回时无法播放的问题
        if (cameraReleaseEnable) {
            Log.d("zjj", "回收摄像头资源");
            mCamera.lock();
            mCamera.stopPreview();
            mCamera.release();
            cameraReleaseEnable = false;
        }
        playerReleaseEnable = true;
        mPlayFlag = true;
        mBtnPlay.setVisibility(View.INVISIBLE);

        mMediaPlayer.reset();
        Uri uri = Uri.parse(path);
        mMediaPlayer = MediaPlayer.create(this, uri);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setDisplay(mSurfaceHolder);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mBtnPlay.setVisibility(View.VISIBLE);
            }
        });
        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMediaPlayer.start();
    }

    private void stopPlay() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
    }

    private void saveImage(byte[] data) {
        new Thread() {
            @Override
            public void run() {
                //方式1 ：java nio 保存图片 保存后的图片存在0度旋转角
//            val imgFileName = "IMG_" + getDate() + ".jpg"
//            val imgFile = File(dirPath + File.separator + imgFileName)
//            val outputStream = FileOutputStream(imgFile)
//            val fileChannel = outputStream.channel
//            val buffer = ByteBuffer.allocate(data.size)
//            try {
//                buffer.put(data)
//                buffer.flip()
//                fileChannel.write(buffer)
//            } catch (e: IOException) {
//                Log.e("写图片失败", e.message)
//            } finally {
//                try {
//                    outputStream.close()
//                    fileChannel.close()
//                    buffer.clear()
//                } catch (e: IOException) {
//                    Log.e("关闭图片失败", e.message)
//                }
//            }

                //方式2： bitmap保存 将拍摄结果旋转90度
                String imgFileName = "IMG_" + getDate() + ".jpg";
                File imgFile = new File(dirPath + File.separator + imgFileName);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap newBitmap = PictureUtils.rotateBitmap(bitmap, 90);
                try {
                    imgFile.createNewFile();
                    BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(imgFile));
                    newBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                    os.flush();
                    os.close();
                    int degree = PictureUtils.getBitmapDegree(imgFile.getAbsolutePath());
                    Log.d("图片角度为：", "$degree");
                    Log.d("zjj", "转为拍照，获取到图片数据 $imagepath");
                    imgPath = imgFile.getAbsolutePath();
                    mCamera.lock();
                    mCamera.stopPreview();
                    mCamera.release();
                    cameraReleaseEnable = false;
                    handler.sendEmptyMessage(1);
//                    runOnUiThread {
//                        mBtnPlay.visibility = View.INVISIBLE
//                        mLlRecordOp.visibility = View.VISIBLE
//                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private Pair<Integer, Integer> getPreviewSize() {
        int bestPreviewWidth = 1920;
        int bestPreviewHeight = 1080;
        int mCameraPreviewWidth;
        int mCameraPreviewHeight;
        int diffs = Integer.MAX_VALUE;
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);// as WindowManager
        Display display = windowManager.getDefaultDisplay();
        Point screenResolution = new Point(display.getWidth(), display.getHeight());
        List<Camera.Size> availablePreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        Log.e("zjj", "屏幕宽度 ${screenResolution.x}  屏幕高度${screenResolution.y}");
        for (int i = 0; i < availablePreviewSizes.size(); i++) {
            Camera.Size previewSize = availablePreviewSizes.get(i);
            Log.v("zjj", " PreviewSizes = $previewSize");
            mCameraPreviewWidth = previewSize.width;
            mCameraPreviewHeight = previewSize.height;
            int newDiffs = Math.abs(mCameraPreviewWidth - screenResolution.y) + Math.abs(mCameraPreviewHeight - screenResolution.x);
            Log.v("zjj", "newDiffs = $newDiffs");
            if (newDiffs == 0) {
                bestPreviewWidth = mCameraPreviewWidth;
                bestPreviewHeight = mCameraPreviewHeight;
                break;
            }
            if (diffs > newDiffs) {
                bestPreviewWidth = mCameraPreviewWidth;
                bestPreviewHeight = mCameraPreviewHeight;
                diffs = newDiffs;
            }
            Log.e("zjj", "${previewSize.width} ${previewSize.height}  宽度 $bestPreviewWidth 高度 $bestPreviewHeight");
        }
        Log.e("zjj", "最佳宽度 $bestPreviewWidth 最佳高度 $bestPreviewHeight");
        return new Pair(bestPreviewWidth, bestPreviewHeight);
    }

    private String getDate() {
        Calendar ca = Calendar.getInstance();
        int year = ca.get(Calendar.YEAR);           // 获取年份
        int month = ca.get(Calendar.MONTH);       // 获取月份
        int day = ca.get(Calendar.DATE);           // 获取日
        int minute = ca.get(Calendar.MINUTE);      // 分
        int hour = ca.get(Calendar.HOUR);          // 小时
        int second = ca.get(Calendar.SECOND);    // 秒
        return "" + year + (month + 1) + day + hour + minute + second;
    }


}

