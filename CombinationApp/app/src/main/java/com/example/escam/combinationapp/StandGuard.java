package com.example.escam.combinationapp;

import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.escam.combinationapp.SimpleLocation;

public class StandGuard extends AppCompatActivity implements com.example.escam.combinationapp.AccelerometerListener{
    private static final String TAG = "CamTestActivity";
    Preview preview;
    Button buttonClick;
    Camera camera;
    Activity act;
    Context ctx;
    boolean movedRecently;	//true if accelerometerManager is still reporting a move event
    double latitude;
    double longitude;
    int threshold = 17; //threshold for acceleration (higher means more movement required)
    private SimpleLocation mLocation;
    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stand_guard);
        configureEndButton();
        ctx = this;
        act = this;
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
        preview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.layout)).addView(preview);
        preview.setKeepScreenOn(true);
        mLocation = new SimpleLocation(this);

        // reduce the precision to 5,000m for privacy reasons
        mLocation.setBlurRadius(5000);

        // if we can't access the location yet
        if (!mLocation.hasLocationEnabled()) {
            // ask the user to enable location access
            SimpleLocation.openSettings(this);
        }

        movedRecently = false;

        //Set acceleration sensitivity
        threshold = 12;
    }


    @Override
    protected void onResume() {
        super.onResume();
        mLocation.beginUpdates();

        int numCams = Camera.getNumberOfCameras();
        if(numCams > 0){
            try{
                camera = Camera.open(1);
                camera.startPreview();
                preview.setCamera(camera);
            } catch (RuntimeException ex){
                Toast.makeText(ctx, "Camera Not Found", Toast.LENGTH_LONG).show();
            }
        }
        if(AccelerometerManager.isSupported(this)) {
            AccelerometerManager.startListening(this);
        }
    }

    @Override
    protected void onPause() {
        if(camera != null) {
            camera.stopPreview();
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        AccelerometerManager.stopListening();
        mLocation.endUpdates();
        super.onPause();
    }

    private void resetCam() {
        camera.startPreview();
        preview.setCamera(camera);
    }

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            //			 Log.d(TAG, "onShutter'd");
        }
    };

    PictureCallback rawCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            //			 Log.d(TAG, "onPictureTaken - raw");
        }
    };

    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);
            resetCam();
            Log.d(TAG, "onPictureTaken - jpeg");
            movedRecently = false;
        }
    };

    @Override
    public void onAccelerationChanged(float x, float y, float z) {
        if(((x+y+z) > threshold) && !movedRecently)  {
            latitude = mLocation.getLatitude();
            longitude = mLocation.getLongitude();
            String message = "Lat: " + Double.toString(latitude) + "\nLng: " + Double.toString(longitude);
            //Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            movedRecently = true;
            camera.takePicture(shutterCallback, rawCallback, jpegCallback);
            Toast.makeText(getApplicationContext(), "Picture Taken", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onShake(float force) {
        //Toast.makeText(getApplicationContext(), "IT SHOOK", Toast.LENGTH_LONG).show();
    }

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outStream = null;
            // Write to SD Card
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File (sdCard.getAbsolutePath() + "/comboApp");
                dir.mkdirs();
                //Toast.makeText(getApplicationContext(), Double.toString(latitude), Toast.LENGTH_SHORT).show();
                //String fileName = String.format("%d.jpg", System.currentTimeMillis());
                String fileName = String.format(System.currentTimeMillis()+"lat"+Double.toString(latitude)+"long"+Double.toString(longitude)+".jpg");
                //fileName += "Lat"+Double.toString(latitude)+"Long"+Double.toString(longitude);
                //Toast.makeText(getApplicationContext(), fileName, Toast.LENGTH_SHORT).show();
                File outFile = new File(dir, fileName);
                outStream = new FileOutputStream(outFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();

                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());
                refreshGallery(outFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            return null;
        }

    }
    public void configureEndButton() {
        Button endButton = findViewById(R.id.endButton);
        endButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view){
                finish();
            }
        });

    }

}
