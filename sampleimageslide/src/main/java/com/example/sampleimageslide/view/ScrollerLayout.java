package com.example.sampleimageslide.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.OverScroller;

public class ScrollerLayout extends FrameLayout {
    private final static String TAG = "ScrollerLayout";
    /**
     * 用于完成滚动操作的实例
     */
    private OverScroller mScroller;
    /**
     * 界面可滚动的左边界
     */
    private int mLeftBorder;
    /**
     * 界面可滚动的右边界
     */
    private int mBottomRightBorder;
    private int mRightBorder;
    private int mTopRightBorder;
    private int mTouchSlop;
    private VelocityTracker mVelocityTracker;
    private int mMaxFlingVelocity, mMinFlingVelocity;
    private float x, y;

    protected Boolean isMove = false;
    protected float mDownX = 0, mDownY = 0;
    private int mPointerId;
    private int mWidth;
    private int mHight;

    private float mBottomSpeedScale = 0.3f;
    private float mTopSpeedScale = 1.5f;


    public ScrollerLayout(Context context) {
        super(context);
        init();
    }

    public ScrollerLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrollerLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mScroller = new OverScroller(getContext());
        final ViewConfiguration vc = ViewConfiguration.get(getContext());
        mTouchSlop = vc.getScaledTouchSlop();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            measureChild(childView, widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void setTopSpeedScale(float speedScale) {
        if (speedScale > 0) {
            this.mTopSpeedScale = speedScale;
        }
    }

    public void setBottomSpeedScale(float speedScale) {
        if (speedScale > 0) {
            this.mBottomSpeedScale = speedScale;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View childView = getChildAt(i);
                childView.layout(0, 0, childView.getMeasuredWidth(), childView.getMeasuredHeight());
            }
            mLeftBorder = getChildAt(1).getLeft();
            mBottomRightBorder = getChildAt(0).getRight();
            mRightBorder = getChildAt(1).getRight();
            mTopRightBorder = getChildAt(2).getRight();
            mWidth = getWidth();
            mHight = getHeight();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        boolean isIntercept = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isIntercept = !mScroller.isFinished();
                mPointerId = event.getPointerId(0);
                mDownX = x = event.getX();
                mDownY = y = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = event.findPointerIndex(mPointerId);
                float mx = event.getX(pointerIndex);
                float my = event.getY(pointerIndex);
                if (Math.abs(x - mx) >= mTouchSlop) {
                    isIntercept = true;
                }
                if (isIntercept) {
                    x = mx;
                    y = my;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                solvePointerUp(event);
                break;
        }
        return isIntercept;
    }

    private void solvePointerUp(MotionEvent event) {
        // 获取离开屏幕的手指的索引
        int pointerIndexLeave = event.getActionIndex();
        int pointerIdLeave = event.getPointerId(pointerIndexLeave);
        if (mPointerId == pointerIdLeave) {
            int reIndex = pointerIndexLeave == 0 ? 1 : 0;
            mPointerId = event.getPointerId(reIndex);
            x = event.getX(reIndex);
            y = event.getY(reIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isMove = false;
                mPointerId = event.getPointerId(0);
                x = event.getX();
                y = event.getY();
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                isMove = true;
                final int pointerIndex = event.findPointerIndex(mPointerId);
                float mx = event.getX(pointerIndex);
                float my = event.getY(pointerIndex);
                move((int) (x - mx), (int) (y - my));
                x = mx;
                y = my;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                isMove = false;
                int pointerIndexLeave = event.getActionIndex();
                int pointerIdLeave = event.getPointerId(pointerIndexLeave);
                if (mPointerId == pointerIdLeave) {
                    int reIndex = pointerIndexLeave == 0 ? 1 : 0;
                    mPointerId = event.getPointerId(reIndex);
                    x = event.getX(reIndex);
                    y = event.getY(reIndex);
                    if (mVelocityTracker != null)
                        mVelocityTracker.clear();
                }
                break;

            case MotionEvent.ACTION_UP:
                isMove = false;
                mVelocityTracker.computeCurrentVelocity(100, mMaxFlingVelocity);
                float velocityX = mVelocityTracker.getXVelocity(mPointerId);
                float velocityY = mVelocityTracker.getYVelocity(mPointerId);
                move(-velocityX, -velocityY);
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                isMove = false;
                break;
        }
        return true;
    }

    private void move(float velocityX, float velocityY) {
        int oneScroll = getChildAt(1).getScrollX();
        int offSet = ((int) velocityX) + oneScroll;

        if (oneScroll + mWidth > mRightBorder || offSet + mWidth > mRightBorder) {
            getChildAt(0).scrollTo(mBottomRightBorder - mWidth, 0);
            getChildAt(1).scrollTo(mRightBorder - mWidth, 0);
            getChildAt(2).scrollTo(mTopRightBorder - mWidth, 0);
        } else if (oneScroll < mLeftBorder || offSet < mLeftBorder) {
            getChildAt(0).scrollTo(mLeftBorder, 0);
            getChildAt(1).scrollTo(mLeftBorder, 0);
            getChildAt(2).scrollTo(mLeftBorder, 0);
        } else {
            getChildAt(0).scrollBy((int) (velocityX * mBottomSpeedScale), 0);
            getChildAt(1).scrollBy((int) velocityX, 0);
            getChildAt(2).scrollBy((int) (velocityX * mTopSpeedScale), 0);
        }

    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            invalidate();
        }
    }
}
