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
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightnessService extends Service implements
		SensorEventListener {
	private final int MAX_BRIGHTNESS = 255;
	private final int MIN_BRIGHTNESS = 0;
	private final int MAX_LUX = 1000;
	private final int MAX_RELATIVE = 100;
	private final int MIN_RELATIVE = 0;
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
			Sensor lightSensor = mSensorManager
					.getDefaultSensor(Sensor.TYPE_LIGHT);
			mSensorManager.registerListener(this, lightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
			Settings.System.putInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE,
					Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(mTag, "starting service");
		init(intent.getExtras().getInt("relativeLevel"));
		return super.onStartCommand(intent, flags, startId);
	}

	public void setRelativeLevel(int level) {
		mRelativeLevel = level;
		Log.d(mTag, "new level: " + level);
		updateTargetBrightness();
		if (mRelativeLevel == MIN_RELATIVE) {
			Toast.makeText(getApplicationContext(), "Min Brightness Set",
					Toast.LENGTH_SHORT).show();

			// Vibrate
			((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(150);
		} else if (mRelativeLevel == MAX_RELATIVE) {
			Toast.makeText(getApplicationContext(), "Max Brightness Set",
					Toast.LENGTH_SHORT).show();

			// Vibrate
			((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(150);
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
			stopSelf();
		}

		// Compute a new target brightness level
		int newTarget;
		if (mRelativeLevel == MIN_RELATIVE) {
			newTarget = MIN_BRIGHTNESS;
		} else if (mRelativeLevel == MAX_RELATIVE) {
			newTarget = MAX_BRIGHTNESS;
		} else {
			newTarget = (int) ((mCurrentLux * MAX_BRIGHTNESS / MAX_LUX) + (mRelativeLevel
					* MAX_BRIGHTNESS / MAX_RELATIVE / 3));
		}

		setTargetBrightness(newTarget);
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
			updateTargetBrightness();
			Log.d(mTag, "lux level: " + mCurrentLux);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(mTag, "binding service");
		init(intent.getExtras().getInt("relativeLevel"));
		return new BrightnessBinder();
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
