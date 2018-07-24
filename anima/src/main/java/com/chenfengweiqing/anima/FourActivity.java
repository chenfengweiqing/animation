package com.chenfengweiqing.anima;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class FourActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_four);

        findViewById(R.id.title_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAfterTransition();
            }
        });
    }
}
