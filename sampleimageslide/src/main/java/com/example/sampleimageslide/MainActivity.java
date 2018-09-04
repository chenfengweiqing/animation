package com.example.sampleimageslide;

import android.graphics.drawable.AnimationDrawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.sampleimageslide.view.ScrollerLayout;
import com.example.sampleimageslide.view.SimpleScaleImageView;

public class MainActivity extends AppCompatActivity {
    private SimpleScaleImageView mImageView;
    private ImageView imageView;
    private AnimationDrawable mAnimDrawable;
    private ScrollerLayout mScrollerLayout;
    private EditText mBottom, mTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_custom);
//        mImageView = findViewById(R.id.imageView);
//        mImageView.setBottomImage(R.drawable.bottom);
//        mImageView.setTopImage(R.drawable.top);
//        mImageView.setImage(R.drawable.center);

//        mScrollerLayout = findViewById(R.id.scrollView);
//        mBottom = findViewById(R.id.bottom);
//        mTop = findViewById(R.id.top);
//        imageView = findViewById(R.id.amm);
//        imageView.setBackgroundResource(R.drawable.anim_laoding);
//        mAnimDrawable = (AnimationDrawable) imageView.getBackground();
//        mAnimDrawable.start();
//        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                String bottom = mBottom.getText().toString();
//                String top = mTop.getText().toString();
//                if (!TextUtils.isEmpty(bottom) && !TextUtils.isEmpty(top)) {
//                    mScrollerLayout.setBottomSpeedScale(Float.parseFloat(bottom));
//                    mScrollerLayout.setTopSpeedScale(Float.parseFloat(top));
//                    Toast.makeText(getApplication(), "设置成功", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//
//        findViewById(R.id.music).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Log.d("liao", "onClick: music");
//            }
//        });
//        findViewById(R.id.music).setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View view) {
//                Log.d("liao", "onLongClick: music");
//                return false;
//            }
//        });

    }
}
