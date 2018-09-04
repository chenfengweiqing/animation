package com.example.videoplay.view;

import android.media.MediaPlayer;

public interface FrameVideoViewListener {
    void mediaPlayerPrepared(MediaPlayer mediaPlayer);

    void mediaPlayerPrepareFailed(MediaPlayer mediaPlayer, String error);
}
