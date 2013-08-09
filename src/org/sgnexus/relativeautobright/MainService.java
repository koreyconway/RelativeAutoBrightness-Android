package org.sgnexus.relativeautobright;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
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
	private long mSenseIntervalMs = 500;
	private float mLux = 0;
	private boolean mIsScreenOn = true;

	private static int LUX_DIFF_THRESHOLD = 0;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if (action != null && action.equals("increase")) {
			increaseBrightness();
		} else if (action != null && action.equals("decrease")) {
			decreaseBrightness();
		} else {
			Log.d(mTag, "starting service");

			// Load settings
			mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			mRelativeLevel = mPrefs.getInt("relativeLevel", 50);
			mPrefs.registerOnSharedPreferenceChangeListener(this);

			// Setup screen on/off detector
			mScreenReceiver = new ScreenReceiver();
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(mScreenReceiver, filter);

			// Setup light sensor
			mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			startSensingLight();

			startNotification();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		Log.d(mTag, "stopping service");
		mPrefs.unregisterOnSharedPreferenceChangeListener(this);
		unregisterReceiver(mScreenReceiver);
		this.stopForeground(true);
		super.onDestroy();
	}

	private void increaseBrightness() {
		setRelativeLevel(mRelativeLevel + 10, true);
	}

	private void decreaseBrightness() {
		setRelativeLevel(mRelativeLevel - 10, true);
	}

	private void startSensingLight() {
		if (mIsScreenOn) {
			mSensorManager.registerListener(this, mLightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void startSensingLight(long delay) {
		new Handler(Looper.myLooper()).postDelayed(new Runnable() {

			@Override
			public void run() {
				startSensingLight();
			}

		}, delay);
	}

	private void stopSensingLight() {
		mSensorManager.unregisterListener(this, mLightSensor);
	}

	private void setRelativeLevel(int relativeLevel) {
		setRelativeLevel(relativeLevel, false);
	}

	private void setRelativeLevel(int relativeLevel, boolean saveToPrefs) {
		relativeLevel = Math.min(Math.max(relativeLevel, 0), 255);
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
		int brightness = (int) ((mLux / 30) + (mRelativeLevel / 2));
		Settings.System.putInt(getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, brightness);
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
			stopSensingLight();
			startSensingLight(mSenseIntervalMs);

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
			setRelativeLevel(sharedPreferences.getInt(key, 50));
		}
	}

	class ScreenReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				mIsScreenOn = false;
				stopSensingLight();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				mIsScreenOn = true;
				startSensingLight();
			}
		}
	}

}
