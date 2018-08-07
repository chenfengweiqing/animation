package com.example.sampleimageslide;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.sampleimageslide.view.SimpleScaleImageView;

public class MainActivity extends AppCompatActivity {
    private SimpleScaleImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.imageView);
        mImageView.setTopImage(R.drawable.top);
        mImageView.setImage(R.drawable.city);
        mImageView.setZoomEnabled(false);
        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("liao", "setOnClickListener:   " + mImageView.getType());
            }
        });
    }
}
