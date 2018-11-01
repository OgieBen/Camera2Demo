package com.ravenshell.camera2demo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class PreviewTextureView extends TextureView {

    private int ratioWidth;
    private int ratioHeight;


    /*
    A TextureView can be used to display a content stream. Such a content stream
    can for instance be a video or an OpenGL scene. The content stream can come
    from the application's process as well as a remote process.

    TextureView can only be used in a hardware accelerated window.
    When rendered in software, TextureView will draw nothing.
    Unlike SurfaceView, TextureView does not create a separate window
    but behaves as a regular View. This key difference allows a TextureView to
    be moved, transformed, animated, etc. For instance, you can make a TextureView
    semi-translucent by calling myView.setAlpha(0.5f).

    Using a TextureView is simple: all you need to do is get its SurfaceTexture.
    The SurfaceTexture can then be used to render content.


    A TextureView's SurfaceTexture can be obtained either by invoking getSurfaceTexture()
    or by using a TextureView.SurfaceTextureListener. It is important to know
    that a SurfaceTexture is available only after the TextureView is attached to a window
    (and onAttachedToWindow() has been invoked.) It is therefore highly recommended you use
    a listener to be notified when the SurfaceTexture becomes available.

    It is important to note that only one producer can use the TextureView.
    For instance, if you use a TextureView to display the camera preview,
    you cannot use lockCanvas() to draw onto the TextureView at the same time.


*/
    public PreviewTextureView(Context context) {
        super(context);
    }

    public PreviewTextureView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public PreviewTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 ==ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
            }
        }
    }

}
