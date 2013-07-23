package org.sgnexus.relativeautobright;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

public class MainActivity extends Activity {

	private CheckBox mVServiceEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mVServiceEnabled = (CheckBox) findViewById(R.id.serviceEnabled);
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
		if (isServiceRunning()) {
			Toast.makeText(getApplicationContext(), "Stopping service",
					Toast.LENGTH_SHORT).show();
			stopService(new Intent(this, AutoBrightService.class));
		} else {
			Toast.makeText(getApplicationContext(), "Starting service",
					Toast.LENGTH_SHORT).show();
			startService(new Intent(this, AutoBrightService.class));
		}
	}

	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (AutoBrightService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
}
