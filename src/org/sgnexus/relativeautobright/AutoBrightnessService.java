package org.sgnexus.relativeautobright;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightnessService extends Service implements
		SensorEventListener {
	private final int SENSOR_DELAY = 3000;
	private final int MAX_BRIGHTNESS = 255;
	private final int MIN_BRIGHTNESS = 0;
	private String mTag = this.getClass().getSimpleName();
	private SensorManager mSensorManager;
	private int relativeLevel = 10;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Settings.System.putInt(getContentResolver(),
		// Settings.System.SCREEN_BRIGHTNESS_MODE,
		// Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		mSensorManager.registerListener(this, lightSensor, SENSOR_DELAY);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public void setBrightness(int brightness) {
		brightness = Math.max(Math.min(brightness, MAX_BRIGHTNESS),
				MIN_BRIGHTNESS);
		Settings.System.putInt(getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, brightness);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		return;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			float lux = event.values[0];
			int newBrightness = (int) Math.log(lux) * relativeLevel;
			setBrightness(newBrightness);

			Log.d(mTag, "lux level: " + lux);
			Log.d(mTag, "brightness level: " + newBrightness);
			Toast.makeText(getApplicationContext(), "lux: " + lux,
					Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onDestroy() {
		mSensorManager.unregisterListener(this);
		super.onDestroy();
	}
}
