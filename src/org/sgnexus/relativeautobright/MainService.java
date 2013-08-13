package org.sgnexus.relativeautobright;

import java.util.Observable;
import java.util.Observer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

public class MainService extends Service implements Observer,
		SensorEventListener {
	private String mTag = this.getClass().getSimpleName();
	private Data mData;
	private ScreenReceiver mScreenReceiver;
	private SensorManager mSensorManager;
	private Sensor mLightSensor;

	private int mRelativeLevel;
	private long mSenseIntervalMs = 0;
	private float mLux = 0;
	private boolean mIsSensingEnabled = true;
	private int mBrightness;
	private boolean mIsSensing = false;
	private boolean mIsSensingQueued = false;

	private AutoBrightnessStrategy mStrategy = new DefaultStrategy();

	final private static String NOTIFICATION_ACTION_DECREASE = "decrease";
	final private static String NOTIFICATION_ACTION_INCREASE = "increase";

	final private Handler HANDLER = new Handler(); // for caching only

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if (NOTIFICATION_ACTION_INCREASE.equals(action)) {
			increaseBrightness();
			return super.onStartCommand(intent, flags, startId);
		} else if (NOTIFICATION_ACTION_DECREASE.equals(action)) {
			decreaseBrightness();
			return super.onStartCommand(intent, flags, startId);
		}

		Log.d(mTag, "starting service");

		// Load settings
		mData = Data.getInstance(getApplicationContext());
		mRelativeLevel = mData.getRelativeLevel();
		mSenseIntervalMs = mData.getSenseInterval();

		// Setup screen on/off detector
		mScreenReceiver = new ScreenReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mScreenReceiver, filter);

		// Set to manual brightness mode
		mData.setBrightnessMode(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		// Get current brightness
		mBrightness = mData.getBrightness();

		// Observe setting changes
		mData.addObserver(this);

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
		mData.deleteObserver(this);
		mData.setServiceEnabled(false);
		this.stopForeground(true);
		super.onDestroy();
	}

	private void increaseBrightness() {
		mData.setRelativeLevel(mRelativeLevel + Data.INCREASE_LEVEL, true);
	}

	private void decreaseBrightness() {
		mData.setRelativeLevel(mRelativeLevel - Data.INCREASE_LEVEL, true);
	}

	private void startSensingLight() {
		if (mIsSensingEnabled && !mIsSensing) {
			Log.d("sense", "starting light sensor");
			mIsSensing = true;
			mSensorManager.registerListener(this, mLightSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void pauseSensingLight(long delay) {
		Log.d("sense", "pausing light sensor");
		mIsSensing = false;
		mSensorManager.unregisterListener(this, mLightSensor);
		mIsSensingQueued = true;
		HANDLER.postDelayed(new Runnable() {
			@Override
			public void run() {
				startSensingLight();
				mIsSensingQueued = false;
			}
		}, delay);
	}

	private void enableSensingLight() {
		mIsSensingEnabled = true;
		if (!mIsSensingQueued) {
			startSensingLight();
		}
	}

	private void disableSensingLight() {
		mIsSensingEnabled = false;
		mIsSensing = false;
		mSensorManager.unregisterListener(this, mLightSensor);
	}

	private void updateBrightness() {
		int newBrightness;

		if (mRelativeLevel == Data.MIN_RELATIVE_LEVEL) {
			newBrightness = Data.MIN_BRIGHTNESS;
			disableSensingLight();
			buzz();
		} else if (mRelativeLevel == Data.MAX_RELATIVE_LEVEL) {
			newBrightness = Data.MAX_BRIGHTNESS;
			disableSensingLight();
			buzz();
		} else {
			newBrightness = mStrategy.computeBrightness(mData);
			enableSensingLight();
		}

		if (newBrightness != mBrightness) {
			// mLastBrightnessChangeMs = System.currentTimeMillis();
			mData.setBrightness(newBrightness);
		}
	}

	private void buzz() {
		((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(150);
	}

	private void startNotification() {
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
		RemoteViews remoteView = new RemoteViews(getPackageName(),
				R.layout.notification);

		intent = new Intent(this, MainService.class)
				.setAction(NOTIFICATION_ACTION_INCREASE);
		pIntent = PendingIntent.getService(this, 0, intent, 0);
		remoteView.setOnClickPendingIntent(R.id.increaseButton, pIntent);

		intent = new Intent(this, MainService.class)
				.setAction(NOTIFICATION_ACTION_DECREASE);
		pIntent = PendingIntent.getService(this, 0, intent, 0);
		remoteView.setOnClickPendingIntent(R.id.decreaseButton, pIntent);

		NotificationCompat.Builder notification = new NotificationCompat.Builder(
				this).setContent(remoteView).setSmallIcon(
				R.drawable.ic_launcher);
		notification.setContentIntent(pIntent);
		startForeground(1, notification.build());
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Ignore if updating too often
			// long currentTime = System.currentTimeMillis();
			// if ((currentTime - mLastSenseMs) < mSenseIntervalMs) {
			// return;
			// }

			// Save last update time
			// mLastSenseMs = currentTime;

			// Turn off light sensor and schedule next reading
			pauseSensingLight(mSenseIntervalMs);

			// Get lux level
			float newLux = event.values[0];

			// Only update if lux has changed significantly
			if (Math.abs(newLux - mLux) > Data.LUX_DIFF_THRESHOLD) {
				mData.setLux(newLux);
			}

			Log.d("sense", "lux: " + newLux);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		return; // No need for this
	}

	@Override
	public void update(Observable observable, Object data) {
		String key = (String) data;

		Log.d(mTag, "updating service: " + key);

		if (Data.RELATIVE_LEVEL.equals(key)) {
			mRelativeLevel = mData.getRelativeLevel();
			updateBrightness();
		} else if (Data.LUX.equals(key)) {
			mLux = mData.getLux();
			updateBrightness();
		} else if (Data.BRIGHTNESS_MODE.equals(key)) {
			if (mData.getBrightnessMode() == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
				stopSelf();
			}
		} else if (Data.BRIGHTNESS.equals(key)) {
			mBrightness = mData.getBrightness();
		} else if (Data.SENSE_INTERVAL.equals(key)) {
			mSenseIntervalMs = mData.getSenseInterval();
		}
	}

	private class ScreenReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				Log.d(mTag, "screen off");
				disableSensingLight();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				Log.d(mTag, "screen on");
				enableSensingLight();
			}
		}
	}
}
