package org.sgnexus.relativeautobright;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightService extends Service {
	private boolean mRunning = false;
	private String mTag = this.getClass().getSimpleName();
	private Thread mThread;
	private LightDetector mLightDetector;
	private SensorManager mSensorManager;
	private Sensor mLightSensor;
	private int mBrightness = 0;
	private final int SENSOR_DELAY = 3000000;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Toast.makeText(getApplicationContext(), "Opening service",
				Toast.LENGTH_SHORT).show();
		if (!mRunning || !mThread.isAlive()) {
			mRunning = true;
			Settings.System.putInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE,
					Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

			mThread = new Thread() {
				public void run() {
					mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
					mLightSensor = mSensorManager
							.getDefaultSensor(Sensor.TYPE_LIGHT);
					mLightDetector = new LightDetector(AutoBrightService.this);
					mSensorManager.registerListener(mLightDetector,
							mLightSensor, SENSOR_DELAY);

					while (mRunning) {
						try {
							Log.d(mTag, "setting brightness to:" + mBrightness);
							Settings.System.putInt(getContentResolver(),
									Settings.System.SCREEN_BRIGHTNESS,
									mBrightness);
							this.wait();
						} catch (InterruptedException e) {
							// We might simply be stopping, let it happen
						}
					}
				}
			};

			mThread.start();
		}

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		Toast.makeText(getApplicationContext(), "Closing service",
				Toast.LENGTH_SHORT).show();
		mRunning = false;
		mThread.interrupt();
		super.onDestroy();
	}
	
	public int getBrightness() {
		return mBrightness;
	}
	
	public void setBrightness(int brightness) {
		mBrightness = Math.max(Math.min(brightness, 255), 0);
		mThread.notify();
	}
}
