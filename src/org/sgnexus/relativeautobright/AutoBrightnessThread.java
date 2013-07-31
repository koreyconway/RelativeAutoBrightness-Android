package org.sgnexus.relativeautobright;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightnessThread extends TriggerEventListener implements Runnable {
	private final int MAX_BRIGHTNESS = 255;
	private final int MIN_BRIGHTNESS = 0;
	private final int MAX_LUX = 1000;
	private final int MAX_RELATIVE = 100;
	private final int MIN_RELATIVE = 0;
	private String mTag = this.getClass().getSimpleName();
	private SensorManager mSensorManager;
	private Sensor mLightSensor;
	private int mRelativeLevel = -1;
	private int mCurrentBrightness;
	private float mCurrentLux;
	private int mTargetBrightness;
	private boolean mRunning = false;
	private boolean mIsSensingLight = false;
	private AutoBrightnessService mService;
	private Handler mHandler;
	private Thread mThread;

	AutoBrightnessThread(AutoBrightnessService service, int relativeLevel) {
		mService = service;
		mRelativeLevel = Math.min(Math.max(relativeLevel, MIN_RELATIVE),
				MAX_RELATIVE);
	}

	@Override
	public void run() {
		init();
		Looper.loop();
		stopSensingLight();
		return;
	}

	private void init() {
		if (!mRunning) {
			mRunning = true;
			mThread = Thread.currentThread();

			Looper.prepare();
			mHandler = new Handler(new Handler.Callback() {
				@Override
				public boolean handleMessage(Message msg) {
					Log.d(mTag, "handling message");
					return false;
				}
			});

			// Get the current system brightness
			try {
				mCurrentBrightness = Settings.System.getInt(
						mService.getContentResolver(),
						Settings.System.SCREEN_BRIGHTNESS);
			} catch (SettingNotFoundException e) {
				mCurrentBrightness = 0;
			}

			// Setup sensor
			mSensorManager = (SensorManager) mService
					.getSystemService(Context.SENSOR_SERVICE);
			mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			startSensingLight();

			// Set to manual mode
			Settings.System.putInt(mService.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE,
					Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		}
	}

	public Handler getHandler() {
		return mHandler;
	}

	private void startSensingLight() {
		//if (!mIsSensingLight) {
			mSensorManager.requestTriggerSensor(this, mLightSensor);
			//mSensorManager.registerListener(this, mLightSensor,
			//		SensorManager.SENSOR_DELAY_NORMAL, mHandler);
			//mIsSensingLight = true;
		//}
	}

	private void stopSensingLight() {
		//if (mIsSensingLight) {
			//mSensorManager.unregisterListener(this);
			//mIsSensingLight = false;
		//}
		mSensorManager.cancelTriggerSensor(this, mLightSensor);
	}

	public void setRelativeLevel(int level) {
		mRelativeLevel = level;
		Log.d(mTag, "new level: " + level);
		updateTargetBrightness();
		if (mRelativeLevel == MIN_RELATIVE) {
			mService.toast("Min Brightness Set");
			// Vibrate
			((Vibrator) mService.getSystemService(Context.VIBRATOR_SERVICE))
					.vibrate(150);
		} else if (mRelativeLevel == MAX_RELATIVE) {
			mService.toast("Max Brightness Set");
			// Vibrate
			((Vibrator) mService.getSystemService(Context.VIBRATOR_SERVICE))
					.vibrate(150);
		}
	}

	private void setTargetBrightness(int targetBrightness) {
		mTargetBrightness = Math.min(MAX_BRIGHTNESS,
				Math.max(MIN_BRIGHTNESS, targetBrightness));
		Log.d(mTag, "new target: " + mTargetBrightness);
		updateScreenBrightness();
	}

	private void updateTargetBrightness() {
		// Stop the service since user is using system auto brightness
		if (isUsingSystemAutoBrightness()) {
			mService.stopSelf();
		}

		// Compute a new target brightness level
		int newTarget;
		if (mRelativeLevel == MIN_RELATIVE) {
			newTarget = MIN_BRIGHTNESS;
			stopSensingLight();
		} else if (mRelativeLevel == MAX_RELATIVE) {
			newTarget = MAX_BRIGHTNESS;
			stopSensingLight();
		} else {
			newTarget = (int) ((mCurrentLux * MAX_BRIGHTNESS / MAX_LUX) + (mRelativeLevel
					* MAX_BRIGHTNESS / MAX_RELATIVE / 3));
			startSensingLight();
		}

		setTargetBrightness(newTarget);
	}

	private void updateScreenBrightness() {
		mCurrentBrightness = mTargetBrightness;
		Log.d(mTag, "new current: " + mCurrentBrightness);
		Settings.System.putInt(mService.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, mCurrentBrightness);
	}

	private boolean isUsingSystemAutoBrightness() {
		try {
			int mode = Settings.System.getInt(mService.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE);

			if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
				return true;
			}

			return false;
		} catch (SettingNotFoundException e) {
			return false;
		}
	}

//	@Override
//	public void onAccuracyChanged(Sensor sensor, int accuracy) {
//		// Don't care about accuracy
//		return;
//	}

//	@Override
//	public void onSensorChanged(SensorEvent event) {
//		stopSensingLight();
//
//		Log.d(mTag, "thread name: " + Thread.currentThread().getName());
//		// Verify light sensor
//		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
//			// Get lux level
//			mCurrentLux = event.values[0];
//			updateTargetBrightness();
//			Log.d(mTag, "lux level: " + mCurrentLux);
//		}
//
//		try {
//			Log.d(mTag, "sleeping");
//			Thread.sleep(3000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		startSensingLight();
//	}

	@SuppressLint("NewApi")
	synchronized public void finish() {
		if (Build.VERSION.SDK_INT >= 18) {
			mHandler.getLooper().quitSafely();
		} else {
			mHandler.getLooper().quit();
		}
		mRunning = false;
		mThread.interrupt();
	}

	@Override
	public void onTrigger(TriggerEvent event) {
		Log.d(mTag, "thread name: " + Thread.currentThread().getName());
		// Verify light sensor
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Get lux level
			mCurrentLux = event.values[0];
			updateTargetBrightness();
			Log.d(mTag, "lux level: " + mCurrentLux);
		}

//		try {
//			Log.d(mTag, "sleeping");
//			Thread.sleep(500);
//		} catch (InterruptedException e) {
//			return;
//		}
		
		startSensingLight();
	}
	

}
