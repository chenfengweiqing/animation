package com.chenfengweiqing.animation

import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.second_activity.*

/**
 * Created by lcz on 17-10-9.
 */
class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.second_activity)
        button.setOnClickListener(View.OnClickListener() {
            ActivityCompat.finishAfterTransition(this)
        })
    }
}