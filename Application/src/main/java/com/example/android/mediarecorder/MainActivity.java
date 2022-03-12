/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediarecorder;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.android.common.media.CameraHelper;
import com.example.android.common.media.ContantsDefine;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This activity uses the camera/camcorder as the A/V source for the
 * {@link android.media.MediaRecorder} API. A {@link android.view.TextureView} is used as the
 * camera preview which limits the code to API 14+. This can be easily replaced with a
 * {@link android.view.SurfaceView} to run on older devices.
 */
public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;
    private File mOutputFile;
    FrameLayout frameln_surface_view;
    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private Button captureButton;
    int cameraViewHeight =0, cameraViewWidth =0, cameraViewHeight_old = 0, cameraViewWidth_old = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mPreview = findViewById(R.id.surface_view);
        captureButton = findViewById(R.id.button_capture);
        frameln_surface_view = findViewById(R.id.frameln_surface_view);
        //updateTextureViewSize(width/2, height);
    }
    private void updateTextureViewSize(final int viewWidth, final int viewHeight, final int viewWidth_old, final int viewHeight_old) {

        final int viewWidthDuration = viewWidth - viewWidth_old;
        final int viewHeightDuration = viewHeight - viewHeight_old;
        final float durationRatio = (float)viewWidthDuration/(float)viewHeightDuration;

        new CountDownTimer(Math.abs(viewHeightDuration)*3, 60) {
            int durationCount = 0;
            public void onTick(long millisUntilFinished) {
                if(Math.abs(viewHeightDuration)>durationCount)
                {
                    int viewWidth_current = 0;
                    int viewHeight_current = 0;
                    if(viewHeightDuration>0)
                        viewHeight_current = viewHeight_old + durationCount;
                    else
                        viewHeight_current = viewHeight_old - durationCount;

                    if(viewWidthDuration>0)
                        viewWidth_current = (int) (viewWidth_old + durationCount*durationRatio);
                    else
                        viewWidth_current = (int) (viewWidth_old - durationCount*durationRatio);
                    frameln_surface_view.setLayoutParams(new FrameLayout.LayoutParams(viewWidth_current, viewHeight_current));
                    durationCount += 20;
                }
            }

            public void onFinish() {
                frameln_surface_view.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewHeight));
            }

        }.start();
    }
    /**
     * The capture button controls all user interaction. When recording, the button click
     * stops recording, releases {@link android.media.MediaRecorder} and
     * {@link android.hardware.Camera}. When not recording, it prepares the
     * {@link android.media.MediaRecorder} and starts recording.
     *
     * @param view the view generating the event.
     */
    public void onCaptureClick(View view) {
        if(cameraViewHeight==0 )
            cameraViewHeight = mPreview.getMeasuredHeight();
         if (cameraViewWidth==0)
             cameraViewWidth= mPreview.getMeasuredWidth();;
        updateTextureViewSize(cameraViewWidth/2, cameraViewHeight/2, cameraViewWidth , cameraViewHeight);
        boolean permissionResult = CameraHelper.checkPermissionAndOpenCamera(this);
        if(!permissionResult)
            return;
        if (isRecording) {
            // BEGIN_INCLUDE(stop_release_media_recorder)

            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
            } catch (RuntimeException e) {
                // RuntimeException is thrown when stop() is called immediately after start().
                // In this case the output file is not properly constructed ans should be deleted.
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                //noinspection ResultOfMethodCallIgnored
                if(mOutputFile!=null)
                    mOutputFile.delete();
            }
            releaseMediaRecorder(); // release the MediaRecorder object
            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Capture");
            isRecording = false;
            releaseCamera();
            // END_INCLUDE(stop_release_media_recorder)

        } else {

            // BEGIN_INCLUDE(prepare_start_media_recorder)

            new MediaPrepareTask().execute(null, null, null);

            // END_INCLUDE(prepare_start_media_recorder)

        }
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean prepareVideoRecorder() {

        // BEGIN_INCLUDE (configure_preview)
        mCamera = CameraHelper.getDefaultCameraInstance(this);
        if (mCamera==null)
            return false;
        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        // END_INCLUDE (configure_preview)


        // BEGIN_INCLUDE (configure_media_recorder)
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(profile);

        // Step 4: Set output file
        mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
        if (mOutputFile == null) {
            return false;
        }
        mMediaRecorder.setOutputFile(mOutputFile.getPath());
        // END_INCLUDE (configure_media_recorder)

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Fix no activity available
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        switch (requestCode) {
            case ContantsDefine.REQUEST_ACCESS_CAMERA_AND_WRITE_EXTENAL_STORAGE:
                if (resultCode == RESULT_OK) {
                    //open again camera when request permission successful
                    //prepareVideoRecorder();
                }
        }
    }
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }


    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                try {
                    mMediaRecorder.start();
                    isRecording = true;
                } catch (RuntimeException e) {
                    // RuntimeException is thrown when stop() is called immediately after start().
                    // In this case the output file is not properly constructed ans should be deleted.
                    Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                    //noinspection ResultOfMethodCallIgnored
                    //mOutputFile.delete();
                }


            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            setCaptureButtonText("Stop");
        }
    }
}
