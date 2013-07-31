package org.sgnexus.relativeautobright;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class AutoBrightnessService extends Service {
	private AutoBrightnessThread mThread;
	private String mTag = this.getClass().getSimpleName();
	private Toast mToast;

	private void init(int initialRelativeLevel) {
		mThread = new AutoBrightnessThread(this, initialRelativeLevel);
		new Thread(mThread).start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(mTag, "starting service");
		init(intent.getExtras().getInt("relativeLevel"));
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(mTag, "binding service");
		init(intent.getExtras().getInt("relativeLevel"));
		return new BrightnessBinder();
	}

	public void setRelativeLevel(int level) {
		mThread.setRelativeLevel(level);
	}

	@Override
	public void onDestroy() {
		Log.d(mTag, "killing service");
		mThread.finish();
		super.onDestroy();
	}
	
	synchronized public void toast(CharSequence msg) {
		if (mToast != null) {
			mToast.cancel();
		}

		mToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
		mToast.show();
	}


	class BrightnessBinder extends Binder {
		public AutoBrightnessService getService() {
			return AutoBrightnessService.this;
		}
	}
}
