package com.dm.zbar.android.scanner;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

public class CameraWrapper {

	int id;
	private Camera camera;

	private List<Size> supportedPreviewSizes;
	Size previewSize;

	int cameraDisplayOrientation;
	private int lastRotation = -1;

	// Needed for preview size optimization
	private boolean optimizing;

	int targetWidth;
	int targetHeight;

	public CameraWrapper(int id) {
		this.id = id;
	}

	public boolean open(Activity activity) {
		try {
			// Attempt to get a Camera instance
			camera = Camera.open(id);
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
			return false;
		}

		fixCameraDisplayOrientation(activity);

		if (targetWidth > 0 && targetHeight > 0) {
			updatePreviewSize();
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
		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		if (camera != null) {
			camera.release();
			camera = null;
		}
		lastRotation = -1;
	}

	synchronized void startPreview() {
		if (camera != null) {
			camera.startPreview();
		}
	}

	synchronized void stopPreview() {
		if (camera != null) {
			camera.stopPreview();
		}
	}

	synchronized void setPreviewDisplay(SurfaceHolder holder) throws IOException {
		if (camera != null) {
			camera.setPreviewDisplay(holder);
		}
	}

	synchronized void autoFocus(AutoFocusCallback cb) {
		if (camera != null) {
			camera.autoFocus(cb);
		}
	}

	synchronized void cancelAutoFocus() {
		if (camera != null) {
			camera.cancelAutoFocus();
		}
	}

	public synchronized void setPreviewCallback(PreviewCallback cb) {
		if (camera != null) {
			camera.setPreviewCallback(cb);
		}
	}

	boolean fixCameraDisplayOrientation(Activity activity) {
		synchronized (this) {
			if (camera == null) {
				return false;
			}
		}

		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		synchronized (this) {
			if (lastRotation != -1 && rotation == lastRotation) {
				return false;
			}
			lastRotation = rotation;
		}

		int degrees;
		switch (lastRotation) {
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
				camera.setDisplayOrientation(result);
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

	public static boolean isRearCameraAvailable(Context context) {
		PackageManager pm = context.getPackageManager();
		return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}
}