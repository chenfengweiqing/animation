package com.example.imageslide;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.example.imageslide.view.ImageSource;
import com.example.imageslide.view.SubSamplingScaleImageView;

public class MainActivity extends AppCompatActivity {
    private SubSamplingScaleImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.imageView);
        mImageView.setTopImage(ImageSource.resource(R.drawable.top));
        mImageView.setImage(ImageSource.resource(R.drawable.city));
        mImageView.setMaxScale(0.667f);
        mImageView.setMinScale(0.667f);
        mImageView.setZoomEnabled(false);
        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("liao", "setOnClickListener:   " + mImageView.getType());
            }
        });
    }

    private float getInitImageScale() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.city);
        WindowManager wm = this.getWindowManager();
        int width = wm.getDefaultDisplay().getWidth();
        int height = wm.getDefaultDisplay().getHeight();
        // 拿到图片的宽和高
        int dw = bitmap.getWidth();
        int dh = bitmap.getHeight();
        float scale = 1.0f;
        Log.d("liao", "getInitImageScale: width " + width + " height " + height + "  dw " + dw + " dh " + dh);
        //图片宽度大于屏幕，但高度小于屏幕，则缩小图片至填满屏幕宽
        if (dw > width && dh <= height) {
            scale = width * 1.0f / dw;
        }
        //图片宽度小于屏幕，但高度大于屏幕，则放大图片至填满屏幕宽
        if (dw <= width && dh > height) {
            scale = width * 1.0f / dw;
        }
        //图片高度和宽度都小于屏幕，则放大图片至填满屏幕宽
        if (dw < width && dh < height) {
            scale = width * 1.0f / dw;
        }
        //图片高度和宽度都大于屏幕，则缩小图片至填满屏幕宽
        if (dw > width && dh > height) {
            scale = width * 1.0f / dw;
        }
        return scale;
    }
}
