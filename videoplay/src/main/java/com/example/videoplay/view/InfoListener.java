package com.example.videoplay.view;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.View;

public class InfoListener implements MediaPlayer.OnInfoListener {
    private static final String TAG = InfoListener.class.getSimpleName();
    private View placeholderView;

    public InfoListener(View placeholderView) {
        this.placeholderView = placeholderView;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onInfo: what= " + what + "  extra= " + extra);
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            Log.d(TAG, "o[MEDIA_INFO_VIDEO_RENDERING_START] placeholder GONE");
            placeholderView.setVisibility(View.GONE);
            return true;
        }
        return false;
    }
}
