package com.example.shilde.Activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;

import com.example.shilde.R;
import com.example.shilde.facerecognitionlibrary.Helpers.CustomCameraView;
import com.example.shilde.facerecognitionlibrary.Helpers.FileHelper;
import com.example.shilde.facerecognitionlibrary.Helpers.MatName;
import com.example.shilde.facerecognitionlibrary.Helpers.MatOperation;
import com.example.shilde.facerecognitionlibrary.PreProcessor.PreProcessorFactory;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.File;
import java.util.Date;
import java.util.List;

public class AddPersonPreviewActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    public static final int TIME = 0;
    public static final int MANUALLY = 1;
    private CustomCameraView mAddPersonView;
    // The timerDiff defines after how many milliseconds a picture is taken
    private long timerDiff;
    private long lastTime;
    private PreProcessorFactory ppF;
    private FileHelper fh;
    private String folder;
    private String name;
    private int total;
    private int numberOfPictures;
    private int method;
    private boolean capturePressed;
    private boolean front_camera;
    private boolean night_portrait;
    private int exposure_compensation;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_person_preview);

        Intent intent = getIntent();
        folder = "Training";
        name = intent.getStringExtra("Name");
        method = intent.getIntExtra("Method", 0);
        capturePressed = false;

        fh = new FileHelper();
        total = 0;
        lastTime = new Date().getTime();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        timerDiff = Integer.valueOf(sharedPrefs.getString("key_timerDiff", "500"));

        mAddPersonView = (CustomCameraView) findViewById(R.id.AddPersonPreview);
        // Use camera which is selected in settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        front_camera = sharedPref.getBoolean("key_front_camera", true);

        numberOfPictures = Integer.valueOf(sharedPref.getString("key_numberOfPictures", "100"));
        //numberOfPictures = 20;
        night_portrait = sharedPref.getBoolean("key_night_portrait", false);
        exposure_compensation = Integer.valueOf(sharedPref.getString("key_exposure_compensation", "50"));

        if (front_camera){
            mAddPersonView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        } else {
            mAddPersonView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        }
        mAddPersonView.setVisibility(SurfaceView.VISIBLE);
        mAddPersonView.setCvCameraViewListener(this);

        int maxCameraViewWidth = Integer.parseInt(sharedPref.getString("key_maximum_camera_view_width", "640"));
        int maxCameraViewHeight = Integer.parseInt(sharedPref.getString("key_maximum_camera_view_height", "480"));
        mAddPersonView.setMaxFrameSize(maxCameraViewWidth, maxCameraViewHeight);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        if (night_portrait) {
            mAddPersonView.setNightPortrait();
        }

        if (exposure_compensation != 50 && 0 <= exposure_compensation && exposure_compensation <= 100)
            mAddPersonView.setExposure(exposure_compensation);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat imgRgba = inputFrame.rgba();
        Mat imgCopy = new Mat();
        imgRgba.copyTo(imgCopy);
        // Selfie / Mirror mode
        if(front_camera){
            Core.flip(imgRgba,imgRgba,1);
        }
        long time = new Date().getTime();
        if((method == MANUALLY) || (method == TIME) && (lastTime + timerDiff < time)){
            lastTime = time;

            // Check that only 1 face is found. Skip if any or more than 1 are found.
            List<Mat> images = ppF.getCroppedImage(imgCopy);
            if (images != null && images.size() == 1){
                Mat img = images.get(0);
                if(img != null){
                    Rect[] faces = ppF.getFacesForRecognition();
                    //Only proceed if 1 face has been detected, ignore if 0 or more than 1 face have been detected
                    if((faces != null) && (faces.length == 1)){
                        faces = MatOperation.rotateFaces(imgRgba, faces, ppF.getAngleForRecognition());
                        if(((method == MANUALLY) && capturePressed) || (method == TIME)){
                            MatName m = new MatName(name + "_" + total, img);
                            String wholeFolderPath = fh.TRAINING_PATH + name;
                            new File(wholeFolderPath).mkdirs();
                            fh.saveMatToImage(m, wholeFolderPath + "/");

                            for(int i = 0; i<faces.length; i++){
                                MatOperation.drawRectangleAndLabelOnPreview(imgRgba, faces[i], String.valueOf(total), front_camera);
                            }

                            total++;

                            // Stop after numberOfPictures (settings option)
                            if(total >= numberOfPictures){
                                // 기존 유저 생체 이미지 초기화
                                setDirEmpty(fh.TRAINING_PATH);

                                Intent intent = new Intent(getApplicationContext(), TrainingActivity.class);
                                intent.putExtra("ID",name);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                            }
                            capturePressed = false;
                        } else {
                            for(int i = 0; i<faces.length; i++){
                                MatOperation.drawRectangleOnPreview(imgRgba, faces[i], front_camera);
                            }
                        }
                    }
                }
            }
        }

        return imgRgba;
    }
    public void setDirEmpty(String dirName){
        String path = dirName;
        File dir = new File(path);
        File[] childFileList = dir.listFiles();
        if (dir.exists()) {
            for (File childFile : childFileList) {
                if(!childFile.getName().contains("person") && !childFile.getName().contains(name)) {
                    if (childFile.isDirectory()) {
                        setDirEmpty(childFile.getAbsolutePath()); //하위 디렉토리
                    } else {
                        childFile.delete(); //하위 파일
                    }
                }
            }
            dir.delete();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        ppF = new PreProcessorFactory(this);
        mAddPersonView.enableView();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mAddPersonView != null)
            mAddPersonView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mAddPersonView != null)
            mAddPersonView.disableView();
    }
}
