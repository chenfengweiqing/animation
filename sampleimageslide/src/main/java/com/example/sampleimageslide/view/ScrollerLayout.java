package com.example.sampleimageslide.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.OverScroller;

public class ScrollerLayout extends FrameLayout {
    private final static String TAG = "ScrollerLayout";
    /**
     * The ScrollerLayout is not currently scrolling.（静止没有滚动）
     */
    public static final int SCROLL_STATE_IDLE = 0;
    /**
     * The RecyclerView is currently being dragged by outside input such as user touch input.
     * （正在被外部拖拽,一般为用户正在用手指滚动）
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * The RecyclerView is currently animating to a final position while not under outside control.
     * （自动滚动）
     */
    public static final int SCROLL_STATE_SETTLING = 2;
    /**
     * 界面可滚动的左边界
     */
    private int mLeftBorder;
    /**
     * 界面可滚动的右边界
     */
    private static final int INVALID_POINTER = -1;
    private int mBottomRightBorder, mRightBorder, mTopRightBorder;
    private int mDesLeftBorder;
    private int mWidth, mHeight;
    private int mTouchSlop;
    private int mMaxFlingVelocity, mMinFlingVelocity;
    private int mScrollState = SCROLL_STATE_IDLE;
    private int mScrollPointerId = INVALID_POINTER;
    private int mLastTouchX;
    private float mBottomSpeedScale = 0.5f;
    private float mTopSpeedScale = 1.5f;
    private ScrollerListener mScrollerListener;
    private VelocityTracker mVelocityTracker;
    private final ViewFling mViewFling = new ViewFling();
    private final int BOUND_OFFSET = 20;
    private static float SPEED_SCALE = 1.2f;

    public ScrollerLayout(Context context) {
        super(context);
        init(context);
    }

