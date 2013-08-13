package org.sgnexus.relativeautobright;

import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment implements Observer {
	final private String mTag = this.getClass().getSimpleName();

	private SwitchPreference mServiceEnabledPref;
	private Preference mLuxPref;
	private Preference mBrightnessPref;
	private Context mContext;
	private Toast mToast;
	private Data mData;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		mServiceEnabledPref = (SwitchPreference) findPreference(Data.SERVICE_ENABLED);
		mLuxPref = (Preference) findPreference(Data.LUX);
		mBrightnessPref = (Preference) findPreference(Data.BRIGHTNESS);
	}

	@Override
	public void onResume() {
		Log.d(mTag, "on resume");

		super.onResume();

		// Check if service is active and update UI accordingly
		boolean isServiceRunning = isServiceRunning();
		if (isServiceRunning != mServiceEnabledPref.isChecked()) {
			mServiceEnabledPref.setChecked(isServiceRunning);
		}

		// TODO update the seekbar if relative level changed
	}

	@Override
	public void onPause() {
		Log.d(mTag, "on pause");
		super.onPause();
	}

	@Override
	public void onAttach(Activity activity) {
		Log.d(mTag, "on attach");
		super.onAttach(activity);
		mContext = activity.getApplicationContext();
		//PreferenceManager.setDefaultValues(mContext, R.xml.preferences, false);
		mData = Data.getInstance(mContext);
		mData.addObserver(this);
	}

	@Override
	public void onDetach() {
		Log.d(mTag, "on detach");
		mData.deleteObserver(this);
		mContext = null;
		super.onDetach();
	}

	private void setServiceEnabled(boolean enabled) {
		if (enabled) {
			showToast("Starting service");
			mContext.startService(new Intent(mContext, MainService.class));
		} else {
			showToast("Stopping service");
			mContext.stopService(new Intent(mContext, MainService.class));
		}
	}

	private void showToast(CharSequence msg) {
		if (mToast != null) {
			mToast.cancel();
		}

		mToast = Toast.makeText(mContext.getApplicationContext(), msg,
				Toast.LENGTH_SHORT);
		mToast.show();
	}

	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) mContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (MainService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void update(Observable observable, Object data) {
		String key = (String) data;

		Log.d(mTag, "update fragment: " + key);

		if (Data.SERVICE_ENABLED.equals(key)) {
			setServiceEnabled(mData.getServiceEnabled());
		} else if (Data.RELATIVE_LEVEL.equals(key)) {
			// TODO update the seekbar
		} else if (Data.LUX.equals(key)) {
			mLuxPref.setTitle("Lux : " + mData.getLux());
		} else if (Data.BRIGHTNESS.equals(key)) {
			int brightnessPercentage = (mData.getBrightness() * 100 / Data.MAX_BRIGHTNESS);
			mBrightnessPref.setTitle("Brightness : " + brightnessPercentage
					+ "%");
		}
	}

}
