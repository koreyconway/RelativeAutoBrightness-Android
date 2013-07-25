package org.sgnexus.relativeautobright;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

public class AutoBrightnessService extends Service implements
		SensorEventListener {
	private final int MAX_BRIGHTNESS = 255;
	private final int MIN_BRIGHTNESS = 0;
	private String mTag = this.getClass().getSimpleName();
	private SensorManager mSensorManager;
	private int mRelativeLevel = -1;
	private int mCurrentBrightness;
	private float mCurrentLux;
	private int mTargetBrightness;
	private boolean mRunning = false;

	@Override
	public void onCreate() {
		Log.d(mTag, "creating service");
		super.onCreate();

		// Get the current system brightness
		try {
			mCurrentBrightness = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS);
		} catch (SettingNotFoundException e) {
			mCurrentBrightness = 0;
		}

		// Setup the light sensor
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	}
	
	private void init(int initialRelativeLevel) {
		if (!mRunning) {
			mRunning = true;
			mRelativeLevel = initialRelativeLevel;
			Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			mSensorManager.registerListener(this, lightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(mTag, "starting service");
		init(intent.getExtras().getInt("relativeLevel"));
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(mTag, "binding service");
		init(intent.getExtras().getInt("relativeLevel"));
		return new BrightnessBinder();
	}

	public void setRelativeLevel(int level) {
		mRelativeLevel = level;
		Log.d(mTag, "new level: " + level);
		computeTargetBrightness();
	}

	private void setTargetBrightness(int brightness) {
		// Stop the service since user is using system auto brightness
		if (isUsingSystemAutoBrightness()) {
			stopSelf();
		}

		mTargetBrightness = Math.max(Math.min(brightness, MAX_BRIGHTNESS),
				MIN_BRIGHTNESS);
		Log.d(mTag, "new target: " + mTargetBrightness);
		updateScreenBrightness();
	}

	private void updateScreenBrightness() {
		mCurrentBrightness = mTargetBrightness;
		Log.d(mTag, "new current: " + mCurrentBrightness);
		Settings.System.putInt(getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, mCurrentBrightness);
	}

	private boolean isUsingSystemAutoBrightness() {
		try {
			int mode = Settings.System.getInt(getContentResolver(),
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
		// Verify light sensor
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Get lux level
			mCurrentLux = event.values[0];
			computeTargetBrightness();
			Log.d(mTag, "lux level: " + mCurrentLux);
		}
	}

	private void computeTargetBrightness() {
		// Compute a new target brightness level
		int newBrightness = (int) Math.log(mCurrentLux) * 5 + mRelativeLevel;
		setTargetBrightness(newBrightness);
	}

	@Override
	public void onDestroy() {
		Log.d(mTag, "killing service");
		mSensorManager.unregisterListener(this);
		super.onDestroy();
	}

	class BrightnessBinder extends Binder {
		public AutoBrightnessService getService() {
			return AutoBrightnessService.this;
		}
	}
}
