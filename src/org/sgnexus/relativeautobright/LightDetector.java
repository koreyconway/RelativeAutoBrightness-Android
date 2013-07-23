package org.sgnexus.relativeautobright;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

public class LightDetector implements SensorEventListener {
	AutoBrightService mService;

	LightDetector(AutoBrightService service) {
		mService = service;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		return;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		int b = mService.getBrightness();
		b += 5;
		mService.setBrightness(b);
		Log.d("light listener", "lux is at: " + event.values[0]);
	}

}
