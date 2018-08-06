package com.example.sampleimageslide.view;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import com.example.sampleimageslide.R.styleable;
import com.example.sampleimageslide.decoder.SkiaImageDecoder;
import com.example.sampleimageslide.decoder.SkiaImageRegionDecoder;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After zooming in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pan and
 * zoom, tiles off screen or higher/lower resolution than required are discarded from memory.
 * </p><p>
 * Tiles are no larger than the max supported bitmap size, so with large images tiling may be used even when zoomed out.
 * </p><p>
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * <br>
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image pixels (scaled)
 * <br>
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped source file pixels
 * </p><p>
 * <a href="https://github.com/davemorrissey/subsampling-scale-image-view">View project on GitHub</a>
 * </p>
 */
@SuppressWarnings("unused")
public class SimpleScaleImageView extends View {

    private static final String TAG = SimpleScaleImageView.class.getSimpleName();

    /**
     * Attempt to use EXIF information on the image to rotate it. Works for external files only.
     */
    public static final int ORIENTATION_USE_EXIF = -1;
    /**
     * Display the image file in its native orientation.
     */
    public static final int ORIENTATION_0 = 0;
    /**
     * Rotate the image 90 degrees clockwise.
     */
    public static final int ORIENTATION_90 = 90;
    /**
     * Rotate the image 180 degrees.
     */
    public static final int ORIENTATION_180 = 180;
    /**
     * Rotate the image 270 degrees clockwise.
     */
    public static final int ORIENTATION_270 = 270;

    private static final List<Integer> VALID_ORIENTATIONS = Arrays.asList(ORIENTATION_0, ORIENTATION_90, ORIENTATION_180, ORIENTATION_270, ORIENTATION_USE_EXIF);

    /**
     * During zoom animation, keep the point of the image that was tapped in the same place, and scale the image around it.
     */
    public static final int ZOOM_FOCUS_FIXED = 1;
    /**
     * During zoom animation, move the point of the image that was tapped to the center of the screen.
     */
    public static final int ZOOM_FOCUS_CENTER = 2;
    /**
     * Zoom in to and center the tapped point immediately without animating.
     */
    public static final int ZOOM_FOCUS_CENTER_IMMEDIATE = 3;

    private static final List<Integer> VALID_ZOOM_STYLES = Arrays.asList(ZOOM_FOCUS_FIXED, ZOOM_FOCUS_CENTER, ZOOM_FOCUS_CENTER_IMMEDIATE);

    /**
     * Quadratic ease out. Not recommended for scale animation, but good for panning.
     */
    public static final int EASE_OUT_QUAD = 1;
    /**
     * Quadratic ease in and out.
     */
    public static final int EASE_IN_OUT_QUAD = 2;

    private static final List<Integer> VALID_EASING_STYLES = Arrays.asList(EASE_IN_OUT_QUAD, EASE_OUT_QUAD);

    /**
     * Don't allow the image to be panned off screen. As much of the image as possible is always displayed, centered in the view when it is smaller. This is the best option for galleries.
     */
    public static final int PAN_LIMIT_INSIDE = 1;
    /**
     * Allows the image to be panned until it is just off screen, but no further. The edge of the image will stop when it is flush with the screen edge.
     */
    public static final int PAN_LIMIT_OUTSIDE = 2;
    /**
     * Allows the image to be panned until a corner reaches the center of the screen but no further. Useful when you want to pan any spot on the image to the exact center of the screen.
     */
    public static final int PAN_LIMIT_CENTER = 3;

    private static final List<Integer> VALID_PAN_LIMITS = Arrays.asList(PAN_LIMIT_INSIDE, PAN_LIMIT_OUTSIDE, PAN_LIMIT_CENTER);

    /**
     * Scale the image so that both dimensions of the image will be equal to or less than the corresponding dimension of the view. The image is then centered in the view. This is the default behaviour and best for galleries.
     */
    public static final int SCALE_TYPE_CENTER_INSIDE = 1;
    /**
     * Scale the image uniformly so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The image is then centered in the view.
     */
    public static final int SCALE_TYPE_CENTER_CROP = 2;
    /**
     * Scale the image so that both dimensions of the image will be equal to or less than the maxScale and equal to or larger than minScale. The image is then centered in the view.
     */
    public static final int SCALE_TYPE_CUSTOM = 3;
    /**
     * Scale the image so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The top left is shown.
     */
    public static final int SCALE_TYPE_START = 4;

    private static final List<Integer> VALID_SCALE_TYPES = Arrays.asList(SCALE_TYPE_CENTER_CROP, SCALE_TYPE_CENTER_INSIDE, SCALE_TYPE_CUSTOM, SCALE_TYPE_START);

    /**
     * State change originated from animation.
     */
    public static final int ORIGIN_ANIM = 1;
    /**
     * State change originated from touch gesture.
     */
    public static final int ORIGIN_TOUCH = 2;
    /**
     * State change originated from a fling momentum anim.
     */
    public static final int ORIGIN_FLING = 3;
    /**
     * State change originated from a double tap zoom anim.
     */
    public static final int ORIGIN_DOUBLE_TAP_ZOOM = 4;

    // Bitmap (preview or full image)
    private Bitmap bitmap;

    // Bitmap (preview or full image)
    private Bitmap topBitmap;

    // Whether the bitmap is a preview image
    private boolean bitmapIsPreview;

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    private boolean bitmapIsCached;

    // Uri of full size image
    private Uri uri;
    private Uri topUri;

    // Sample size used to display the whole image when fully zoomed out
    private int fullImageSampleSize;

    // Overlay tile boundaries and other info
    private boolean debug = true;

    // Image orientation setting
    private int orientation = ORIENTATION_0;

    // Max scale allowed (prevent infinite zoom)
    private float maxScale = 2F;

    // Min scale allowed (prevent infinite zoom)
    private float minScale = minScale();

    // Density to reach before loading higher resolution tiles
    private int minimumTileDpi = -1;

    // Pan limiting style
    private int panLimit = PAN_LIMIT_INSIDE;

    // Minimum scale type
    private int minimumScaleType = SCALE_TYPE_CENTER_INSIDE;

    // overrides for the dimensions of the generated tiles
    public static final int TILE_SIZE_AUTO = Integer.MAX_VALUE;
    private int maxTileWidth = TILE_SIZE_AUTO;
    private int maxTileHeight = TILE_SIZE_AUTO;

    // An executor service for loading of images
    private Executor executor = AsyncTask.THREAD_POOL_EXECUTOR;

    // Whether tiles should be loaded while gestures and animations are still in progress
    private boolean eagerLoadingEnabled = true;

    // Gesture detection settings
    private boolean panEnabled = true;
    private boolean zoomEnabled = true;
    private boolean quickScaleEnabled = true;
    private boolean initialiseBaseLayer;

    // Double tap zoom behaviour
    private float doubleTapZoomScale = 1F;
    private int doubleTapZoomStyle = ZOOM_FOCUS_FIXED;
    private int doubleTapZoomDuration = 500;

    // Current scale and scale at start of zoom
    private float scale;
    private float scaleStart;

    // Screen coordinate of top-left corner of source image
    private PointF vTranslate;
    private PointF vTranslateStart;
    private PointF vTranslateBefore;
    private int count = 1;

    // Source coordinate to center on, used when new position is set externally before view is ready
    private Float pendingScale;
    private PointF sPendingCenter;
    private PointF sRequestedCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private int sWidth;
    private int sHeight;
    private int sTopWidth;
    private int sTopHeight;
    private int sOrientation;

    // Is two-finger zooming in progress
    private boolean isZooming;
    // Is one-finger panning in progress
    private boolean isPanning;
    // Is quick-scale gesture in progress
    private boolean isQuickScaling;
    // Max touches used in current gesture
    private int maxTouchCount;

    // Fling detector
    private GestureDetector detector;
    private GestureDetector singleDetector;

    private final ReadWriteLock decoderLock = new ReentrantReadWriteLock(true);
    private SkiaImageDecoder skiaImageDecoder = new SkiaImageDecoder();
    private SkiaImageRegionDecoder skiaImageRegionDecoder = new SkiaImageRegionDecoder();

    // Debug values
    private PointF vCenterStart;
    private float vDistStart;

    // Current quickscale state
    private final float quickScaleThreshold;
    private float quickScaleLastDistance;
    private boolean quickScaleMoved;
    private PointF quickScaleVLastPoint;
    private PointF quickScaleSCenter;
    private PointF quickScaleVStart;

    // Scale and center animation tracking
    private Anim anim;
    private PointF pointF;
    private AnimationBuilder flingAnimationBuilder;
    private PointF flingPointF;

    // Whether a ready notification has been sent to subclasses
    private boolean readySent;
    // Whether a base layer loaded notification has been sent to subclasses
    private boolean imageLoadedSent;

    // Event listener
    private OnImageEventListener onImageEventListener;

    // Scale and center listener
    private OnStateChangedListener onStateChangedListener;

    // Long click listener
    private OnLongClickListener onLongClickListener;

    // Long click handler
    private final Handler handler;
    private static final int MESSAGE_LONG_CLICK = 1;

    // Paint objects created once and reused for efficiency
    private Paint bitmapPaint;
    private Paint topBitmapPaint;
    private Paint tileBgPaint;
    private Paint topTileBgPaint;

    // Volatile fields used to reduce object creation
    private ScaleAndTranslate satTemp;
    private Matrix matrix;
    private RectF sRect;
    private RectF sTopRect;
    private final float[] srcArray = new float[8];
    private final float[] dstArray = new float[8];

    //The logical density of the display
    private final float density;

    // A global preference for bitmap format, available to decoder classes that respect it
    private static Bitmap.Config preferredBitmapConfig;

