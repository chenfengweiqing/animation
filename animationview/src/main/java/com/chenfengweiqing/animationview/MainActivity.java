package com.chenfengweiqing.animationview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.chenfengweiqing.animationview.view.DynamicHeartView;

public class MainActivity extends AppCompatActivity {
    private DynamicHeartView mDynamicHeartView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        mDynamicHeartView = findViewById(R.id.heart);
//        mDynamicHeartView.startPathAnim(1000);
    }
}
