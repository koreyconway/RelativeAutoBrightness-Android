package org.sgnexus.relativeautobright;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

public class MainService extends Service implements
		OnSharedPreferenceChangeListener, SensorEventListener {
	private String mTag = this.getClass().getSimpleName();
	private ScreenReceiver mScreenReceiver;
	private int mRelativeLevel;
	private SharedPreferences mPrefs;
	private SensorManager mSensorManager;
	private Sensor mLightSensor;
	private long mLastSenseMs = 0;
	private long mLastBrightnessChangeMs;
	private long mSenseIntervalMs = 500;
	private float mLux = 0;
	private boolean mIsSensingEnabled = true;
	private SettingsContentObserver mSettingsObserver;
	private int mBrightness;

	final private static int LUX_DIFF_THRESHOLD = 0;
	final private static int LAST_BRIGHTNESS_CHANGE_THRESHOLD_MS = 300;
	final private static int DEFAULT_RELATIVE_LEVEL = 50;
	final private static String RELATIVE_LEVEL_KEY = "relativeLevel";
	final private static int INCREASE_LEVEL = 10; // TODO put this in advanced
													// preferences
	final private static int MIN_RELATIVE_LEVEL = 0;
	final private static int MAX_RELATIVE_LEVEL = 255;
	final private static int DEFAULT_BRIGHTNESS = 100;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if ("increase".equals(action)) {
			increaseBrightness();
			return super.onStartCommand(intent, flags, startId);
		} else if ("decrease".equals(action)) {
			decreaseBrightness();
			return super.onStartCommand(intent, flags, startId);
		}

		Log.d(mTag, "starting service");

		// Load settings
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mRelativeLevel = mPrefs.getInt(RELATIVE_LEVEL_KEY,
				DEFAULT_RELATIVE_LEVEL);
		mPrefs.registerOnSharedPreferenceChangeListener(this);

		// Setup screen on/off detector
		mScreenReceiver = new ScreenReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mScreenReceiver, filter);

		// Set to manual brightness mode
		Settings.System.putInt(getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS_MODE,
				Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		// Setup system settings observer
		mSettingsObserver = new SettingsContentObserver(new Handler());
		getContentResolver()
				.registerContentObserver(
						Uri.withAppendedPath(
								android.provider.Settings.System.CONTENT_URI,
								android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE),
						true, mSettingsObserver);
		getContentResolver().registerContentObserver(
				Uri.withAppendedPath(
						android.provider.Settings.System.CONTENT_URI,
						android.provider.Settings.System.SCREEN_BRIGHTNESS),
				true, mSettingsObserver);

		// Get current brightness
		try {
			mBrightness = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS);
		} catch (SettingNotFoundException e) {
			mBrightness = DEFAULT_BRIGHTNESS;
		}

		// Setup light sensor
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		startSensingLight();

		// Start the foreground notification
		startNotification();

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		Log.d(mTag, "stopping service");
		disableSensingLight();
		unregisterReceiver(mScreenReceiver);
		getContentResolver().unregisterContentObserver(mSettingsObserver);
		mPrefs.unregisterOnSharedPreferenceChangeListener(this);
		mPrefs.edit().putBoolean("serviceEnabled", false);
		this.stopForeground(true);
		super.onDestroy();
	}

	private void increaseBrightness() {
		setRelativeLevel(mRelativeLevel + INCREASE_LEVEL, true);
	}

	private void decreaseBrightness() {
		setRelativeLevel(mRelativeLevel - INCREASE_LEVEL, true);
	}

	private void startSensingLight() {
		if (mIsSensingEnabled) {
			mSensorManager.registerListener(this, mLightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void pauseSensingLight(long delay) {
		mSensorManager.unregisterListener(this, mLightSensor);
		new Handler(Looper.myLooper()).postDelayed(new Runnable() {

			@Override
			public void run() {
				startSensingLight();
			}

		}, delay);
	}

	private void enableSensingLight() {
		mIsSensingEnabled = true;
		startSensingLight();
	}

	private void disableSensingLight() {
		mIsSensingEnabled = false;
		mSensorManager.unregisterListener(this, mLightSensor);
	}

	private void setRelativeLevel(int relativeLevel) {
		setRelativeLevel(relativeLevel, false);
	}

	private void setRelativeLevel(int relativeLevel, boolean saveToPrefs) {
		relativeLevel = Math.min(Math.max(relativeLevel, MIN_RELATIVE_LEVEL),
				MAX_RELATIVE_LEVEL);
		Log.d(mTag, "new relative level: " + relativeLevel);
		if (mRelativeLevel != relativeLevel) {
			mRelativeLevel = relativeLevel;
			updateBrightness();

			if (saveToPrefs) {
				mPrefs.edit().putInt("relativeLevel", mRelativeLevel).commit();
			}
		}
	}

	private void updateBrightness() {
		int newBrightness = (int) ((mLux / 30) + (mRelativeLevel / 2));
		if (newBrightness != mBrightness) {
			mBrightness = newBrightness;
			Settings.System.putInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS, mBrightness);
			mLastBrightnessChangeMs = System.currentTimeMillis();
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Ignore if updating too often
			long currentTime = System.currentTimeMillis();
			if ((currentTime - mLastSenseMs) < mSenseIntervalMs) {
				return;
			}

			// Save last update time
			mLastSenseMs = currentTime;

			// Get lux level
			float previousLux = mLux;
			float newLux = event.values[0];

			// Turn off light sensor and schedule next reading
			pauseSensingLight(mSenseIntervalMs);

			// Only update if lux has changed
			if (Math.abs(newLux - previousLux) > LUX_DIFF_THRESHOLD) {
				Log.d(mTag, "new lux: " + newLux);
				mLux = newLux;
				updateBrightness();
			} else {
				Log.d(mTag, "not updating");
			}
		}
	}

	private void startNotification() {
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
		RemoteViews remoteView = new RemoteViews(getPackageName(),
				R.layout.notification);

		intent = new Intent(this, MainService.class).setAction("increase");
		pIntent = PendingIntent.getService(this, 0, intent, 0);
		remoteView.setOnClickPendingIntent(R.id.increaseButton, pIntent);

		intent = new Intent(this, MainService.class).setAction("decrease");
		pIntent = PendingIntent.getService(this, 0, intent, 0);
		remoteView.setOnClickPendingIntent(R.id.decreaseButton, pIntent);

		NotificationCompat.Builder notification = new NotificationCompat.Builder(
				this).setContent(remoteView).setSmallIcon(
				R.drawable.ic_launcher);
		notification.setContentIntent(pIntent);
		startForeground(1, notification.build());
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		return; // No need for this
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if ("relativeLevel".equals(key)) {
			setRelativeLevel(sharedPreferences.getInt(key,
					DEFAULT_RELATIVE_LEVEL));
		}
	}

	private class ScreenReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				disableSensingLight();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				enableSensingLight();
			}
		}
	}

	private class SettingsContentObserver extends ContentObserver {

		public SettingsContentObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			if ("content://settings/system/screen_brightness".equals(uri
					.toString())) {
				// Stop service if brightness was modified by another app
				if ((System.currentTimeMillis() - mLastBrightnessChangeMs) > LAST_BRIGHTNESS_CHANGE_THRESHOLD_MS) {
					Log.d(mTag, "Brightness changed outside of app");
					stopSelf();
				}
			} else if ("content://settings/system/screen_brightness_mode"
					.equals(uri.toString())) {
				// Stop server if brightness mode changed outside of app
				Log.d(mTag, "Brightness mode changed outside of app");
				stopSelf();
			}
		}

	}

}