    public SimpleScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        density = getResources().getDisplayMetrics().density;
        setMinimumDpi(160);
        setDoubleTapZoomDpi(160);
        setMinimumTileDpi(320);
        setGestureDetector(context);
        this.handler = new Handler(new Handler.Callback() {
            public boolean handleMessage(Message message) {
                if (message.what == MESSAGE_LONG_CLICK && onLongClickListener != null) {
                    maxTouchCount = 0;
                    SimpleScaleImageView.super.setOnLongClickListener(onLongClickListener);
                    performLongClick();
                    SimpleScaleImageView.super.setOnLongClickListener(null);
                }
                return true;
            }
        });
        // Handle XML attributes
        if (attr != null) {
            TypedArray typedAttr = getContext().obtainStyledAttributes(attr, styleable.SubSamplingScaleImageView);
            if (typedAttr.hasValue(styleable.SubSamplingScaleImageView_src)) {
                int resId = typedAttr.getResourceId(styleable.SubSamplingScaleImageView_src, 0);
                if (resId > 0) {
                    setImage(ImageSource.resource(resId).tilingEnabled());
                }
            }
            if (typedAttr.hasValue(styleable.SubSamplingScaleImageView_panEnabled)) {
                setPanEnabled(typedAttr.getBoolean(styleable.SubSamplingScaleImageView_panEnabled, true));
            }
            if (typedAttr.hasValue(styleable.SubSamplingScaleImageView_zoomEnabled)) {
                setZoomEnabled(typedAttr.getBoolean(styleable.SubSamplingScaleImageView_zoomEnabled, true));
            }
            if (typedAttr.hasValue(styleable.SubSamplingScaleImageView_quickScaleEnabled)) {
                setQuickScaleEnabled(typedAttr.getBoolean(styleable.SubSamplingScaleImageView_quickScaleEnabled, true));
            }
            if (typedAttr.hasValue(styleable.SubSamplingScaleImageView_tileBackgroundColor)) {
                setTileBackgroundColor(typedAttr.getColor(styleable.SubSamplingScaleImageView_tileBackgroundColor, Color.argb(0, 0, 0, 0)));
            }
            typedAttr.recycle();
        }

        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, context.getResources().getDisplayMetrics());
    }

    public SimpleScaleImageView(Context context) {
        this(context, null);
    }

    /**
     * instances can read this and use it when decoding images.
     *
     * @return the preferred bitmap configuration, or null if none has been set.
     */
    public static Bitmap.Config getPreferredBitmapConfig() {
        return preferredBitmapConfig;
    }

    /**
     * Set a global preferred bitmap config shared by all view instances and applied to new instances
     * an instance-specific config) but custom decoder classes will not.
     *
     * @param preferredBitmapConfig the bitmap configuration to be used by future instances of the view. Pass null to restore the default.
     */
    public static void setPreferredBitmapConfig(Bitmap.Config preferredBitmapConfig) {
        SimpleScaleImageView.preferredBitmapConfig = preferredBitmapConfig;
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset, because it may waste
     * loading of tiles. However, this can be freely called at any time.
     *
     * @param orientation orientation to be set. See ORIENTATION_ static fields for valid values.
     */
    public final void setOrientation(int orientation) {
        if (!VALID_ORIENTATIONS.contains(orientation)) {
            throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
        this.orientation = orientation;
        reset(false);
        invalidate();
        requestLayout();
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI.
     *
     * @param imageSource Image source.
     */
    public final void setTopImage(@NonNull ImageSource imageSource) {
        if (imageSource == null) {
            throw new NullPointerException("imageSource must not be null");
        }
        topUri = imageSource.getUri();
        if (topUri == null && imageSource.getResource() != null) {
            topUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getContext().getPackageName() + "/" + imageSource.getResource());
        }
        boolean tile = imageSource.getTile();
//        debug("setTopImage  tile " + tile + " topUri " + topUri);
        BitmapLoadTask task = new BitmapLoadTask(this, getContext(), skiaImageDecoder, topUri, false, true);
        execute(task);
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    private synchronized void onTopImageLoaded(Bitmap bitmap, int sOrientation) {
//        debug("onTopImageLoaded sTopWidth " + sTopWidth + " bitmap width " + bitmap.getWidth() + " sTopHeight " +
//                sTopHeight + " bitmap height " + bitmap.getHeight() + " this.topBitmap " + this.topBitmap);
        if (this.topBitmap != null) {
            this.topBitmap.recycle();
        }
        this.topBitmap = bitmap;
        this.sTopWidth = bitmap.getWidth();
        this.sTopHeight = bitmap.getHeight();
        invalidate();
        requestLayout();
    }


    /**
     * Set the image source from a bitmap, resource, asset, file or other URI.
     *
     * @param imageSource Image source.
     */
    public final void setImage(@NonNull ImageSource imageSource) {
        //noinspection ConstantConditions
        if (imageSource == null) {
            throw new NullPointerException("imageSource must not be null");
        }
        reset(true);
        uri = imageSource.getUri();
        if (uri == null && imageSource.getResource() != null) {
            uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getContext().getPackageName() + "/" + imageSource.getResource());
        }
//        debug("setImage: getTile " + imageSource.getTile() + " uri " + uri);
        if (imageSource.getTile()) {
            // Load the bitmap using tile decoding.
            TilesInitTask task = new TilesInitTask(this, getContext(), skiaImageRegionDecoder, uri);
            execute(task);
        } else {
            // Load the bitmap as a single image.
            BitmapLoadTask task = new BitmapLoadTask(this, getContext(), skiaImageDecoder, uri, false, false);
            execute(task);
        }
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private void reset(boolean newImage) {
//        debug("reset newImage " + newImage);
        scale = 0.6667f;
        scaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        vTranslateBefore = null;
        pendingScale = 0f;
        sPendingCenter = null;
        sRequestedCenter = null;
        isZooming = false;
        isPanning = false;
        isQuickScaling = false;
        maxTouchCount = 0;
        fullImageSampleSize = 0;
        vCenterStart = null;
        vDistStart = 0;
        quickScaleLastDistance = 0f;
        quickScaleMoved = false;
        quickScaleSCenter = null;
        quickScaleVLastPoint = null;
        quickScaleVStart = null;
        anim = null;
        satTemp = null;
        matrix = null;
        sRect = null;
        if (newImage) {
            uri = null;
            if (bitmap != null && !bitmapIsCached) {
                bitmap.recycle();
            }
            if (bitmap != null && bitmapIsCached && onImageEventListener != null) {
                onImageEventListener.onPreviewReleased();
            }
            sWidth = 0;
            sHeight = 0;
            sOrientation = 0;
            readySent = false;
            imageLoadedSent = false;
            bitmap = null;
            bitmapIsPreview = false;
            bitmapIsCached = false;
        }
        setGestureDetector(getContext());
    }

    private void setGestureDetector(final Context context) {
        this.detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (panEnabled && readySent && vTranslate != null && e1 != null && e2 != null && (Math.abs(e1.getX() - e2.getX()) > 50 || Math.abs(e1.getY() - e2.getY()) > 50) && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !isZooming) {
                    float sCenterXEnd = ((getWidth() / 2) - (vTranslate.x + (velocityX * 0.25f))) / scale;
                    float sCenterYEnd = ((getHeight() / 2) - (vTranslate.y + (velocityY * 0.25f))) / scale;
                    if (flingPointF == null) {
                        flingPointF = new PointF(sCenterXEnd, sCenterYEnd);
                    } else {
                        flingPointF.x = sCenterXEnd;
                        flingPointF.y = sCenterYEnd;
                    }
                    if (flingAnimationBuilder == null) {
                        flingAnimationBuilder = new AnimationBuilder(flingPointF).withEasing(EASE_OUT_QUAD).withPanLimited(false).withOrigin(ORIGIN_FLING);
                    } else {
                        flingAnimationBuilder.setTargetSCenter(flingPointF);
                    }
                    flingAnimationBuilder.start();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return super.onDoubleTapEvent(e);
            }
        });

        singleDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                return true;
            }
        });
    }

    /**
     * On resize, preserve center and scale. Various behaviours are possible, override this method to use another.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        PointF sCenter = getCenter();
//        debug("onSizeChanged %dx%d -> %dx%d  readySent %s sCenter %s", oldw, oldh, w, h, readySent, sCenter);
        if (readySent && sCenter != null) {
            this.anim = null;
            this.pendingScale = scale;
            this.sPendingCenter = sCenter;
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth();
                height = sHeight();
            } else if (resizeHeight) {
                height = (int) ((((double) sHeight() / (double) sWidth()) * width));
            } else if (resizeWidth) {
                width = (int) ((((double) sWidth() / (double) sHeight()) * height));
            }
        }
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // During non-interruptible anims, ignore all touch events
        if (anim != null && !anim.interruptible) {
            requestDisallowInterceptTouchEvent(true);
            return true;
        } else {
            if (anim != null && anim.listener != null) {
                try {
                    anim.listener.onInterruptedByUser();
                } catch (Exception e) {
                    Log.w(TAG, "Error thrown by animation listener", e);
                }
            }
            anim = null;
        }

        // Abort if not ready
        if (vTranslate == null) {
            if (singleDetector != null) {
                singleDetector.onTouchEvent(event);
            }
            return true;
        }
        // Detect flings, taps and double taps
        if (!isQuickScaling && (detector == null || detector.onTouchEvent(event))) {
            isZooming = false;
            isPanning = false;
            maxTouchCount = 0;
            return true;
        }

        if (vTranslateStart == null) {
            vTranslateStart = new PointF(0, 0);
        }
        if (vTranslateBefore == null) {
            vTranslateBefore = new PointF(0, 0);
        }
        if (vCenterStart == null) {
            vCenterStart = new PointF(0, 0);
        }

        // Store current values so we can send an event if they change
        float scaleBefore = scale;
        vTranslateBefore.set(vTranslate);

        boolean handled = onTouchEventInternal(event);
        sendStateChanged(scaleBefore, vTranslateBefore, ORIGIN_TOUCH);
        return handled || super.onTouchEvent(event);
    }

    public int getType() {
        return currentType;
    }

    private int currentType;

    @SuppressWarnings("deprecation")
    private boolean onTouchEventInternal(@NonNull MotionEvent event) {
        int touchCount = event.getPointerCount();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                anim = null;
                requestDisallowInterceptTouchEvent(true);
                maxTouchCount = Math.max(maxTouchCount, touchCount);
                if (touchCount >= 2) {
                    if (zoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        scaleStart = scale;
                        vDistStart = distance;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        vCenterStart.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
                    } else {
                        // Abort all gestures on second touch
                        maxTouchCount = 0;
                    }
                    // Cancel long click timer
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                } else if (!isQuickScaling) {
                    // Start one-finger pan
                    vTranslateStart.set(vTranslate.x, vTranslate.y);
                    vCenterStart.set(event.getX(), event.getY());

                    // Start long click timer
                    handler.sendEmptyMessageDelayed(MESSAGE_LONG_CLICK, 600);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean consumed = false;
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        float vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        float vCenterEndX = (event.getX(0) + event.getX(1)) / 2;
                        float vCenterEndY = (event.getY(0) + event.getY(1)) / 2;

                        if (zoomEnabled && (distance(vCenterStart.x, vCenterEndX, vCenterStart.y, vCenterEndY) > 5 || Math.abs(vDistEnd - vDistStart) > 5 || isPanning)) {
                            isZooming = true;
                            isPanning = true;
                            consumed = true;

                            double previousScale = scale;
                            scale = Math.min(maxScale, (vDistEnd / vDistStart) * scaleStart);

                            if (scale <= minScale()) {
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                vDistStart = vDistEnd;
                                scaleStart = minScale();
                                vCenterStart.set(vCenterEndX, vCenterEndY);
                                vTranslateStart.set(vTranslate);
                            } else if (panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (scale / scaleStart);
                                float vTopNow = vTopStart * (scale / scaleStart);
                                vTranslate.x = vCenterEndX - vLeftNow;
                                vTranslate.y = vCenterEndY - vTopNow;
                                if ((previousScale * sHeight() < getHeight() && scale * sHeight() >= getHeight()) || (previousScale * sWidth() < getWidth() && scale * sWidth() >= getWidth())) {
                                    fitToBounds(true);
                                    vCenterStart.set(vCenterEndX, vCenterEndY);
                                    vTranslateStart.set(vTranslate);
                                    scaleStart = scale;
                                    vDistStart = vDistEnd;
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate.x = (getWidth() / 2) - (scale * sRequestedCenter.x);
                                vTranslate.y = (getHeight() / 2) - (scale * sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate.x = (getWidth() / 2) - (scale * (sWidth() / 2));
                                vTranslate.y = (getHeight() / 2) - (scale * (sHeight() / 2));
                            }

                            fitToBounds(true);
                        }
                    } else if (isQuickScaling) {
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        float dist = Math.abs(quickScaleVStart.y - event.getY()) * 2 + quickScaleThreshold;

                        if (quickScaleLastDistance == -1f) {
                            quickScaleLastDistance = dist;
                        }
                        boolean isUpwards = event.getY() > quickScaleVLastPoint.y;
                        quickScaleVLastPoint.set(0, event.getY());

                        float spanDiff = Math.abs(1 - (dist / quickScaleLastDistance)) * 0.5f;

                        if (spanDiff > 0.03f || quickScaleMoved) {
                            quickScaleMoved = true;

                            float multiplier = 1;
                            if (quickScaleLastDistance > 0) {
                                multiplier = isUpwards ? (1 + spanDiff) : (1 - spanDiff);
                            }

                            double previousScale = scale;
                            scale = Math.max(minScale(), Math.min(maxScale, scale * multiplier));

                            if (panEnabled) {
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (scale / scaleStart);
                                float vTopNow = vTopStart * (scale / scaleStart);
                                vTranslate.x = vCenterStart.x - vLeftNow;
                                vTranslate.y = vCenterStart.y - vTopNow;
                                if ((previousScale * sHeight() < getHeight() && scale * sHeight() >= getHeight()) || (previousScale * sWidth() < getWidth() && scale * sWidth() >= getWidth())) {
                                    fitToBounds(true);
                                    vCenterStart.set(sourceToViewCoord(quickScaleSCenter));
                                    vTranslateStart.set(vTranslate);
                                    scaleStart = scale;
                                    dist = 0;
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate.x = (getWidth() / 2) - (scale * sRequestedCenter.x);
                                vTranslate.y = (getHeight() / 2) - (scale * sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate.x = (getWidth() / 2) - (scale * (sWidth() / 2));
                                vTranslate.y = (getHeight() / 2) - (scale * (sHeight() / 2));
                            }
                        }

                        quickScaleLastDistance = dist;

                        fitToBounds(true);
                        consumed = true;
                    } else if (!isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.
                        float dx = Math.abs(event.getX() - vCenterStart.x);
                        float dy = Math.abs(event.getY() - vCenterStart.y);

                        //On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        float offset = density * 5;
                        if (dx > offset || dy > offset || isPanning) {
                            consumed = true;
                            vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                            vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                            float lastX = vTranslate.x;
                            float lastY = vTranslate.y;
                            fitToBounds(true);
                            boolean atXEdge = lastX != vTranslate.x;
                            boolean atYEdge = lastY != vTranslate.y;
                            boolean edgeXSwipe = atXEdge && dx > dy && !isPanning;
                            boolean edgeYSwipe = atYEdge && dy > dx && !isPanning;
                            boolean yPan = lastY == vTranslate.y && dy > offset * 3;
                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                                isPanning = true;
                            } else if (dx > offset || dy > offset) {
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount = 0;
                                handler.removeMessages(MESSAGE_LONG_CLICK);
                                requestDisallowInterceptTouchEvent(false);
                            }
                            if (!panEnabled) {
                                vTranslate.x = vTranslateStart.x;
                                vTranslate.y = vTranslateStart.y;
                                requestDisallowInterceptTouchEvent(false);
                            }
                        }
                    }
                }
                if (consumed) {
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                float aa = event.getX() - vTranslate.x;
                if (aa < 500.0f) {
                    currentType = 1;
                } else if (aa < 1000.0f) {
                    currentType = 2;
                } else if (aa < 1500.0f) {
                    currentType = 3;
                } else if (aa < 2000.0f) {
                    currentType = 4;
                } else if (aa < 2500.0f) {
                    currentType = 5;
                } else if (aa < 3000.0f) {
                    currentType = 6;
                } else {
                    currentType = 7;
                }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                handler.removeMessages(MESSAGE_LONG_CLICK);
                if (isQuickScaling) {
                    isQuickScaling = false;
                }
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            vCenterStart.set(event.getX(0), event.getY(0));
                        } else {
                            vCenterStart.set(event.getX(1), event.getY(1));
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false;
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false;
                        maxTouchCount = 0;
                    }
                }
                if (touchCount == 1) {
                    isZooming = false;
                    isPanning = false;
                    maxTouchCount = 0;
                }
                return true;
        }
        return false;
    }

    private void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is setup, tiles are displayed as they are loaded.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        createPaints();

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || getWidth() == 0 || getHeight() == 0) {
//            debug("onDraw sWidth is 0 ");
            return;
        }

        if (!initialiseBaseLayer) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas));
        }
        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
        // dimensions and therefore the first opportunity to set scale and translate. If this call returns
        // false there is nothing to be drawn so return immediately.
        if (!checkReady()) {
//            debug("onDraw checkReady false ");
            return;
        }
        // Set scale and translate before draw.
        preDraw();

//        debug("onDraw anim  " + anim + " bitmap " + bitmap + " topBitmap " + topBitmap +
//                " bitmapIsPreview  " + bitmapIsPreview + " matrix " + matrix + " tileBgPaint " + tileBgPaint + " topTileBgPaint " +
//                topTileBgPaint + "  vTranslate " + vTranslate);
        // If animating scale, calculate current scale and center with easing equations
        if (anim != null && anim.vFocusStart != null) {
            // Store current values so we can send an event if they change
//            debug("onDraw anim  vFocusStart ");
            float scaleBefore = scale;
            if (vTranslateBefore == null) {
                vTranslateBefore = new PointF(0, 0);
            }
            vTranslateBefore.set(vTranslate);

            long scaleElapsed = System.currentTimeMillis() - anim.time;
            boolean finished = scaleElapsed > anim.duration;
            scaleElapsed = Math.min(scaleElapsed, anim.duration);
            scale = ease(anim.easing, scaleElapsed, anim.scaleStart, anim.scaleEnd - anim.scaleStart, anim.duration);

            // Apply required animation to the focal point
            float vFocusNowX = ease(anim.easing, scaleElapsed, anim.vFocusStart.x, anim.vFocusEnd.x - anim.vFocusStart.x, anim.duration);
            float vFocusNowY = ease(anim.easing, scaleElapsed, anim.vFocusStart.y, anim.vFocusEnd.y - anim.vFocusStart.y, anim.duration);
            // Find out where the focal point is at this scale and adjust its position to follow the animation path
            vTranslate.x -= sourceToViewX(anim.sCenterEnd.x) - vFocusNowX;
            vTranslate.y -= sourceToViewY(anim.sCenterEnd.y) - vFocusNowY;

            // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
            fitToBounds(finished || (anim.scaleStart == anim.scaleEnd));
            sendStateChanged(scaleBefore, vTranslateBefore, anim.origin);
            if (finished) {
                if (anim.listener != null) {
                    try {
                        anim.listener.onComplete();
                    } catch (Exception e) {
                        Log.w(TAG, "Error thrown by animation listener", e);
                    }
                }
                anim = null;
            }
            invalidate();
        }

//        debug(" isBaseLayerReady " + isBaseLayerReady());

        if (bitmap != null) {
            float xScale = scale, yScale = scale;
            if (bitmapIsPreview) {
                xScale = scale * ((float) sWidth / bitmap.getWidth());
                yScale = scale * ((float) sHeight / bitmap.getHeight());
            }

            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.reset();
            matrix.postScale(xScale, yScale);
            matrix.postRotate(getRequiredRotation());
            matrix.postTranslate(vTranslate.x, vTranslate.y);

            if (getRequiredRotation() == ORIENTATION_180) {
                matrix.postTranslate(scale * sWidth, scale * sHeight);
            } else if (getRequiredRotation() == ORIENTATION_90) {
                matrix.postTranslate(scale * sHeight, 0);
            } else if (getRequiredRotation() == ORIENTATION_270) {
                matrix.postTranslate(0, scale * sWidth);
            }

            if (tileBgPaint != null) {
                if (sRect == null) {
                    sRect = new RectF();
                }
                sRect.set(0f, 0f, bitmapIsPreview ? bitmap.getWidth() : sWidth, bitmapIsPreview ? bitmap.getHeight() : sHeight);
                matrix.mapRect(sRect);
                canvas.drawRect(sRect, tileBgPaint);
            }
            canvas.drawBitmap(bitmap, matrix, bitmapPaint);

        }

        if (topBitmap != null) {
            float xScale = scale, yScale = scale;
            if (bitmapIsPreview) {
                xScale = scale * ((float) sTopWidth / topBitmap.getWidth());
                yScale = scale * ((float) sTopHeight / topBitmap.getHeight());
            }

            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.reset();
            matrix.postScale(xScale, yScale);
            matrix.postRotate(getRequiredRotation());
            count++;
            if (count > 10) {
                count = 1;
            }
            matrix.postTranslate(vTranslate.x + 100 * count, vTranslate.y + 200);

            if (getRequiredRotation() == ORIENTATION_180) {
                matrix.postTranslate(scale * sWidth, scale * sHeight);
            } else if (getRequiredRotation() == ORIENTATION_90) {
                matrix.postTranslate(scale * sHeight, 0);
            } else if (getRequiredRotation() == ORIENTATION_270) {
                matrix.postTranslate(0, scale * sWidth);
            }

            if (topTileBgPaint != null) {
                if (sTopRect == null) {
                    sTopRect = new RectF();
                }
                sTopRect.set(0f, 0f, bitmapIsPreview ? topBitmap.getWidth() : sTopWidth, bitmapIsPreview ? topBitmap.getHeight() : sTopHeight);
                matrix.mapRect(sTopRect);
                canvas.drawRect(sTopRect, topTileBgPaint);
            }
            canvas.drawBitmap(topBitmap, matrix, topBitmapPaint);
        }
    }

    /**
     * Helper method for setting the values of a tile matrix array.
     */
    private void setMatrixArray(float[] array, float f0, float f1, float f2, float f3, float f4, float f5, float f6, float f7) {
        array[0] = f0;
        array[1] = f1;
        array[2] = f2;
        array[3] = f3;
        array[4] = f4;
        array[5] = f5;
        array[6] = f6;
        array[7] = f7;
    }

    /**
     * Checks whether the base layer of tiles or full size bitmap is ready.
     */
    private boolean isBaseLayerReady() {
        if (bitmap != null && !bitmapIsPreview) {
            return true;
        }
        return false;
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    private boolean checkReady() {
        boolean ready = getWidth() > 0 && getHeight() > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isBaseLayerReady());
//        debug("checkReady readySent " + readySent + " ready " + ready + " bitmap " + bitmap + " isBaseLayerReady " + isBaseLayerReady());
        if (!readySent && ready) {
            preDraw();
            readySent = true;
            onReady();
            if (onImageEventListener != null) {
                onImageEventListener.onReady();
            }
        }
        return ready;
    }

    /**
     * Check whether either the full size bitmap or base layer tiles are loaded. First time, send image
     * loaded event to listener.
     */
    private boolean checkImageLoaded() {
        boolean imageLoaded = isBaseLayerReady();
//        debug("checkImageLoaded imageLoaded " + imageLoaded + " imageLoadedSent " + imageLoadedSent);
        if (!imageLoadedSent && imageLoaded) {
            preDraw();
            imageLoadedSent = true;
            onImageLoaded();
            if (onImageEventListener != null) {
                onImageEventListener.onImageLoaded();
            }
        }
        return imageLoaded;
    }

    /**
     * Creates Paint objects once when first needed.
     */
    private void createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint.setDither(true);
        }
        if (topBitmapPaint == null) {
            topBitmapPaint = new Paint();
            topBitmapPaint.setAntiAlias(true);
            topBitmapPaint.setFilterBitmap(true);
            topBitmapPaint.setDither(true);
            topBitmapPaint.setAlpha(100);
        }
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    private synchronized void initialiseBaseLayer(@NonNull Point maxTileDimensions) {
        satTemp = new ScaleAndTranslate(0f, new PointF(0, 0));
        fitToBounds(true, satTemp);

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp.scale);
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2;
        }

