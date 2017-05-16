package com.segway.robot.host.coreservice.vision.sample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.vision.frame.Frame;
import com.segway.robot.sdk.vision.stream.StreamInfo;
import com.segway.robot.sdk.vision.stream.StreamType;

/**
 * The Sample Activity demonstrate the main function of Segway Robot VisionService.
 */
public class VisionSampleActivity extends Activity implements CompoundButton.OnCheckedChangeListener {
    private boolean mBind;
    private Vision mVision;

    private Switch mBindSwitch;
    private Switch mPreviewSwitch;
    private Switch mTransferSwitch;

    private SurfaceView mColorSurfaceView;
    private SurfaceView mDepthSurfaceView;

    private ImageView mColorImageView;
    private ImageView mDepthImageView;

    private Bitmap mColorBitmap;
    private Bitmap mDepthBitmap;

    ServiceBinder.BindStateListener mBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            mBind = true;
        }

        @Override
        public void onUnbind(String reason) {
            mBind = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vision_sample);

        // init content view
        mBindSwitch = (Switch) findViewById(R.id.bind);
        mPreviewSwitch = (Switch) findViewById(R.id.preview);
        mTransferSwitch = (Switch) findViewById(R.id.transfer);

        mBindSwitch.setOnCheckedChangeListener(this);
        mPreviewSwitch.setOnCheckedChangeListener(this);
        mTransferSwitch.setOnCheckedChangeListener(this);

        mColorSurfaceView = (SurfaceView) findViewById(R.id.colorSurface);
        mDepthSurfaceView = (SurfaceView) findViewById(R.id.depthSurface);

        mColorImageView = (ImageView) findViewById(R.id.colorImage);
        mDepthImageView = (ImageView) findViewById(R.id.depthImage);

        // get Vision SDK instance
        mVision = Vision.getInstance();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mBindSwitch.setChecked(false);
        mPreviewSwitch.setChecked(false);
        mTransferSwitch.setChecked(false);
    }

    /**
     * Start preview color and depth image
     */
    private synchronized void startPreview() {
        // 1. Get activated stream info from Vision Service.
        //    Streams are pre-config.
        StreamInfo[] infos = mVision.getActivatedStreamInfo();
        for(StreamInfo info : infos) {
            // Adjust image ratio for display
            float ratio = (float)info.getWidth()/info.getHeight();
            ViewGroup.LayoutParams layout;
            switch (info.getStreamType()) {
                case StreamType.COLOR:
                    // Adjust color surface view
                    mColorSurfaceView.getHolder().setFixedSize(info.getWidth(), info.getHeight());
                    layout = mColorSurfaceView.getLayoutParams();
                    layout.width = (int) (mColorSurfaceView.getHeight() * ratio);
                    mColorSurfaceView.setLayoutParams(layout);

                    // preview color stream
                    mVision.startPreview(StreamType.COLOR, mColorSurfaceView.getHolder().getSurface());
                    break;
                case StreamType.DEPTH:
                    // Adjust depth surface view
                    mDepthSurfaceView.getHolder().setFixedSize(info.getWidth(), info.getHeight());
                    layout = mDepthSurfaceView.getLayoutParams();
                    layout.width = (int) (mDepthSurfaceView.getHeight() * ratio);
                    mDepthSurfaceView.setLayoutParams(layout);

                    // preview depth stream
                    mVision.startPreview(StreamType.DEPTH, mDepthSurfaceView.getHolder().getSurface());
                    break;
            }
        }
    }

    /**
     * Stop preview
     */
    private synchronized void stopPreview() {
        StreamInfo[] infos = mVision.getActivatedStreamInfo();
        for(StreamInfo info : infos) {
            switch (info.getStreamType()) {
                case StreamType.COLOR:
                    // Stop color preview
                    mVision.stopPreview(StreamType.COLOR);
                    break;
                case StreamType.DEPTH:
                    // Stop depth preview
                    mVision.stopPreview(StreamType.DEPTH);
                    break;
            }
        }
    }

    /**
     * FrameListener instance for get raw image data form vision service
     */
    Vision.FrameListener mFrameListener = new Vision.FrameListener() {
        @Override
        public void onNewFrame(int streamType, Frame frame) {
            Runnable runnable = null;
            switch (streamType) {
                case StreamType.COLOR:
                    // draw color image to bitmap and display
                    mColorBitmap.copyPixelsFromBuffer(frame.getByteBuffer());
                    runnable = new Runnable() {
                        @Override
                        public void run() {
                            mColorImageView.setImageBitmap(mColorBitmap);
                        }
                    };
                    break;
                case StreamType.DEPTH:
                    // draw depth image to bitmap and display
                    mDepthBitmap.copyPixelsFromBuffer(frame.getByteBuffer());
                    runnable = new Runnable() {
                        @Override
                        public void run() {
                            mDepthImageView.setImageBitmap(mDepthBitmap);
                        }
                    };
                    break;
            }

            if (runnable != null) {
                runOnUiThread(runnable);
            }
        }
    };

    /**
     * Start transfer raw image data form VisionService to giving FrameListener
     */
    private synchronized void startImageTransfer() {
        StreamInfo[] infos = mVision.getActivatedStreamInfo();
        for(StreamInfo info : infos) {
            switch (info.getStreamType()) {
                case StreamType.COLOR:
                    mColorBitmap = Bitmap.createBitmap(info.getWidth(), info.getHeight(), Bitmap.Config.ARGB_8888);
                    mVision.startListenFrame(StreamType.COLOR, mFrameListener);
                    break;
                case StreamType.DEPTH:
                    mDepthBitmap = Bitmap.createBitmap(info.getWidth(), info.getHeight(), Bitmap.Config.RGB_565);
                    mVision.startListenFrame(StreamType.DEPTH, mFrameListener);
                    break;
            }
        }

    }

    /**
     * Stop transfer raw image data
     */
    private synchronized void stopImageTransfer() {
        mVision.stopListenFrame(StreamType.COLOR);
        mVision.stopListenFrame(StreamType.DEPTH);
    }

    /**
     * Buttons
     * @param buttonView
     * @param isChecked
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.bind:
                if (isChecked) {
                    if(!mVision.bindService(this, mBindStateListener)) {
                        mBindSwitch.setChecked(false);
                        Toast.makeText(this, "Bind service failed", Toast.LENGTH_SHORT).show();
                    }
                    Toast.makeText(this, "Bind service success", Toast.LENGTH_SHORT).show();
                } else {
                    mPreviewSwitch.setChecked(false);
                    mTransferSwitch.setChecked(false);
                    mVision.unbindService();
                    mBind = false;
                    Toast.makeText(this, "Unbind service", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.preview:
                if (isChecked) {
                    if (!mBind) {
                        mPreviewSwitch.setChecked(false);
                        Toast.makeText(this, "Need to bind service first", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    startPreview();
                } else {
                    if (mBind) {
                        stopPreview();
                    }
                }
                break;
            case R.id.transfer:
                if (isChecked) {
                    if (!mBind) {
                        mTransferSwitch.setChecked(false);
                        Toast.makeText(this, "Need to bind service first", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    startImageTransfer();
                } else {
                    if (mBind) {
                        stopImageTransfer();
                    }
                }
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBind){
            mVision.unbindService();
            StreamInfo[] infos = mVision.getActivatedStreamInfo();
            for(StreamInfo info : infos) {
                switch (info.getStreamType()) {
                    case StreamType.COLOR:
                        mVision.stopListenFrame(StreamType.COLOR);
                        break;
                    case StreamType.DEPTH:
                        mVision.stopListenFrame(StreamType.DEPTH);
                        break;
                }
            }
        }
    }
}
