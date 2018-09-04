package com.example.videoplay;

import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;

import com.example.videoplay.view.FrameVideoView;
import com.example.videoplay.view.FrameVideoViewListener;
import com.example.videoplay.view.ImplType;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "liao";
    private FrameVideoView mFrameVideoView;
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        mFrameVideoView = findViewById(R.id.video_view);
//        mFrameVideoView.setImpl(ImplType.VIDEO_VIEW);
//        Uri uri = Uri.parse("/storage/emulated/0/Pictures/ROOBOCamera/VID_20180825_165430.mp4");
//        Log.d(TAG, "onCreate: uri " + uri);
//        mFrameVideoView.setup(uri);
//        mFrameVideoView.onResume();
//        mFrameVideoView.setFrameVideoViewListener(new FrameVideoViewListener() {
//            @Override
//            public void mediaPlayerPrepared(MediaPlayer mediaPlayer) {
//                Log.d(TAG, "mediaPlayerPrepared: ");
//            }
//
//            @Override
//            public void mediaPlayerPrepareFailed(MediaPlayer mediaPlayer, String error) {
//                Log.d(TAG, "mediaPlayerPrepareFailed: ");
//            }
//        });
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        //视频链接可能已失效
        String uri = "http://video.dispatch.tc.qq.com/77613075/x0021o8d3g3.mp4?sdtfrom=v1001&type=mp4&vkey=23289E4B8D0F4B6CF18703222DFD0038845D8F56A75EEC20D5D4FDE678093D9AB211EFD7F4C99E5A612A96A04F46CEEB483628CFFBEA493D3AADBFCB81A540F7A92193874192FA0F70D1099DF330B2B419D45736554CB9BB3435019C985F530C5960E4B20FEBD5FAED17DC9F1FCE1C73&platform=10902&fmt=auto&sp=350&guid=1175defd049d3301e047ce50d93e9c7a";

        player = new MediaPlayer();
        try {
            player.setDataSource(this, Uri.parse(uri));
            holder = surfaceView.getHolder();
            holder.addCallback(new MyCallBack());
            player.prepare();
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d(TAG, "onPrepared: ");
                    progressBar.setVisibility(View.INVISIBLE);
                    player.start();
                }
            });
        } catch (IOException e) {
            Log.d(TAG, "onCreate: IOException " + e);
            e.printStackTrace();
        }
    }

    private class MyCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated: ");
            player.setDisplay(holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged: ");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed: ");
        }
    }
}
