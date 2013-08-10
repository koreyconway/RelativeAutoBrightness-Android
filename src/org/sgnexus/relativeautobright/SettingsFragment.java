package org.sgnexus.relativeautobright;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {
	private String mTag = this.getClass().getSimpleName();
	private SwitchPreference mServiceEnabledPref;
	final private static String SERVICE_ENABLED_KEY = "serviceEnabled";
	final private static String RELATIVE_LEVEL_KEY = "relativeLevel";
	private Context mContext;
	private SharedPreferences mPrefs;
	private Toast mToast;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		mServiceEnabledPref = (SwitchPreference) findPreference(SERVICE_ENABLED_KEY);
	}

	@Override
	public void onResume() {
		Log.d(mTag, "on resume");

		super.onResume();

		// Check if service is active and update UI accordingly
		boolean isServiceRunning = mPrefs
				.getBoolean(SERVICE_ENABLED_KEY, false);
		if (isServiceRunning != mServiceEnabledPref.isChecked()) {
			mServiceEnabledPref.setChecked(isServiceRunning);
		}

		// TODO update the seekbar if relative level changed

		mPrefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
		Log.d(mTag, "on pause");

		mPrefs.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	@Override
	public void onAttach(Activity activity) {
		Log.d(mTag, "on attach");
		super.onAttach(activity);
		mContext = activity;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
	}

	@Override
	public void onDetach() {
		Log.d(mTag, "on detach");
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (SERVICE_ENABLED_KEY.equals(key)) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			setServiceEnabled(enabled);
		} else if (RELATIVE_LEVEL_KEY.equals(key)) {
			// TODO update the seekbar
		}
	}

}
