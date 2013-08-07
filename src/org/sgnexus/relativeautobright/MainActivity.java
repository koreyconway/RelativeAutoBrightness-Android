package org.sgnexus.relativeautobright;

import org.sgnexus.relativeautobright.MainService.MainServiceBinder;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

public class MainActivity extends Activity {
	private String mTag = MainActivity.class.getSimpleName();
	private CheckBox mVServiceEnabled;
	private SeekBar mVBrightnessSeekBar;
	private MainServiceBinder mServiceBinder;
	private Toast mToast;
	private SharedPreferences mPrefs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Load the UI
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Load the preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Get and setup UI components
		mVServiceEnabled = (CheckBox) findViewById(R.id.serviceEnabled);
		mVServiceEnabled
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							startService();
						} else {
							stopService();
						}

					}
				});
		mVBrightnessSeekBar = (SeekBar) findViewById(R.id.brightnessSeekBar);
		mVBrightnessSeekBar.setProgress(mPrefs.getInt("relativeLevel", 50));
		mVBrightnessSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						setRelativeLevel(progress);
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						return;
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						return;
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Check if we are even still running and update the checkbox
		mVServiceEnabled.setChecked(isServiceRunning());

		// Read the prefs then set seekbar progress (might have changed from
		// notification area)
		int relativeLevel = PreferenceManager.getDefaultSharedPreferences(this)
				.getInt("relativeLevel", mVBrightnessSeekBar.getProgress());
		mVBrightnessSeekBar.setProgress(relativeLevel);
		
		Log.d(mTag, "resuming");
	}

	private void setRelativeLevel(int relativeLevel) {
		// Update service
		if (mServiceBinder != null) {
			mServiceBinder.setRelativeLevel(relativeLevel);
		}

		// Save into preference file
		mPrefs.edit().putInt("relativeLevel", relativeLevel).apply();
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

	private void startService() {
		toast("Starting service");
		Intent intent = new Intent(this, MainService.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	private void stopService() {
		toast("Stopping service");
		if (mServiceBinder != null) {
			unbindService(mConnection);
			mServiceBinder = null;
		}
		stopService(new Intent(this, MainService.class));
	}

	private void toast(CharSequence msg) {
		if (mToast != null) {
			mToast.cancel();
		}

		mToast = Toast.makeText(getApplicationContext(), msg,
				Toast.LENGTH_SHORT);
		mToast.show();
	}

	@Override
	protected void onDestroy() {
		if (mServiceBinder != null) {
			unbindService(mConnection);
			mServiceBinder = null;
		}
		super.onDestroy();
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			mServiceBinder = (MainServiceBinder) binder;
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.d(mTag, "unbinding this service");
			mServiceBinder = null;
		}
	};

}