//        debug("initialiseBaseLayer maxTileDimensions=%dx%d fullImageSampleSize %s",
//                maxTileDimensions.x, maxTileDimensions.y, fullImageSampleSize);
        if (fullImageSampleSize == 1 && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            // Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
            // Use BitmapDecoder for better image support.
            initialiseBaseLayer = true;
            BitmapLoadTask task = new BitmapLoadTask(this, getContext(), skiaImageDecoder, uri, false, false);
            execute(task);
        }
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private void preDraw() {
        if (getWidth() == 0 || getHeight() == 0 || sWidth <= 0 || sHeight <= 0) {
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            if (vTranslate == null) {
                vTranslate = new PointF();
            }
            vTranslate.x = (getWidth() / 2) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight() / 2) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            fitToBounds(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false);
    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private int calculateInSampleSize(float scale) {
        if (minimumTileDpi > 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
            scale = (minimumTileDpi / averageDpi) * scale;
        }

        int reqWidth = (int) (sWidth() * scale);
        int reqHeight = (int) (sHeight() * scale);

        // Raw height and width of image
        int inSampleSize = 1;
        if (reqWidth == 0 || reqHeight == 0) {
            return 32;
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) sHeight() / (float) reqHeight);
            final int widthRatio = Math.round((float) sWidth() / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        int power = 1;
        while (power * 2 < inSampleSize) {
            power = power * 2;
        }

        return power;
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat    The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    private void fitToBounds(boolean center, ScaleAndTranslate sat) {
        if (panLimit == PAN_LIMIT_OUTSIDE && isReady()) {
            center = false;
        }

        PointF vTranslate = sat.vTranslate;
        float scale = limitedScale(sat.scale);
        float scaleWidth = scale * sWidth();
        float scaleHeight = scale * sHeight();

        if (panLimit == PAN_LIMIT_CENTER && isReady()) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() / 2 - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() / 2 - scaleHeight);
        } else if (center) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() - scaleHeight);
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight);
        }

        // Asymmetric padding adjustments
        float xPaddingRatio = getPaddingLeft() > 0 || getPaddingRight() > 0 ? getPaddingLeft() / (float) (getPaddingLeft() + getPaddingRight()) : 0.5f;
        float yPaddingRatio = getPaddingTop() > 0 || getPaddingBottom() > 0 ? getPaddingTop() / (float) (getPaddingTop() + getPaddingBottom()) : 0.5f;

        float maxTx;
        float maxTy;
        if (panLimit == PAN_LIMIT_CENTER && isReady()) {
            maxTx = Math.max(0, getWidth() / 2);
            maxTy = Math.max(0, getHeight() / 2);
        } else if (center) {
            maxTx = Math.max(0, (getWidth() - scaleWidth) * xPaddingRatio);
            maxTy = Math.max(0, (getHeight() - scaleHeight) * yPaddingRatio);
        } else {
            maxTx = Math.max(0, getWidth());
            maxTy = Math.max(0, getHeight());
        }

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        sat.scale = scale;
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    private void fitToBounds(boolean center) {
        boolean init = false;
        if (vTranslate == null) {
            init = true;
            vTranslate = new PointF(0, 0);
        }
        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        satTemp.scale = scale;
        satTemp.vTranslate.set(vTranslate);
        fitToBounds(center, satTemp);
        scale = satTemp.scale;
        vTranslate.set(satTemp.vTranslate);
        if (init && minimumScaleType != SCALE_TYPE_START) {
            vTranslate.set(vTranslateForSCenter(sWidth() / 2, sHeight() / 2, scale));
        }
    }

    /**
     * Async task used to get image details without blocking the UI thread.
     */
    private static class TilesInitTask extends AsyncTask<Void, Void, int[]> {
        private final WeakReference<SimpleScaleImageView> viewRef;
        private final WeakReference<Context> contextRef;
        private final WeakReference<SkiaImageRegionDecoder> skiaImageRegionDecoderRef;
        private final Uri source;
        private Exception exception;

        TilesInitTask(SimpleScaleImageView view, Context context, SkiaImageRegionDecoder skiaImageRegionDecoder, Uri source) {
            this.viewRef = new WeakReference<>(view);
            this.contextRef = new WeakReference<>(context);
            this.skiaImageRegionDecoderRef = new WeakReference<>(skiaImageRegionDecoder);
            this.source = source;
        }

        @Override
        protected int[] doInBackground(Void... params) {
            try {
                String sourceUri = source.toString();
                Context context = contextRef.get();
                SkiaImageRegionDecoder decoderFactory = skiaImageRegionDecoderRef.get();
                SimpleScaleImageView view = viewRef.get();
                if (context != null && decoderFactory != null && view != null) {
//                    view.debug("TilesInitTask.doInBackground");
                    Point dimensions = decoderFactory.init(context, source);
                    int sWidth = dimensions.x;
                    int sHeight = dimensions.y;
                    int exifOrientation = view.getExifOrientation(context, sourceUri);
                    return new int[]{sWidth, sHeight, exifOrientation};
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialise bitmap decoder", e);
                this.exception = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(int[] xyo) {
            final SimpleScaleImageView view = viewRef.get();
            if (view != null) {
                if (xyo != null && xyo.length == 3) {
                    view.onTilesInited(xyo[0], xyo[1], xyo[2]);
                } else if (exception != null && view.onImageEventListener != null) {
                    view.onImageEventListener.onImageLoadError(exception);
                }
            }
        }
    }

    /**
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    private synchronized void onTilesInited(int sWidth, int sHeight, int sOrientation) {
//        debug("onTilesInited sWidth=%d, sHeight=%d, sOrientation=%d", sWidth, sHeight, orientation);
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
//            debug("onTilesInited reset");
            reset(false);
            if (bitmap != null) {
                if (!bitmapIsCached) {
                    bitmap.recycle();
                }
                bitmap = null;
                if (onImageEventListener != null && bitmapIsCached) {
                    onImageEventListener.onPreviewReleased();
                }
                bitmapIsPreview = false;
                bitmapIsCached = false;
            }
        }
        this.sWidth = sWidth;
        this.sHeight = sHeight;
        this.sOrientation = sOrientation;
        checkReady();
        initialiseBaseLayer(new Point(maxTileWidth, maxTileHeight));
        invalidate();
        requestLayout();
    }

    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    private synchronized void onTileLoaded() {
//        debug("onTileLoaded");
        checkReady();
        checkImageLoaded();
        if (isBaseLayerReady() && bitmap != null) {
            if (!bitmapIsCached) {
                bitmap.recycle();
            }
            bitmap = null;
            if (onImageEventListener != null && bitmapIsCached) {
                onImageEventListener.onPreviewReleased();
            }
            bitmapIsPreview = false;
            bitmapIsCached = false;
        }
        invalidate();
    }

    /**
     * Async task used to load bitmap without blocking the UI thread.
     */
    private static class BitmapLoadTask extends AsyncTask<Void, Void, Integer> {
        private final WeakReference<SimpleScaleImageView> viewRef;
        private final WeakReference<Context> contextRef;
        private final WeakReference<SkiaImageDecoder> skiaImageDecoderRef;
        private final Uri source;
        private final boolean preview;
        private final boolean isTop;
        private Bitmap bitmap;
        private Exception exception;

        BitmapLoadTask(SimpleScaleImageView view, Context context, SkiaImageDecoder skiaImageDecoder, Uri source, boolean preview, boolean isTop) {
            this.viewRef = new WeakReference<>(view);
            this.contextRef = new WeakReference<>(context);
            this.skiaImageDecoderRef = new WeakReference<>(skiaImageDecoder);
            this.source = source;
            this.preview = preview;
            this.isTop = isTop;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                String sourceUri = source.toString();
                Context context = contextRef.get();
                SkiaImageDecoder imageDecoder = skiaImageDecoderRef.get();
                SimpleScaleImageView view = viewRef.get();
                if (context != null && imageDecoder != null && view != null) {
//                    view.debug("BitmapLoadTask.doInBackground");
                    bitmap = imageDecoder.decode(context, source);
                    return view.getExifOrientation(context, sourceUri);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load bitmap", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer orientation) {
            SimpleScaleImageView subSamplingScaleImageView = viewRef.get();
            if (subSamplingScaleImageView != null) {
                if (bitmap != null && orientation != null) {
                    if (preview) {
                        subSamplingScaleImageView.onPreviewLoaded(bitmap);
                    } else {
                        if (isTop) {
                            subSamplingScaleImageView.onTopImageLoaded(bitmap, orientation);
                        } else {
                            subSamplingScaleImageView.onImageLoaded(bitmap, orientation, false);
                        }
                    }
                } else if (exception != null && subSamplingScaleImageView.onImageEventListener != null) {
                    if (preview) {
                        subSamplingScaleImageView.onImageEventListener.onPreviewLoadError(exception);
                    } else {
                        subSamplingScaleImageView.onImageEventListener.onImageLoadError(exception);
                    }
                }
            }
        }
    }

    /**
     * Called by worker task when preview image is loaded.
     */
    private synchronized void onPreviewLoaded(Bitmap previewBitmap) {
//        debug("onPreviewLoaded");
        if (bitmap != null || imageLoadedSent) {
            previewBitmap.recycle();
            return;
        }
        bitmap = previewBitmap;
        bitmapIsPreview = true;
        if (checkReady()) {
            invalidate();
            requestLayout();
        }
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    private synchronized void onImageLoaded(Bitmap bitmap, int sOrientation, boolean bitmapIsCached) {
//        debug("onImageLoaded sWidth " + sWidth + " bitmap width " + bitmap.getWidth() + " sHeight " +
//                sHeight + " bitmap height " + bitmap.getHeight() + " this.bitmap " + this.bitmap + " bitmapIsCached " + this.bitmapIsCached);
//        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != bitmap.getWidth() || this.sHeight != bitmap.getHeight())) {
            reset(false);
        }
        if (this.bitmap != null && !this.bitmapIsCached) {
            this.bitmap.recycle();
        }

        if (this.bitmap != null && this.bitmapIsCached && onImageEventListener != null) {
            onImageEventListener.onPreviewReleased();
        }

        this.bitmapIsPreview = false;
        this.bitmapIsCached = bitmapIsCached;
        this.bitmap = bitmap;
        this.sWidth = bitmap.getWidth();
        this.sHeight = bitmap.getHeight();
        this.sOrientation = sOrientation;
        boolean ready = checkReady();
        boolean imageLoaded = checkImageLoaded();
//        debug("onImageLoaded  ready " + ready + " imageLoaded " + imageLoaded);
        if (ready || imageLoaded) {
            invalidate();
            requestLayout();
        }
    }

    /**
     * Helper method for load tasks. Examines the EXIF info on the image file to determine the orientation.
     * This will only work for external files, not assets, resources or other URIs.
     */
    @AnyThread
    private int getExifOrientation(Context context, String sourceUri) {
        int exifOrientation = ORIENTATION_0;
        if (sourceUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = null;
            try {
                String[] columns = {MediaStore.Images.Media.ORIENTATION};
                cursor = context.getContentResolver().query(Uri.parse(sourceUri), columns, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int orientation = cursor.getInt(0);
                        if (VALID_ORIENTATIONS.contains(orientation) && orientation != ORIENTATION_USE_EXIF) {
                            exifOrientation = orientation;
                        } else {
                            Log.w(TAG, "Unsupported orientation: " + orientation);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get orientation of image from media store");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (sourceUri.startsWith(ImageSource.FILE_SCHEME) && !sourceUri.startsWith(ImageSource.ASSET_SCHEME)) {
            try {
                ExifInterface exifInterface = new ExifInterface(sourceUri.substring(ImageSource.FILE_SCHEME.length() - 1));
                int orientationAttr = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientationAttr == ExifInterface.ORIENTATION_NORMAL || orientationAttr == ExifInterface.ORIENTATION_UNDEFINED) {
                    exifOrientation = ORIENTATION_0;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_90) {
                    exifOrientation = ORIENTATION_90;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_180) {
                    exifOrientation = ORIENTATION_180;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_270) {
                    exifOrientation = ORIENTATION_270;
                } else {
                    Log.w(TAG, "Unsupported EXIF orientation: " + orientationAttr);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get EXIF orientation of image");
            }
        }
        return exifOrientation;
    }

    private void execute(AsyncTask<Void, Void, ?> asyncTask) {
        asyncTask.executeOnExecutor(executor);
    }

    private static class Anim {

        private float scaleStart; // Scale at start of anim
        private float scaleEnd; // Scale at end of anim (target)
        private PointF sCenterStart; // Source center point at start
        private PointF sCenterEnd; // Source center point at end, adjusted for pan limits
        private PointF sCenterEndRequested; // Source center point that was requested, without adjustment
        private PointF vFocusStart; // View point that was double tapped
        private PointF vFocusEnd; // Where the view focal point should be moved to during the anim
        private long duration = 500; // How long the anim takes
        private boolean interruptible = true; // Whether the anim can be interrupted by a touch
        private int easing = EASE_IN_OUT_QUAD; // Easing style
        private int origin = ORIGIN_ANIM; // Animation origin (API, double tap or fling)
        private long time = System.currentTimeMillis(); // Start time
        private OnAnimationEventListener listener; // Event listener

    }

    private static class ScaleAndTranslate {
        private ScaleAndTranslate(float scale, PointF vTranslate) {
            this.scale = scale;
            this.vTranslate = vTranslate;
        }

        private float scale;
        private final PointF vTranslate;
    }

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing {@link #TILE_SIZE_AUTO} will re-enable the default behaviour.
     *
     * @param maxPixels Maximum tile size X and Y in pixels.
     */
    public void setMaxTileSize(int maxPixels) {
        this.maxTileWidth = maxPixels;
        this.maxTileHeight = maxPixels;
    }

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing {@link #TILE_SIZE_AUTO} will re-enable the default behaviour.
     *
     * @param maxPixelsX Maximum tile width.
     * @param maxPixelsY Maximum tile height.
     */
    public void setMaxTileSize(int maxPixelsX, int maxPixelsY) {
        this.maxTileWidth = maxPixelsX;
        this.maxTileHeight = maxPixelsY;
    }

    /**
     * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
     */
    @NonNull
    private Point getMaxBitmapDimensions(Canvas canvas) {
        return new Point(Math.min(canvas.getMaximumBitmapWidth(), maxTileWidth), Math.min(canvas.getMaximumBitmapHeight(), maxTileHeight));
    }

    /**
     * Get source width taking rotation into account.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private int sWidth() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sHeight;
        } else {
            return sWidth;
        }
    }

    /**
     * Get source height taking rotation into account.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private int sHeight() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sWidth;
        } else {
            return sHeight;
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    @AnyThread
    private void fileSRect(Rect sRect, Rect target) {
        if (getRequiredRotation() == 0) {
            target.set(sRect);
        } else if (getRequiredRotation() == 90) {
            target.set(sRect.top, sHeight - sRect.right, sRect.bottom, sHeight - sRect.left);
        } else if (getRequiredRotation() == 180) {
            target.set(sWidth - sRect.right, sHeight - sRect.bottom, sWidth - sRect.left, sHeight - sRect.top);
        } else {
            target.set(sWidth - sRect.bottom, sRect.left, sWidth - sRect.top, sRect.right);
        }
    }

    /**
     * Determines the rotation to be applied to tiles, based on EXIF orientation or chosen setting.
     */
    @AnyThread
    private int getRequiredRotation() {
        if (orientation == ORIENTATION_USE_EXIF) {
            return sOrientation;
        } else {
            return orientation;
        }
    }

    /**
     * Pythagoras distance between two points.
     */
    private float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
     * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
     * but state (scale and center) is forgotten. You can restore these yourself if required.
     */
    public void recycle() {
        reset(true);
        bitmapPaint = null;
        tileBgPaint = null;
        topTileBgPaint = null;
        topBitmapPaint = null;
    }

    /**
     * Convert screen to source x coordinate.
     */
    private float viewToSourceX(float vx) {
        if (vTranslate == null) {
            return Float.NaN;
        }
        return (vx - vTranslate.x) / scale;
    }

    /**
     * Convert screen to source y coordinate.
     */
    private float viewToSourceY(float vy) {
        if (vTranslate == null) {
            return Float.NaN;
        }
        return (vy - vTranslate.y) / scale;
    }

    /**
     * Converts a rectangle within the view to the corresponding rectangle from the source file, taking
     * into account the current scale, translation, orientation and clipped region. This can be used
     * to decode a bitmap from the source file.
     * <p>
     * This method will only work when the image has fully initialised, after {@link #isReady()} returns
     * true. It is not guaranteed to work with preloaded bitmaps.
     * <p>
     * The result is written to the fRect argument. Re-use a single instance for efficiency.
     *
     * @param vRect rectangle representing the view area to interpret.
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    public void viewToFileRect(Rect vRect, Rect fRect) {
        if (vTranslate == null || !readySent) {
            return;
        }
        fRect.set(
                (int) viewToSourceX(vRect.left),
                (int) viewToSourceY(vRect.top),
                (int) viewToSourceX(vRect.right),
                (int) viewToSourceY(vRect.bottom));
        fileSRect(fRect, fRect);
        fRect.set(
                Math.max(0, fRect.left),
                Math.max(0, fRect.top),
                Math.min(sWidth, fRect.right),
                Math.min(sHeight, fRect.bottom)
        );
    }

    /**
     * Find the area of the source file that is currently visible on screen, taking into account the
     * current scale, translation, orientation and clipped region. This is a convenience method; see
     * {@link #viewToFileRect(Rect, Rect)}.
     *
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    public void visibleFileRect(Rect fRect) {
        if (vTranslate == null || !readySent) {
            return;
        }
        fRect.set(0, 0, getWidth(), getHeight());
        viewToFileRect(fRect, fRect);
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    @Nullable
    public final PointF viewToSourceCoord(PointF vxy) {
        return viewToSourceCoord(vxy.x, vxy.y, new PointF());
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    @Nullable
    public final PointF viewToSourceCoord(float vx, float vy) {
        return viewToSourceCoord(vx, vy, new PointF());
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy     view coordinates to convert.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @Nullable
    public final PointF viewToSourceCoord(PointF vxy, @NonNull PointF sTarget) {
        return viewToSourceCoord(vxy.x, vxy.y, sTarget);
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx      view X coordinate.
     * @param vy      view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @Nullable
    public final PointF viewToSourceCoord(float vx, float vy, @NonNull PointF sTarget) {
        if (vTranslate == null) {
            return null;
        }
        sTarget.set(viewToSourceX(vx), viewToSourceY(vy));
        return sTarget;
    }

    /**
     * Convert source to view x coordinate.
     */
    private float sourceToViewX(float sx) {
        if (vTranslate == null) {
            return Float.NaN;
        }
        return (sx * scale) + vTranslate.x;
    }

    /**
     * Convert source to view y coordinate.
     */
    private float sourceToViewY(float sy) {
        if (vTranslate == null) {
            return Float.NaN;
        }
        return (sy * scale) + vTranslate.y;
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    @Nullable
    public final PointF sourceToViewCoord(PointF sxy) {
        return sourceToViewCoord(sxy.x, sxy.y, new PointF());
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @return view coordinates.
     */
    @Nullable
    public final PointF sourceToViewCoord(float sx, float sy) {
        return sourceToViewCoord(sx, sy, new PointF());
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy     source coordinates to convert.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public final PointF sourceToViewCoord(PointF sxy, @NonNull PointF vTarget) {
        return sourceToViewCoord(sxy.x, sxy.y, vTarget);
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx      source X coordinate.
     * @param sy      source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    @Nullable
    public final PointF sourceToViewCoord(float sx, float sy, @NonNull PointF vTarget) {
        if (vTranslate == null) {
            return null;
        }
        vTarget.set(sourceToViewX(sx), sourceToViewY(sy));
        return vTarget;
    }

    /**
     * Convert source rect to screen rect, integer values.
     */
    private void sourceToViewRect(@NonNull Rect sRect, @NonNull Rect vTarget) {
        vTarget.set(
                (int) sourceToViewX(sRect.left),
                (int) sourceToViewY(sRect.top),
                (int) sourceToViewX(sRect.right),
                (int) sourceToViewY(sRect.bottom)
        );
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
    @NonNull
    private PointF vTranslateForSCenter(float sCenterX, float sCenterY, float scale) {
        int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) / 2;
        int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) / 2;
        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        satTemp.scale = scale;
        satTemp.vTranslate.set(vxCenter - (sCenterX * scale), vyCenter - (sCenterY * scale));
        fitToBounds(true, satTemp);
        return satTemp.vTranslate;
    }

    /**
     * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
     * pan limits, keeping the requested center as near to the middle of the screen as allowed.
     */
    @NonNull
    private PointF limitedSCenter(float sCenterX, float sCenterY, float scale, @NonNull PointF sTarget) {
        PointF vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale);
        int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) / 2;
        int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) / 2;
        float sx = (vxCenter - vTranslate.x) / scale;
        float sy = (vyCenter - vTranslate.y) / scale;
        sTarget.set(sx, sy);
        return sTarget;
    }

    /**
     * Returns the minimum allowed scale.
     */
    private float minScale() {
        int vPadding = getPaddingBottom() + getPaddingTop();
        int hPadding = getPaddingLeft() + getPaddingRight();
        if (minimumScaleType == SCALE_TYPE_CENTER_CROP || minimumScaleType == SCALE_TYPE_START) {
            return Math.max((getWidth() - hPadding) / (float) sWidth(), (getHeight() - vPadding) / (float) sHeight());
        } else if (minimumScaleType == SCALE_TYPE_CUSTOM && minScale > 0) {
            return minScale;
        } else {
            return Math.min((getWidth() - hPadding) / (float) sWidth(), (getHeight() - vPadding) / (float) sHeight());
        }
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    private float limitedScale(float targetScale) {
        targetScale = Math.max(minScale(), targetScale);
        targetScale = Math.min(maxScale, targetScale);
        return targetScale;
    }

    /**
     * Apply a selected type of easing.
     *
     * @param type     Easing type, from static fields
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private float ease(int type, long time, float from, float change, long duration) {
        switch (type) {
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(time, from, change, duration);
            case EASE_OUT_QUAD:
                return easeOutQuad(time, from, change, duration);
            default:
                throw new IllegalStateException("Unexpected easing type: " + type);
        }
    }

    /**
     * Quadratic easing for fling. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private float easeOutQuad(long time, float from, float change, long duration) {
        float progress = (float) time / (float) duration;
        return -change * progress * (progress - 2) + from;
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private float easeInOutQuad(long time, float from, float change, long duration) {
        float timeF = time / (duration / 2f);
        if (timeF < 1) {
            return (change / 2f * timeF * timeF) + from;
        } else {
            timeF--;
            return (-change / 2f) * (timeF * (timeF - 2) - 1) + from;
        }
    }

    /**
     * Debug logger
     */
    @AnyThread
    private void debug(String message, Object... args) {
        if (debug) {
            Log.d(TAG, String.format(message, args));
        }
    }

    /**
     * For debug overlays. Scale pixel value according to screen density.
     */
    private int px(int px) {
        return (int) (density * px);
    }

    /**
     * Set the pan limiting style. See static fields. Normally {@link #PAN_LIMIT_INSIDE} is best, for image galleries.
     *
     * @param panLimit a pan limit constant. See static fields.
     */
    public final void setPanLimit(int panLimit) {
        if (!VALID_PAN_LIMITS.contains(panLimit)) {
            throw new IllegalArgumentException("Invalid pan limit: " + panLimit);
        }
        this.panLimit = panLimit;
        if (isReady()) {
            fitToBounds(true);
            invalidate();
        }
    }

    /**
     * Set the minimum scale type. See static fields. Normally {@link #SCALE_TYPE_CENTER_INSIDE} is best, for image galleries.
     *
     * @param scaleType a scale type constant. See static fields.
     */
    public final void setMinimumScaleType(int scaleType) {
        if (!VALID_SCALE_TYPES.contains(scaleType)) {
            throw new IllegalArgumentException("Invalid scale type: " + scaleType);
        }
        this.minimumScaleType = scaleType;
        if (isReady()) {
            fitToBounds(true);
            invalidate();
        }
    }

    /**
     * Set the maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish to set this according
     * to screen density - on a retina screen, 1:1 may still be too small. Consider using {@link #setMinimumDpi(int)},
     * which is density aware.
     *
     * @param maxScale maximum scale expressed as a source/view pixels ratio.
     */
    public final void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    /**
     * Set the minimum scale allowed. A value of 1 means 1:1 pixels at minimum scale. You may wish to set this according
     * to screen density. Consider using {@link #setMaximumDpi(int)}, which is density aware.
     *
     * @param minScale minimum scale expressed as a source/view pixels ratio.
     */
    public final void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    /**
     * This is a screen density aware alternative to {@link #setMaxScale(float)}; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     *
     * @param dpi Source image pixel density at maximum zoom.
     */
    public final void setMinimumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setMaxScale(averageDpi / dpi);
    }

    /**
     * This is a screen density aware alternative to {@link #setMinScale(float)}; it allows you to express the minimum
     * allowed scale in terms of the maximum pixel density.
     *
     * @param dpi Source image pixel density at minimum zoom.
     */
    public final void setMaximumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setMinScale(averageDpi / dpi);
    }

    /**
     * Returns the maximum allowed scale.
     *
     * @return the maximum scale as a source/view pixels ratio.
     */
    public float getMaxScale() {
        return maxScale;
    }

    /**
     * Returns the minimum allowed scale.
     *
     * @return the minimum scale as a source/view pixels ratio.
     */
    public final float getMinScale() {
        return minScale();
    }

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen this may not be
     * necessary, and may increase the likelihood of an OutOfMemoryError. This method sets a DPI at which higher
     * resolution tiles should be loaded. Using a lower number will on average use less memory but result in a lower
     * quality image. 160-240dpi will usually be enough. This should be called before setting the image source,
     * because it affects which tiles get loaded. When using an untiled source image this method has no effect.
     *
     * @param minimumTileDpi Tile loading threshold.
     */
    public void setMinimumTileDpi(int minimumTileDpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        this.minimumTileDpi = (int) Math.min(averageDpi, minimumTileDpi);
        if (isReady()) {
            reset(false);
            invalidate();
        }
    }

    /**
     * Returns the source point at the center of the view.
     *
     * @return the source coordinates current at the center of the view.
     */
    @Nullable
    public final PointF getCenter() {
        int mX = getWidth() / 2;
        int mY = getHeight() / 2;
        return viewToSourceCoord(mX, mY);
    }

    /**
     * Returns the current scale value.
     *
     * @return the current scale as a source/view pixels ratio.
     */
    public final float getScale() {
        return scale;
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     *
     * @param scale   New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    public final void setScaleAndCenter(float scale, @Nullable PointF sCenter) {
//        debug("setScaleAndCenter");
        this.anim = null;
        this.pendingScale = scale;
        this.sPendingCenter = sCenter;
        this.sRequestedCenter = sCenter;
        invalidate();
    }

    /**
     * Call to find whether the view is initialised, has dimensions, and will display an image on
     * the next draw. If a preview has been provided, it may be the preview that will be displayed
     * and the full size image may still be loading. If no preview was provided, this is called once
     * the base layer tiles of the full size image are loaded.
     *
     * @return true if the view is ready to display an image and accept touch gestures.
     */
    public final boolean isReady() {
        return readySent;
    }

    /**
     * Called once when the view is initialised, has dimensions, and will display an image on the
     * next draw. This is triggered at the same time as {@link OnImageEventListener#onReady()} but
     * allows a subclass to receive this event without using a listener.
     */
    @SuppressWarnings("EmptyMethod")
    protected void onReady() {

    }

    /**
     * Call to find whether the main image (base layer tiles where relevant) have been loaded. Before
     * this event the view is blank unless a preview was provided.
     *
     * @return true if the main image (not the preview) has been loaded and is ready to display.
     */
    public final boolean isImageLoaded() {
        return imageLoadedSent;
    }

    /**
     * Called once when the full size image or its base layer tiles have been loaded.
     */
    @SuppressWarnings("EmptyMethod")
    protected void onImageLoaded() {

    }

    /**
     * Get source width, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you can use {@link #getSHeight()}
     * for the apparent width.
     *
     * @return the source image width in pixels.
     */
    public final int getSWidth() {
        return sWidth;
    }

    /**
     * Get source height, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you can use {@link #getSWidth()}
     * for the apparent height.
     *
     * @return the source image height in pixels.
     */
    public final int getSHeight() {
        return sHeight;
    }

    /**
     * Returns the orientation setting. This can return {@link #ORIENTATION_USE_EXIF}, in which case it doesn't tell you
     * the applied orientation of the image. For that, use {@link #getAppliedOrientation()}.
     *
     * @return the orientation setting. See static fields.
     */
    public final int getOrientation() {
        return orientation;
    }

    /**
     * Returns the actual orientation of the image relative to the source file. This will be based on the source file's
     * EXIF orientation if you're using ORIENTATION_USE_EXIF. Values are 0, 90, 180, 270.
     *
     * @return the orientation applied after EXIF information has been extracted. See static fields.
     */
    public final int getAppliedOrientation() {
        return getRequiredRotation();
    }

    /**
     * Returns true if zoom gesture detection is enabled.
     *
     * @return true if zoom gesture detection is enabled.
     */
    public final boolean isZoomEnabled() {
        return zoomEnabled;
    }

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the the current scale.
     *
     * @param zoomEnabled true to enable zoom gestures, false to disable.
     */
    public final void setZoomEnabled(boolean zoomEnabled) {
        this.zoomEnabled = zoomEnabled;
    }

    /**
     * Returns true if double tap &amp; swipe to zoom is enabled.
     *
     * @return true if double tap &amp; swipe to zoom is enabled.
     */
    public final boolean isQuickScaleEnabled() {
        return quickScaleEnabled;
    }

    /**
     * Enable or disable double tap &amp; swipe to zoom.
     *
     * @param quickScaleEnabled true to enable quick scale, false to disable.
     */
    public final void setQuickScaleEnabled(boolean quickScaleEnabled) {
        this.quickScaleEnabled = quickScaleEnabled;
    }

    /**
     * Returns true if pan gesture detection is enabled.
     *
     * @return true if pan gesture detection is enabled.
     */
    public final boolean isPanEnabled() {
        return panEnabled;
    }

    /**
     * Enable or disable pan gesture detection. Disabling pan causes the image to be centered. Pan
     * can still be changed from code.
     *
     * @param panEnabled true to enable panning, false to disable.
     */
    public final void setPanEnabled(boolean panEnabled) {
        this.panEnabled = panEnabled;
        if (!panEnabled && vTranslate != null) {
            vTranslate.x = (getWidth() / 2) - (scale * (sWidth() / 2));
            vTranslate.y = (getHeight() / 2) - (scale * (sHeight() / 2));
            if (isReady()) {
                invalidate();
            }
        }
    }

    /**
     * Set a solid color to render behind tiles, useful for displaying transparent PNGs.
     *
     * @param tileBgColor Background color for tiles.
     */
    public final void setTileBackgroundColor(int tileBgColor) {
        if (Color.alpha(tileBgColor) == 0) {
            tileBgPaint = null;
            topTileBgPaint = null;
        } else {
            tileBgPaint = new Paint();
            tileBgPaint.setStyle(Style.FILL);
            tileBgPaint.setColor(tileBgColor);
            topTileBgPaint = new Paint();
            topTileBgPaint.setStyle(Style.FILL);
            topTileBgPaint.setColor(tileBgColor);
            topTileBgPaint.setAlpha(0);
        }
        invalidate();
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     *
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomScale(float doubleTapZoomScale) {
        this.doubleTapZoomScale = doubleTapZoomScale;
    }

    /**
     * A density aware alternative to {@link #setDoubleTapZoomScale(float)}; this allows you to express the scale the
     * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
     * be ignored. A sensible starting point is 160 - the default used by this view.
     *
     * @param dpi New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setDoubleTapZoomScale(averageDpi / dpi);
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     *
     * @param doubleTapZoomStyle New value for zoom style.
     */
    public final void setDoubleTapZoomStyle(int doubleTapZoomStyle) {
        if (!VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)) {
            throw new IllegalArgumentException("Invalid zoom style: " + doubleTapZoomStyle);
        }
        this.doubleTapZoomStyle = doubleTapZoomStyle;
    }

    /**
     * Set the duration of the double tap zoom animation.
     *
     * @param durationMs Duration in milliseconds.
     */
    public final void setDoubleTapZoomDuration(int durationMs) {
        this.doubleTapZoomDuration = Math.max(0, durationMs);
    }

    /**
     * <p>
     * Provide an {@link Executor} to be used for loading images. By default, {@link AsyncTask#THREAD_POOL_EXECUTOR}
     * is used to minimise contention with other background work the app is doing. You can also choose
     * to use {@link AsyncTask#SERIAL_EXECUTOR} if you want to limit concurrent background tasks.
     * Alternatively you can supply an {@link Executor} of your own to avoid any contention. It is
     * strongly recommended to use a single executor instance for the life of your application, not
     * one per view instance.
     * </p><p>
     * supply an executor with more than one thread, you must make sure your implementation supports
     * multi-threaded bitmap decoding or has appropriate internal synchronization. From SDK 21, Android's
     * {@link android.graphics.BitmapRegionDecoder} uses an internal lock so it is thread safe but
     * there is no advantage to using multiple threads.
     * </p>
     *
     * @param executor an {@link Executor} for image loading.
     */
    public void setExecutor(@NonNull Executor executor) {
        //noinspection ConstantConditions
        if (executor == null) {
            throw new NullPointerException("Executor must not be null");
        }
        this.executor = executor;
    }

    /**
     * Enable or disable eager loading of tiles that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and animations
     * are completed.
     *
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading until gestures end
     */
    public void setEagerLoadingEnabled(boolean eagerLoadingEnabled) {
        this.eagerLoadingEnabled = eagerLoadingEnabled;
    }

    /**
     * Enables visual debugging, showing tile boundaries and sizes.
     *
     * @param debug true to enable debugging, false to disable.
     */
    public final void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Check if an image has been set. The image may not have been loaded and displayed yet.
     *
     * @return If an image is currently set.
     */
    public boolean hasImage() {
        return uri != null || bitmap != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    /**
     * Add a listener allowing notification of load and error events. Extend {@link DefaultOnImageEventListener}
     * to simplify implementation.
     *
     * @param onImageEventListener an {@link OnImageEventListener} instance.
     */
    public void setOnImageEventListener(OnImageEventListener onImageEventListener) {
        this.onImageEventListener = onImageEventListener;
    }

    /**
     * Add a listener for pan and zoom events. Extend {@link DefaultOnStateChangedListener} to simplify
     * implementation.
     *
     * @param onStateChangedListener an {@link OnStateChangedListener} instance.
     */
    public void setOnStateChangedListener(OnStateChangedListener onStateChangedListener) {
        this.onStateChangedListener = onStateChangedListener;
    }

    private void sendStateChanged(float oldScale, PointF oldVTranslate, int origin) {
        if (onStateChangedListener != null && scale != oldScale) {
            onStateChangedListener.onScaleChanged(scale, origin);
        }
        if (onStateChangedListener != null && !vTranslate.equals(oldVTranslate)) {
            onStateChangedListener.onCenterChanged(getCenter(), origin);
        }
    }

    /**
     * Creates a panning animation builder, that when started will animate the image to place the given coordinates of
     * the image in the center of the screen. If doing this would move the image beyond the edges of the screen, the
     * image is instead animated to move the center point as near to the center of the screen as is allowed - it's
     * guaranteed to be on screen.
     *
     * @param sCenter Target center point
     * @return {@link AnimationBuilder} instance. Call {@link AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateCenter(PointF sCenter) {
        if (!isReady()) {
            return null;
        }
        return new AnimationBuilder(sCenter);
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this would move the image
     * beyond the panning limits, the image is automatically panned during the animation.
     *
     * @param scale Target scale.
     * @return {@link AnimationBuilder} instance. Call {@link AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateScale(float scale) {
        if (!isReady()) {
            return null;
        }
        return new AnimationBuilder(scale);
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this would move the image
     * beyond the panning limits, the image is automatically panned during the animation.
     *
     * @param scale   Target scale.
     * @param sCenter Target source center.
     * @return {@link AnimationBuilder} instance. Call {@link AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateScaleAndCenter(float scale, PointF sCenter) {
        if (!isReady()) {
            return null;
        }
        return new AnimationBuilder(scale, sCenter);
    }

    /**
     * Builder class used to set additional options for a scale animation. Create an instance using {@link #animateScale(float)},
     * then set your options and call {@link #start()}.
     */
    public final class AnimationBuilder {

        private final float targetScale;
        private PointF targetSCenter;
        private final PointF vFocus;
        private long duration = 500;
        private int easing = EASE_IN_OUT_QUAD;
        private int origin = ORIGIN_ANIM;
        private boolean interruptible = true;
        private boolean panLimited = true;
        private OnAnimationEventListener listener;

        private AnimationBuilder(PointF sCenter) {
            this.targetScale = scale;
            this.targetSCenter = sCenter;
            this.vFocus = null;
        }

        private AnimationBuilder(float scale) {
            this.targetScale = scale;
            this.targetSCenter = getCenter();
            this.vFocus = null;
        }

        private AnimationBuilder(float scale, PointF sCenter) {
            this.targetScale = scale;
            this.targetSCenter = sCenter;
            this.vFocus = null;
        }

        private AnimationBuilder(float scale, PointF sCenter, PointF vFocus) {
            this.targetScale = scale;
            this.targetSCenter = sCenter;
            this.vFocus = vFocus;
        }

        /**
         * Desired duration of the anim in milliseconds. Default is 500.
         *
         * @param duration duration in milliseconds.
         * @return this builder for method chaining.
         */
        @NonNull
        public AnimationBuilder withDuration(long duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Whether the animation can be interrupted with a touch. Default is true.
         *
         * @param interruptible interruptible flag.
         * @return this builder for method chaining.
         */
        @NonNull
        public AnimationBuilder withInterruptible(boolean interruptible) {
            this.interruptible = interruptible;
            return this;
        }

        /**
         * Set the easing style. See static fields. {@link #EASE_IN_OUT_QUAD} is recommended, and the default.
         *
         * @param easing easing style.
         * @return this builder for method chaining.
         */
        @NonNull
        public AnimationBuilder withEasing(int easing) {
            if (!VALID_EASING_STYLES.contains(easing)) {
                throw new IllegalArgumentException("Unknown easing type: " + easing);
            }
            this.easing = easing;
            return this;
        }

        /**
         * Add an animation event listener.
         *
         * @param listener The listener.
         * @return this builder for method chaining.
         */
        @NonNull
        public AnimationBuilder withOnAnimationEventListener(OnAnimationEventListener listener) {
            this.listener = listener;
            return this;
        }

        public void setTargetSCenter(PointF sCenter) {
            this.targetSCenter = sCenter;
        }

        /**
         * Only for internal use. When set to true, the animation proceeds towards the actual end point - the nearest
         * point to the center allowed by pan limits. When false, animation is in the direction of the requested end
         * point and is stopped when the limit for each axis is reached. The latter behaviour is used for flings but
         * nothing else.
         */
        @NonNull
        private AnimationBuilder withPanLimited(boolean panLimited) {
            this.panLimited = panLimited;
            return this;
        }

        /**
         * Only for internal use. Indicates what caused the animation.
         */
        @NonNull
        private AnimationBuilder withOrigin(int origin) {
            this.origin = origin;
            return this;
        }

        /**
         * Starts the animation.
         */
        public void start() {
            if (anim != null && anim.listener != null) {
                try {
                    anim.listener.onInterruptedByNewAnim();
                } catch (Exception e) {
                    Log.w(TAG, "Error thrown by animation listener", e);
                }
            }

            int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) / 2;
            int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) / 2;
            float targetScale = limitedScale(this.targetScale);
            PointF targetSCenter = panLimited ? limitedSCenter(this.targetSCenter.x, this.targetSCenter.y, targetScale, new PointF()) : this.targetSCenter;
            if (anim == null) {
                anim = new Anim();
            }
            anim.scaleStart = scale;
            anim.scaleEnd = targetScale;
            anim.time = System.currentTimeMillis();
            anim.sCenterEndRequested = targetSCenter;
            anim.sCenterStart = getCenter();
            anim.sCenterEnd = targetSCenter;
            anim.vFocusStart = sourceToViewCoord(targetSCenter);
            if (pointF == null) {
                pointF = new PointF(
                        vxCenter,
                        vyCenter
                );
            } else {
                pointF.x = vxCenter;
                pointF.y = vyCenter;
            }
            anim.vFocusEnd = pointF;
            anim.duration = duration;
            anim.interruptible = interruptible;
            anim.easing = easing;
            anim.origin = origin;
            anim.time = System.currentTimeMillis();
            anim.listener = listener;

            if (vFocus != null) {
                // Calculate where translation will be at the end of the anim
                float vTranslateXEnd = vFocus.x - (targetScale * anim.sCenterStart.x);
                float vTranslateYEnd = vFocus.y - (targetScale * anim.sCenterStart.y);
                ScaleAndTranslate satEnd = new ScaleAndTranslate(targetScale, new PointF(vTranslateXEnd, vTranslateYEnd));
                // Fit the end translation into bounds
                fitToBounds(true, satEnd);
                // Adjust the position of the focus point at end so image will be in bounds
                anim.vFocusEnd = new PointF(
                        vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                        vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
                );
            }

            invalidate();
        }

    }

    /**
     * An event listener for animations, allows events to be triggered when an animation completes,
     * is aborted by another animation starting, or is aborted by a touch event. Note that none of
     * these events are triggered if the activity is paused, the image is swapped, or in other cases
     * where the view's internal state gets wiped or draw events stop.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnAnimationEventListener {

        /**
         * The animation has completed, having reached its endpoint.
         */
        void onComplete();

        /**
         * The animation has been aborted before reaching its endpoint because the user touched the screen.
         */
        void onInterruptedByUser();

        /**
         * The animation has been aborted before reaching its endpoint because a new animation has been started.
         */
        void onInterruptedByNewAnim();

    }

    /**
     * Default implementation of {@link OnAnimationEventListener} for extension. This does nothing in any method.
     */
    public static class DefaultOnAnimationEventListener implements OnAnimationEventListener {

        @Override
        public void onComplete() {
        }

        @Override
        public void onInterruptedByUser() {
        }

        @Override
        public void onInterruptedByNewAnim() {
        }

    }

    /**
     * An event listener, allowing subclasses and activities to be notified of significant events.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnImageEventListener {

        /**
         * Called when the dimensions of the image and view are known, and either a preview image,
         * the full size image, or base layer tiles are loaded. This indicates the scale and translate
         * are known and the next draw will display an image. This event can be used to hide a loading
         * graphic, or inform a subclass that it is safe to draw overlays.
         */
        void onReady();

        /**
         * Called when the full size image is ready. When using tiling, this means the lowest resolution
         * base layer of tiles are loaded, and when tiling is disabled, the image bitmap is loaded.
         * This event could be used as a trigger to enable gestures if you wanted interaction disabled
         * while only a preview is displayed, otherwise for most cases {@link #onReady()} is the best
         * event to listen to.
         */
        void onImageLoaded();

        /**
         * Called when a preview image could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. The view will continue to load the full size image.
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        void onPreviewLoadError(Exception e);

        /**
         * Indicates an error initiliasing the decoder when using a tiling, or when loading the full
         * size bitmap when tiling is disabled. This method cannot be relied upon; certain encoding
         * types of supported image formats can result in corrupt or blank images being loaded and
         * displayed with no detectable error.
         *
         * @param e The exception thrown. This error is also logged by the view.
         */
        void onImageLoadError(Exception e);

        /**
         * Called when an image tile could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. Most cases where an unsupported file is used will
         * result in an error caught by {@link #onImageLoadError(Exception)}.
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        void onTileLoadError(Exception e);

        /**
         * Called when a bitmap set using ImageSource.cachedBitmap is no longer being used by the View.
         * This is useful if you wish to manage the bitmap after the preview is shown
         */
        void onPreviewReleased();
    }

    /**
     * Default implementation of {@link OnImageEventListener} for extension. This does nothing in any method.
     */
    public static class DefaultOnImageEventListener implements OnImageEventListener {

        @Override
        public void onReady() {
        }

        @Override
        public void onImageLoaded() {
        }

        @Override
        public void onPreviewLoadError(Exception e) {
        }

        @Override
        public void onImageLoadError(Exception e) {
        }

        @Override
        public void onTileLoadError(Exception e) {
        }

        @Override
        public void onPreviewReleased() {
        }

    }

    /**
     * An event listener, allowing activities to be notified of pan and zoom events. Initialisation
     * and calls made by your code do not trigger events; touch events and animations do. Methods in
     * this listener will be called on the UI thread and may be called very frequently - your
     * implementation should return quickly.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnStateChangedListener {

        /**
         * The scale has changed. Use with {@link #getMaxScale()} and {@link #getMinScale()} to determine
         * whether the image is fully zoomed in or out.
         *
         * @param newScale The new scale.
         * @param origin   Where the event originated from - one of {@link #ORIGIN_ANIM}, {@link #ORIGIN_TOUCH}.
         */
        void onScaleChanged(float newScale, int origin);

        /**
         * The source center has been changed. This can be a result of panning or zooming.
         *
         * @param newCenter The new source center point.
         * @param origin    Where the event originated from - one of {@link #ORIGIN_ANIM}, {@link #ORIGIN_TOUCH}.
         */
        void onCenterChanged(PointF newCenter, int origin);

    }

    /**
     * Default implementation of {@link OnStateChangedListener}. This does nothing in any method.
     */
    public static class DefaultOnStateChangedListener implements OnStateChangedListener {

        @Override
        public void onCenterChanged(PointF newCenter, int origin) {
        }

        @Override
        public void onScaleChanged(float newScale, int origin) {
        }

    }

}
