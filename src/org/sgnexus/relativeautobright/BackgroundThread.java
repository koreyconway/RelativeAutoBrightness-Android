package org.sgnexus.relativeautobright;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

public class BackgroundThread extends Thread implements SensorEventListener {
	private final String mTag = this.getClass().getSimpleName();

	static final int ACTION_SENSOR_ON = -1;
	static final int ACTION_SCREEN_ON = -2;
	static final int ACTION_SCREEN_OFF = -3;
	static final int ACTION_DECREASE_LEVEL = -4;
	static final int ACTION_INCREASE_LEVEL = -5;

	static private final int MAX_BRIGHTNESS = 255, MIN_BRIGHTNESS = 0;
	static private final int MIN_RELATIVE = 0, MAX_RELATIVE = 100;
	static private final int MAX_LUX = 300;
	static private final int DEFAULT_SENSE_INTERVAL = 1000;
	static private final int INCREASE_INTERVAL = 10;

	private SensorManager mSensorManager;
	private Sensor mLightSensor;
	private int mRelativeLevel;
	private int mCurrentBrightness;
	private float mCurrentLux;
	private int mTargetBrightness;
	private boolean mIsSensingLight = false;
	private MainService mService;
	private Handler mHandler;
	private long mLastSenseMs = 0;
	private int mSenseIntervalMs = DEFAULT_SENSE_INTERVAL;
	private Toast mToast;
	private boolean mScreenIsOn = true;
	private boolean mLevelIsAbsolute = false;
	private SharedPreferences mPrefs;

	BackgroundThread(MainService service, int initialRelativeLevel) {
		mService = service;
		mRelativeLevel = Math.min(Math.max(initialRelativeLevel, MIN_RELATIVE),
				MAX_RELATIVE);
	}

	@Override
	public void run() {
		// Get the current system brightness
		try {
			mCurrentBrightness = Settings.System.getInt(
					mService.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS);
		} catch (SettingNotFoundException e) {
			mCurrentBrightness = 0;
		}

		// Set to manual brightness mode
		Settings.System.putInt(mService.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS_MODE,
				Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		// Setup light sensor
		mSensorManager = (SensorManager) mService
				.getSystemService(Context.SENSOR_SERVICE);
		mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		startSensingLight();

		// Load the app preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mService);

		// Setup looper and handler
		Looper.prepare();
		mHandler = new Handler(new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				int actionId = msg.what;

				if (actionId == ACTION_SCREEN_ON) {
					Log.d(mTag, "starting light sensor");
					mScreenIsOn = true;
					mSenseIntervalMs = DEFAULT_SENSE_INTERVAL;
					startSensingLight();
				} else if (actionId == ACTION_SCREEN_OFF) {
					Log.d(mTag, "stopping light sensor");
					mScreenIsOn = false;
					stopSensingLight();
				} else if (actionId == ACTION_SENSOR_ON) {
					if (mScreenIsOn && !mLevelIsAbsolute) {
						// Check screen is on to avoid starting sensing that was
						// previously scheduled before screen went off
						Log.d(mTag, "starting light sensor");
						startSensingLight();
					}
				} else if (actionId == ACTION_DECREASE_LEVEL) {
					// Set and save new relative level
					int newLevel = mRelativeLevel - INCREASE_INTERVAL;
					setRelativeLevel(newLevel);
					mPrefs.edit().putInt("relativeLevel", newLevel).apply();
				} else if (actionId == ACTION_INCREASE_LEVEL) {
					// Set and save new relative level
					int newLevel = mRelativeLevel + INCREASE_INTERVAL;
					setRelativeLevel(mRelativeLevel + INCREASE_INTERVAL);
					mPrefs.edit().putInt("relativeLevel", newLevel).apply();
				} else if (actionId >= 0) {
					Log.d(mTag, "setting relative level");
					setRelativeLevel(msg.what);
				}
				return true;
			}
		});
		Looper.loop();
	}

	private void startSensingLight() {
		Log.d(mTag, "start sensing light here");
		if (!mIsSensingLight) {
			mSensorManager.registerListener(this, mLightSensor,
					SensorManager.SENSOR_DELAY_NORMAL, mHandler);
			mIsSensingLight = true;
		}
	}

	private void stopSensingLight() {
		if (mIsSensingLight) {
			mSensorManager.unregisterListener(this, mLightSensor);
			mIsSensingLight = false;
		}
	}

	private void setRelativeLevel(int level) {
		level = Math.min(Math.max(MIN_RELATIVE, level), MAX_RELATIVE);
		if (level != mRelativeLevel) {
			mRelativeLevel = level;
			Log.d(mTag, "new level: " + level);
			updateScreenBrightness();
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		return; // Don't care about accuracy
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// Verify light sensor
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Ignore if updating too often
			long currentTime = System.currentTimeMillis();
			if ((currentTime - mLastSenseMs) < mSenseIntervalMs) {
				return;
			}

			// Stop sensing light, and save last update time
			mLastSenseMs = currentTime;

			// Get lux level
			float oldLux = mCurrentLux;
			mCurrentLux = event.values[0];

			// Only update if lux has changed
			if (Math.abs(mCurrentLux - oldLux) > 0.1) {
				stopSensingLight();
				updateScreenBrightness();
				mHandler.sendEmptyMessageDelayed(ACTION_SENSOR_ON,
						mSenseIntervalMs);
			} else {
				Log.d(mTag, "not updating");
			}
		}
	}

	private void updateScreenBrightness() {
		// Stop the service since user is using system auto brightness
		if (isUsingSystemAutoBrightness()) {
			mService.stopSelf();
		}

		// Compute a new target brightness level
		int newTarget;
		if (mRelativeLevel <= MIN_RELATIVE) {
			mLevelIsAbsolute = true;
			newTarget = MIN_BRIGHTNESS;
			stopSensingLight();
			toast("Min Brightness Set");
		} else if (mRelativeLevel >= MAX_RELATIVE) {
			mLevelIsAbsolute = true;
			newTarget = MAX_BRIGHTNESS;
			stopSensingLight();
			toast("Max Brightness Set");
		} else {
			mLevelIsAbsolute = false;
			newTarget = (int) ((mCurrentLux * MAX_BRIGHTNESS / MAX_LUX) + (3 * mRelativeLevel - MAX_RELATIVE));
			startSensingLight();
		}

		mTargetBrightness = Math.min(MAX_BRIGHTNESS,
				Math.max(MIN_BRIGHTNESS, newTarget));
		mCurrentBrightness = mTargetBrightness;
		Settings.System.putInt(mService.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, mCurrentBrightness);

		// For debbuging: show lux level
		Log.d(mTag, "thread name: " + Thread.currentThread().getName());
		Log.d(mTag, "lux level: " + mCurrentLux);
		Log.d(mTag, "new brightness: " + mCurrentBrightness);
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

	Handler getHandler() {
		return mHandler;
	}

	private void toast(final CharSequence msg) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (mToast != null) {
					mToast.cancel();
				}

				mToast = Toast.makeText(mService.getApplicationContext(), msg,
						Toast.LENGTH_SHORT);
				mToast.show();

				// Vibrate
				((Vibrator) mService.getSystemService(Context.VIBRATOR_SERVICE))
						.vibrate(150);
			}
		});
	}

	synchronized void finish() {
		mHandler.getLooper().quit();
		this.interrupt();
	}

}
