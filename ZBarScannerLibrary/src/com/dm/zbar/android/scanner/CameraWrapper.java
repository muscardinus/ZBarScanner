package com.dm.zbar.android.scanner;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class CameraWrapper {

	private final String LOG_TAG = "ZBarScanner/CameraPreview";

	private Context appContext;

	int id;
	private Camera camera;

	private List<Size> supportedPreviewSizes;
	Size previewSize;

	int cameraDisplayOrientation;
	private int lastRotation = -1;

	private OrientationEventListener orientationEventListener;

	// Needed for preview size optimization
	private boolean optimizing;

	int targetWidth;
	int targetHeight;

	private boolean previewStarted;
	private boolean autofocusRunning;

	public CameraWrapper(int id) {
		this.id = id;
	}

	public boolean open(Context appContext) {
		this.appContext = appContext;
		try {
			// Attempt to get a Camera instance
			camera = Camera.open(id);
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
			Log.e(LOG_TAG, "Failed to open camera");
			return false;
		}

		fixCameraDisplayOrientation();

		if (targetWidth > 0 && targetHeight > 0) {
			updatePreviewSize();
		}

		// Register an OrientationEventListener in order to handle
		// orientation changes from 90 to 270 degrees and visa-versa.
		// This is necessary, because in this case, a configuration change
		// does not happen and so the activity does not restart (which would
		// normally rotate the camera view).

		if (orientationEventListener == null) {
			orientationEventListener = new OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_NORMAL) {

				@Override
				public void onOrientationChanged(int _) {
					synchronized (CameraWrapper.this) {
						fixCameraDisplayOrientation();
					}
				}
			};
		}

		if (orientationEventListener.canDetectOrientation()) {
			orientationEventListener.enable();
		}

		return true;
	}

	void setTargetPreviewSize(int width, int height) throws IllegalStateException {
		targetWidth = width;
		targetHeight = height;

		if (camera != null) {
			updatePreviewSize();
		}
	}

	private void updatePreviewSize() {
		supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
		optimizePreviewSize();

		if (previewSize != null && !supportedPreviewSizes.contains(previewSize)) {
			// The set preview size is no longer available. We'll have to get a
			// new one.
			previewSize = null;
		}
	}

	public synchronized void release() {
		appContext = null;

		if (orientationEventListener != null) {
			orientationEventListener.disable();
		}

		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		if (camera != null) {
			camera.release();
			camera = null;
		}
		lastRotation = -1;
	}

	synchronized void startPreview() {
		if (!previewStarted) {
			fixCameraDisplayOrientation();
			if (camera != null) {
				previewStarted = true;
				autofocusRunning = true;

				try {
					camera.startPreview();
				} catch (RuntimeException e) {
					Log.e(LOG_TAG, "Failed to start camera preview", e);
				}
			}
		}
	}

	synchronized void stopPreview() {
		if (camera != null) {
			camera.stopPreview();
		}
		previewStarted = false;
	}

	synchronized void setPreviewDisplay(SurfaceHolder holder) throws IOException {
		if (camera != null) {
			camera.setPreviewDisplay(holder);
		}
	}

	synchronized void autoFocus(AutoFocusCallback cb) {
		if (camera != null && autofocusRunning) {
			camera.autoFocus(cb);
		}
	}

	synchronized void cancelAutoFocus() {
		if (camera != null) {
			autofocusRunning = false;
			camera.cancelAutoFocus();
		}
	}

	public synchronized void setPreviewCallback(PreviewCallback cb) {
		if (camera != null) {
			camera.setPreviewCallback(cb);
		}
	}

	boolean fixCameraDisplayOrientation() {
		synchronized (this) {
			if (camera == null) {
				return false;
			}
		}

		WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
		int rotation = wm.getDefaultDisplay().getRotation();
		synchronized (this) {
			if (lastRotation != -1 && rotation == lastRotation) {
				return false;
			}
		}

		int degrees;
		switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
			default:
				degrees = 0;
				break;
		}

		CameraInfo info = new CameraInfo();
		Camera.getCameraInfo(id, info);

		int result;
		if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
			// Front-facing
			cameraDisplayOrientation = result = (info.orientation + degrees) % 360;
			// Compensate the mirror image
			result = (360 - result) % 360;
		} else {
			// Back-facing
			cameraDisplayOrientation = result = (info.orientation - degrees + 360) % 360;
		}

		synchronized (this) {
			if (camera != null) {
				if (!previewStarted) {
					camera.setDisplayOrientation(result);
				} else {
					camera.stopPreview();
					camera.setDisplayOrientation(result);
					camera.startPreview();
				}
				lastRotation = rotation;
			}
		}

		return true;
	}

	private boolean optimizePreviewSize() {
		synchronized (this) {
			if (camera == null) {
				return false;
			}
		}

		synchronized (this) {
			if (optimizing) {
				return false;
			}
			optimizing = true;
		}

		if (supportedPreviewSizes == null) {
			return false;
		}

		// TODO If view is larger? Should we use a larger preview?

		boolean portrait = cameraDisplayOrientation == 90 || cameraDisplayOrientation == 270;

		Size optimalSize = null;
		if (portrait) {
			// Portrait

			double minDiff = Double.MAX_VALUE;
			// Try finding the closest preview size that is also larger than the
			// target size (Since we'd rather scale down than scale up).
			for (Size size : supportedPreviewSizes) {
				// We have to swap with and height, because we will be rotation
				// the preview 90 degrees.
				if (size.height < targetWidth || size.width < targetHeight) {
					continue;
				}

				// We compare only "width", since "height" is much larger anyway
				int diff = (size.height - targetWidth);

				if (diff < minDiff) {
					optimalSize = size;
					minDiff = diff;
				}
			}

			if (optimalSize == null) {
				// No such luck, so find the closest preview size, even if
				// smaller.
				for (Size size : supportedPreviewSizes) {
					// We compare only "width", since "height" is much larger
					// anyway
					int diff = Math.abs(size.height - targetWidth);

					if (diff < minDiff) {
						optimalSize = size;
						minDiff = diff;
					}
				}
			}
		} else {
			// Landscape

			Size targetSize = camera.new Size(targetWidth, targetHeight);
			if (supportedPreviewSizes.contains(targetSize)) {
				optimalSize = targetSize;
			} else {
				double minDiff = Double.MAX_VALUE;
				// Try finding the closest preview size that is also larger than
				// the target size (Since we'd rather scale down than scale up).
				for (Size size : supportedPreviewSizes) {
					if (size.width < targetWidth || size.height < targetHeight) {
						continue;
					}

					// We don't need Math.abs(), since we know size is larger
					// than target size :)
					int diff = (size.height - targetHeight) + (size.width - targetWidth);

					if (diff < minDiff) {
						optimalSize = size;
						minDiff = diff;
					}
				}

				if (optimalSize == null) {
					// No such luck, so find the closest preview size, even if
					// smaller.
					for (Size size : supportedPreviewSizes) {
						int diff = Math.abs(size.height - targetHeight) + Math.abs(size.width - targetWidth);

						if (diff < minDiff) {
							optimalSize = size;
							minDiff = diff;
						}
					}
				}
			}
		}

		previewSize = optimalSize;

		synchronized (this) {
			if (camera != null) {
				Camera.Parameters parameters = camera.getParameters();
				parameters.setPreviewSize(previewSize.width, previewSize.height);
				camera.setParameters(parameters);
			}
		}

		optimizing = false;

		return true;
	}

	public static boolean isAnyCameraAvailable(Context context) {
		PackageManager pm = context.getPackageManager();
		return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
				|| pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
	}

	public static boolean isRearCameraAvailable(Context context) {
		PackageManager pm = context.getPackageManager();
		return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}
}