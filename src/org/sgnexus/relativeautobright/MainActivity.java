package org.sgnexus.relativeautobright;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.Toast;

public class MainActivity extends Activity implements
		OnSharedPreferenceChangeListener {
	private String mTag = MainActivity.class.getSimpleName();
	private SettingsFragment mSettingsFragment;
	private Toast mToast;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set default settings (first run only)
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		// Load the settings UI
		mSettingsFragment = new SettingsFragment();
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, mSettingsFragment).commit();
	}

	@Override
	protected void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
		((SwitchPreference) mSettingsFragment.findPreference("serviceEnabled"))
				.setChecked(isServiceRunning());
	}

	@Override
	protected void onPause() {
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	private void setServiceEnabled(boolean enabled) {
		if (enabled) {
			showToast("Starting service");
			startService(new Intent(this, MainService.class));
		} else {
			showToast("Stopping service");
			stopService(new Intent(this, MainService.class));
		}
	}

	private void showToast(CharSequence msg) {
		if (mToast != null) {
			mToast.cancel();
		}

		mToast = Toast.makeText(getApplicationContext(), msg,
				Toast.LENGTH_SHORT);
		mToast.show();
	}

	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
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
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if ("serviceEnabled".equals(key)) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			setServiceEnabled(enabled);
		} else if ("relativeLevel".equals(key)) {
			// TODO update the preference
		}
	}
}
