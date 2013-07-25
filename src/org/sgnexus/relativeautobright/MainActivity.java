package org.sgnexus.relativeautobright;

import org.sgnexus.relativeautobright.AutoBrightnessService.BrightnessBinder;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

public class MainActivity extends Activity {
	// private String mTag = MainActivity.class.getSimpleName();
	private CheckBox mVServiceEnabled;
	private SeekBar mVBrightnessSeekBar;
	private AutoBrightnessService mService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mVServiceEnabled = (CheckBox) findViewById(R.id.serviceEnabled);
		mVBrightnessSeekBar = (SeekBar) findViewById(R.id.brightnessSeekBar);
		
		this.mVBrightnessSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						if (mService != null) {
							mService.setRelativeLevel(progress);
						}
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
		mVServiceEnabled.setChecked(isServiceRunning());
	}

	public void toggleService(View v) {
		Log.d("thread", "Activity Thread id: " + Thread.currentThread().getId());
		if (isServiceRunning()) {
			stopService();
		} else {
			startService();
		}
	}

	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (AutoBrightnessService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
	
	private void stopService() {
		Toast.makeText(getApplicationContext(), "Stopping service",
				Toast.LENGTH_SHORT).show();
		this.unbindService(mConnection);
		super.stopService(new Intent(this, AutoBrightnessService.class));
	}
	
	private void startService() {
		Toast.makeText(getApplicationContext(), "Starting service",
				Toast.LENGTH_SHORT).show();
		Intent intent = new Intent(this, AutoBrightnessService.class);
		intent.putExtra("relativeLevel", mVBrightnessSeekBar.getProgress());
		super.startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			mService = ((BrightnessBinder)binder).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

}
