package org.sgnexus.relativeautobright;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightnessThread extends Thread implements SensorEventListener {
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

	AutoBrightnessThread(AutoBrightnessService service, int relativeLevel) {
		mService = service;
		mRelativeLevel = Math.min(Math.max(relativeLevel, MIN_RELATIVE),
				MAX_RELATIVE);
	}

	@Override
	public void run() {
		init();

		while (mRunning) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// Ignore
			}
		}

		// clean up
		stopSensingLight();
		return;
	}

	private void init() {
		if (!mRunning) {
			mRunning = true;

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
	
	private void startSensingLight() {
		if (!mIsSensingLight) {
			mSensorManager.registerListener(this, mLightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
			mIsSensingLight = true;
		}
	}

	private void stopSensingLight() {
		if (mIsSensingLight) {
			mSensorManager.unregisterListener(this);
			mIsSensingLight = false;
		}
	}

	public void setRelativeLevel(int level) {
		mRelativeLevel = level;
		Log.d(mTag, "new level: " + level);
		updateTargetBrightness();
		if (mRelativeLevel == MIN_RELATIVE) {
			Toast.makeText(mService.getApplicationContext(),
					"Min Brightness Set", Toast.LENGTH_SHORT).show();

			// Vibrate
			((Vibrator) mService.getSystemService(Context.VIBRATOR_SERVICE))
					.vibrate(150);
		} else if (mRelativeLevel == MAX_RELATIVE) {
			Toast.makeText(mService.getApplicationContext(),
					"Max Brightness Set", Toast.LENGTH_SHORT).show();

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

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Don't care about accuracy
		return;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		Log.d(mTag, "thread name: " + Thread.currentThread().getName());
		// Verify light sensor
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Get lux level
			mCurrentLux = event.values[0];
			updateTargetBrightness();
			Log.d(mTag, "lux level: " + mCurrentLux);
		}
	}

	synchronized public void finish() {
		mRunning = false;
		this.interrupt();
	}
}
