package com.dm.zbar.android.scanner;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.Log;

public class ScannerHelper implements Camera.PreviewCallback {

	static {
		System.loadLibrary("iconv");
	}

	private static final String LOG_TAG = "ZBarScanner/ScannerHelper";

	public interface ScannerResultListener {
		public void onResult(String symData, int symType);
	}

	ScannerResultListener mResultListener;

	private ImageScanner mScanner;

	public ScannerHelper(int[] scanModes, ScannerResultListener resultListener) {
		mResultListener = resultListener;

		mScanner = new ImageScanner();
		mScanner.setConfig(0, Config.X_DENSITY, 3);
		mScanner.setConfig(0, Config.Y_DENSITY, 3);

		int[] symbols = scanModes;
		if (symbols != null) {
			mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
			for (int symbol : symbols) {
				mScanner.setConfig(symbol, Config.ENABLE, 1);
			}
		}
	}

	public Camera.PreviewCallback getCameraPreviewCallback() {
		return this;
	}

	public synchronized void onPreviewFrame(byte[] data, Camera camera) {
		Camera.Parameters parameters;
		try {
			parameters = camera.getParameters();
		} catch (RuntimeException e) {
			Log.e(LOG_TAG, "Unable to get camera parameters", e);
			return;
		}

		Camera.Size size = parameters.getPreviewSize();

		Image barcode = new Image(size.width, size.height, "Y800");
		barcode.setData(data);

		int result = mScanner.scanImage(barcode);

		if (result != 0) {
			SymbolSet syms = mScanner.getResults();
			for (Symbol sym : syms) {
				String symData = sym.getData();
				if (!TextUtils.isEmpty(symData)) {
					mResultListener.onResult(symData, sym.getType());
					break;
				}
			}
		}
	}
}