    public ScrollerLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ScrollerLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    }

    public void setScrollerListener(ScrollerListener listener) {
        mScrollerListener = listener;
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

    public int getLeftBorder() {
        return this.mDesLeftBorder - BOUND_OFFSET;
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
            mHeight = getHeight();
            getChildAt(0).scrollTo(0, 0);
            getChildAt(1).scrollTo(BOUND_OFFSET, 0);
            getChildAt(2).scrollTo(BOUND_OFFSET, 0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        boolean isIntercept = false;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScrollPointerId = event.getPointerId(0);
                mLastTouchX = (int) (event.getX() + 0.5f);
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = event.findPointerIndex(mScrollPointerId);
                int mx = (int) (event.getX(pointerIndex) + 0.5f);
                if (Math.abs(mLastTouchX - mx) >= mTouchSlop) {
                    isIntercept = true;
                    mLastTouchX = mx;
                } else {
                    isIntercept = false;
                }
                break;
        }
        return isIntercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        boolean eventAddedToVelocityTracker = false;
        final int action = MotionEventCompat.getActionMasked(event);
        final int actionIndex = MotionEventCompat.getActionIndex(event);
        final MotionEvent vtev = MotionEvent.obtain(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setScrollState(SCROLL_STATE_IDLE);
                mScrollPointerId = event.getPointerId(0);
                mLastTouchX = (int) (event.getX() + 0.5f);
                break;
            case MotionEventCompat.ACTION_POINTER_DOWN:
                mScrollPointerId = event.getPointerId(actionIndex);
                mLastTouchX = (int) (event.getX(actionIndex) + 0.5f);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = event.findPointerIndex(mScrollPointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id " + mScrollPointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                final int x = (int) (event.getX(index) + 0.5f);
                int dx = mLastTouchX - x;
                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    boolean startScroll = false;
                    if (Math.abs(dx) > mTouchSlop) {
                        if (dx > 0) {
                            dx -= mTouchSlop;
                        } else {
                            dx += mTouchSlop;
                        }
                        startScroll = true;
                    }
                    if (startScroll) {
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    mLastTouchX = x;
                    constrainScrollBy(dx, 0);
                }
                break;
            case MotionEventCompat.ACTION_POINTER_UP: {
                if (event.getPointerId(actionIndex) == mScrollPointerId) {
                    // Pick a new pointer to pick up the slack.
                    final int newIndex = actionIndex == 0 ? 1 : 0;
                    mScrollPointerId = event.getPointerId(newIndex);
                    mLastTouchX = (int) (event.getX(newIndex) + 0.5f);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(vtev);
                eventAddedToVelocityTracker = true;
                mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
                float xVelocity = -VelocityTrackerCompat.getXVelocity(mVelocityTracker, mScrollPointerId);
                if (Math.abs(xVelocity) < mMinFlingVelocity) {
                    xVelocity = 0F;
                } else {
                    xVelocity = Math.max(-mMaxFlingVelocity, Math.min(xVelocity, mMaxFlingVelocity));
                }
                int dxV = (int) xVelocity;
                int oneScroll = getChildAt(1).getScrollX();
                if (oneScroll <= mLeftBorder + BOUND_OFFSET || oneScroll >= mRightBorder - mWidth - BOUND_OFFSET || dxV == 0) {
                    setScrollState(SCROLL_STATE_IDLE);
                    int scrollX = getChildAt(1).getScrollX();
                    if (scrollX < BOUND_OFFSET) {
                        constrainScrollBy(BOUND_OFFSET - scrollX, 0);
                    } else if (scrollX + mWidth + BOUND_OFFSET >= mRightBorder) {
                        constrainScrollBy(mRightBorder - mWidth - scrollX - BOUND_OFFSET, 0);
                    }
                } else {
                    if (dxV + dxV + mWidth >= mRightBorder) {
                        dxV = mRightBorder - mWidth - oneScroll;
                    } else if (oneScroll + dxV <= 0) {
                        dxV = -oneScroll;
                    }
                    mViewFling.fling(dxV);
                }
                resetTouch();
                break;
            case MotionEvent.ACTION_CANCEL:
                resetTouch();
                break;
        }
        if (!eventAddedToVelocityTracker) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();
        return true;
    }

    private void resetTouch() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
    }

    private void setScrollState(int state) {
        if (state == mScrollState) {
            return;
        }
        mScrollState = state;
        if (mScrollerListener != null) {
            mScrollerListener.onScrollStateChanged(state != SCROLL_STATE_IDLE, mDesLeftBorder, 0);
        }
        if (state != SCROLL_STATE_SETTLING) {
            mViewFling.stop();
        }
    }

    private class ViewFling implements Runnable {
        private int mLastFlingX = 0;
        private OverScroller mScroller;
        private boolean mEatRunOnAnimationRequest = false;
        private boolean mReSchedulePostAnimationCallback = false;

        public ViewFling() {
            mScroller = new OverScroller(getContext());
        }

        @Override
        public void run() {
            disableRunOnAnimationRequests();
            if (mScroller.computeScrollOffset()) {
                final int x = mScroller.getCurrX();
                int dx = (int) (x * SPEED_SCALE - mLastFlingX);
                mLastFlingX = x;
                constrainScrollBy(dx, 0);
                postOnAnimation();
                if (mScroller.isFinished()) {
                    setScrollState(SCROLL_STATE_IDLE);
                    int scrollX = getChildAt(1).getScrollX();
                    if (scrollX < BOUND_OFFSET) {
                        constrainScrollBy(BOUND_OFFSET - scrollX, 0);
                    } else if (scrollX + mWidth + BOUND_OFFSET >= mRightBorder) {
                        constrainScrollBy(mRightBorder - mWidth - scrollX - BOUND_OFFSET, 0);
                    }
                }
            }
            enableRunOnAnimationRequests();
        }

        public void fling(int velocityX) {
            mLastFlingX = 0;
            setScrollState(SCROLL_STATE_SETTLING);
            mScroller.fling(0, 0, velocityX, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            postOnAnimation();
        }

        public void stop() {
            removeCallbacks(this);
            mScroller.abortAnimation();
        }

        private void disableRunOnAnimationRequests() {
            mReSchedulePostAnimationCallback = false;
            mEatRunOnAnimationRequest = true;
        }

        private void enableRunOnAnimationRequests() {
            mEatRunOnAnimationRequest = false;
            if (mReSchedulePostAnimationCallback) {
                postOnAnimation();
            }
        }

        void postOnAnimation() {
            if (mEatRunOnAnimationRequest) {
                mReSchedulePostAnimationCallback = true;
            } else {
                removeCallbacks(this);
                ViewCompat.postOnAnimation(ScrollerLayout.this, this);
            }
        }
    }

    private void constrainScrollBy(int dx, int dy) {
        int scrollX = getChildAt(1).getScrollX();
        Log.d(TAG, "constrainScrollBy: mRightBorder " + mRightBorder + " scrollX  " + scrollX + " dx " + dx);
        if (scrollX + dx + mWidth >= mRightBorder - BOUND_OFFSET) {
            mDesLeftBorder = mTopRightBorder - mWidth;
            int center = scrollX + dx;
            int top = getChildAt(2).getScrollX() + dx;
            if (scrollX + dx + mWidth >= mRightBorder) {
                center = mRightBorder - mWidth;
                top = mTopRightBorder - mWidth;
            }
            mDesLeftBorder = center;
            getChildAt(0).scrollTo(mBottomRightBorder - mWidth, 0);
            getChildAt(1).scrollTo(center, 0);
            getChildAt(2).scrollTo(top, 0);
        } else if (scrollX + dx <= mLeftBorder + BOUND_OFFSET) {
            int x = scrollX + dx;
            if (scrollX + dx <= mLeftBorder) {
                x = mLeftBorder;
            }
            mDesLeftBorder = x;
            getChildAt(0).scrollTo(mLeftBorder, 0);
            getChildAt(1).scrollTo(x, 0);
            getChildAt(2).scrollTo(x, 0);
        } else {
            mDesLeftBorder = scrollX + dx;
            getChildAt(0).scrollBy((int) (dx * mBottomSpeedScale), 0);
            getChildAt(1).scrollBy(dx, 0);
            getChildAt(2).scrollBy((int) (dx * mTopSpeedScale), 0);
        }
    }

    public interface ScrollerListener {
        void onScrollStateChanged(boolean isScrolling, int l, int t);
    }

}
