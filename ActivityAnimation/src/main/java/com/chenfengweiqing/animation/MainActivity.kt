package com.chenfengweiqing.animation

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //第一个是指当前activity，第二个和第三个参数分别是进入动画和退出动画，
        // 需要注意的是我们启动activity的方式是使用ActivityCompat.startActivity，
        // 最后一个参数我们使用compat.toBundle
        button1.setOnClickListener(View.OnClickListener() {
            val compat = ActivityOptionsCompat.makeCustomAnimation(this,
                    android.R.anim.slide_in_left, android.R.anim.slide_in_left)
            ActivityCompat.startActivity(this,
                    Intent(this, SecondActivity::class.java), compat.toBundle())
        })

        button2.setOnClickListener(View.OnClickListener() {
            startActivity(Intent(MainActivity@ this, SecondActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_in_right)

        })
        button3.setOnClickListener(View.OnClickListener() {
            startActivity(Intent(MainActivity@ this, SecondActivity::class.java))
            overridePendingTransition(R.anim.slide_in_up, R.anim.slide_in_up)
        })

        button4.setOnClickListener(View.OnClickListener() {
            startActivity(Intent(MainActivity@ this, SecondActivity::class.java))
            overridePendingTransition(R.anim.slide_in_down, R.anim.slide_in_down)
        })

        button5.setOnClickListener(View.OnClickListener() {

        })

        button6.setOnClickListener(View.OnClickListener() {
            val compat = ActivityOptionsCompat.makeCustomAnimation(this,
                    android.R.anim.fade_in, android.R.anim.fade_out)
            ActivityCompat.startActivity(this,
                    Intent(this, SecondActivity::class.java), compat.toBundle())
        })

        //第1个参数是scale哪个view的大小，第2和3个参数是以view为基点，从哪开始动画，这里是该view的中心，
        // 4和5参数是新的activity从多大开始放大，这里是从无到有的过程
        button7.setOnClickListener(View.OnClickListener() {
            val compat = ActivityOptionsCompat.makeScaleUpAnimation(button4,
                    button4.getWidth() / 2, button4.getHeight() / 2, 0, 0)
            ActivityCompat.startActivity(this, Intent(this, SecondActivity::class.java),
                    compat.toBundle())
        })
        button8.setOnClickListener(View.OnClickListener() {
            val compat = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                    button5, "晨风维清")
            ActivityCompat.startActivity(this, Intent(this,
                    SecondActivity::class.java), compat.toBundle())
        })
        button9.setOnClickListener(View.OnClickListener() {

        })
        button10.setOnClickListener(View.OnClickListener() {

        })

    }
}
