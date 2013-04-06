package com.dm.zbar.android.scanner;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class CameraPreview extends ViewGroup implements SurfaceHolder.Callback {

	private final String TAG = "ZBarScanner/CameraPreview";

	SurfaceView mSurfaceView;
	SurfaceHolder mHolder;
	private Handler mAutoFocusHandler;

	private CameraWrapper mCamera;

	private boolean surfaceCreated;

	@SuppressWarnings("deprecation")
	public CameraPreview(Context context) {
		super(context);
		
		mAutoFocusHandler = new Handler();

		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);

		setBackgroundColor(Color.BLACK);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			// Deprecated setting, but required on Android versions prior to
			// 3.0
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		// Register a OrientationEventListener in order to handle
		// orientation changes from 90 to 270 degrees and visa-versa.
		// This is necessary, because in this case, a configuration change
		// does not happen and so the activity does not restart (which would
		// normally rotate the camera view).

		OrientationEventListener orientationEventListener = new OrientationEventListener(context,
				SensorManager.SENSOR_DELAY_NORMAL) {

			@Override
			public void onOrientationChanged(int _) {
				if (mCamera != null && mCamera.fixCameraDisplayOrientation((Activity) getContext())) {
					requestLayout();
				}
			}
		};

		if (orientationEventListener.canDetectOrientation()) {
			orientationEventListener.enable();
		}
	}

	public void setCamera(CameraWrapper camera) {
		final CameraWrapper oldCamera = mCamera;

		if (oldCamera != null) {
			oldCamera.stopPreview();
			try {
				oldCamera.setPreviewDisplay(null);
			} catch (IOException e) {
				Log.e(TAG, "IOException caused by setPreviewDisplay(null)", e);
			}
			oldCamera.cancelAutoFocus();
		}

		if (camera != null) {
			mCamera = camera;

			if (surfaceCreated) {

				try {
					mCamera.setPreviewDisplay(mHolder);
				} catch (IOException e) {
					Log.e(TAG, "IOException caused by setPreviewDisplay()", e);
					return;
				}

				mCamera.startPreview();
				mCamera.autoFocus(mAutoFocusCallback);

				post(new Runnable() {

					@Override
					public void run() {
						requestLayout();
					}
				});
			}

		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// We purposely disregard child measurements because act as a
		// wrapper to a SurfaceView that centers the camera preview instead
		// of stretching it.
		final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);

		mCamera.setTargetPreviewSize(width * 2, height * 2);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (changed && getChildCount() > 0) {
			final View child = getChildAt(0);

			final int width = r - l;
			final int height = b - t;

			int previewWidth = width;
			int previewHeight = height;
			if (mCamera != null && mCamera.previewSize != null) {
				if (mCamera.cameraDisplayOrientation == 90 || mCamera.cameraDisplayOrientation == 270) {
					// Switch height and width so the view is in portrait.
					previewWidth = mCamera.previewSize.height;
					previewHeight = mCamera.previewSize.width;
				} else {
					previewWidth = mCamera.previewSize.width;
					previewHeight = mCamera.previewSize.height;
				}
			}

			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				// Preview is taller than the view
				final int scaledChildWidth = previewWidth * height / previewHeight;
				child.layout((width - scaledChildWidth) / 2, 0,
						(width + scaledChildWidth) / 2, height);
			} else {
				// Preview is wider than the view (or equal ratio)
				final int scaledChildHeight = previewHeight * width / previewWidth;
				child.layout(0, (height - scaledChildHeight) / 2,
						width, (height + scaledChildHeight) / 2);
			}
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		surfaceCreated = true;

		// The Surface has been created, now tell the camera where to draw the
		// preview.
		try {
			if (mCamera != null) {
				mCamera.setPreviewDisplay(holder);
			}
		} catch (IOException e) {
			Log.d(TAG, "IOException caused by setPreviewDisplay()", e);
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceCreated = false;

		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null) {
			mCamera.cancelAutoFocus();
			mCamera.stopPreview();
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (holder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		if (mCamera == null) {
			return;
		}

		requestLayout();

		mCamera.startPreview();
		mCamera.autoFocus(mAutoFocusCallback);
	}

	private Runnable doAutoFocus = new Runnable() {
		public void run() {
			if (mCamera != null) {
				mCamera.autoFocus(mAutoFocusCallback);
			}
		}
	};

	// Mimic continuous auto-focusing
	Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
		public void onAutoFocus(boolean success, Camera camera) {
			mAutoFocusHandler.postDelayed(doAutoFocus, 1000);
		}
	};
}
