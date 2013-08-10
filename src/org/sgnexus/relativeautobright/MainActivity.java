package org.sgnexus.relativeautobright;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.widget.Toast;

public class MainActivity extends Activity {
	private String mTag = MainActivity.class.getSimpleName();
	private SettingsFragment mSettingsFragment;

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

}
