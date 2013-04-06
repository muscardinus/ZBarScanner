package com.dm.zbar.android.scanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.dm.zbar.android.scanner.ScannerHelper.ScannerResultListener;

public class ZBarScannerActivity extends Activity implements ZBarConstants, ScannerResultListener {

	private static final String TAG = "ZBarScanner/ZBarScannerActivity";
	private CameraPreview mPreview;
	private CameraWrapper mCamera;
	private ScannerHelper mScanner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!CameraWrapper.isRearCameraAvailable(this)) {
			// Cancel request if there is no rear-facing camera.
			cancelRequest();
			return;
		}

		// Create and configure the ImageScanner;
		mScanner = new ScannerHelper(getIntent().getIntArrayExtra(SCAN_MODES), this);

		// Create camera. Use the first/default i.e. the first rear facing
		// camera.
		mCamera = new CameraWrapper(0);

		// Create a RelativeLayout container that will hold a SurfaceView,
		// and set it as the content of our activity.
		mPreview = new CameraPreview(this);
		setContentView(mPreview);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCamera.open(this);
		mPreview.setCamera(mCamera);
		mCamera.setPreviewCallback(mScanner.getCameraPreviewCallback());
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		mPreview.setCamera(null);
		mCamera.setPreviewCallback(null);
		mCamera.release();
	}

	public void cancelRequest() {
		setResult(Activity.RESULT_CANCELED);
		finish();
	}

	@Override
	public void onResult(String symData, int symType) {

		mPreview.setCamera(null);
		mCamera.setPreviewCallback(null);

		if (!TextUtils.isEmpty(symData)) {
			Intent dataIntent = new Intent();
			dataIntent.putExtra(SCAN_RESULT, symData);
			dataIntent.putExtra(SCAN_RESULT_TYPE, symType);
			setResult(Activity.RESULT_OK, dataIntent);
			finish();
		}
	}
}
