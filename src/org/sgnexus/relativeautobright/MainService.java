package org.sgnexus.relativeautobright;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class MainService extends Service {
	private String mTag = this.getClass().getSimpleName();
	private BackgroundThread mBgThread;
	private ScreenReceiver mScreenReceiver;

	void init() {
		if (mBgThread == null) {
			Log.d(mTag, "Creating service object");
			int relativeLevel = PreferenceManager.getDefaultSharedPreferences(
					this).getInt("relativeLevel", 50);

			// Setup screen on/off detector
			mScreenReceiver = new ScreenReceiver();
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(mScreenReceiver, filter);

			mBgThread = new BackgroundThread(this, relativeLevel);
			startBgThread();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(mTag, "starting service");
		init();
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(mTag, "binding service");
		init();
		return new MainServiceBinder();
	}

	private void startBgThread() {
		mBgThread.start();
		startNotification();
	}

	private void stopBgThread() {
		mBgThread.finish();
		mBgThread.interrupt();
		stopForeground(true);
	}

	private void startNotification() {
		NotificationCompat.Builder notification = new NotificationCompat.Builder(
				this).setContentTitle("AutoBright")
				.setContentText("content text")
				.setSmallIcon(R.drawable.ic_launcher);
		startForeground(1, notification.build());
	}

	void setRelativeLevel(int relativeLevel) {
		mBgThread.getHandler().sendEmptyMessage(relativeLevel);
		mBgThread.interrupt();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(mTag, "unbinding service");
		return super.onUnbind(intent);
	}

	@Override
	public void onDestroy() {
		Log.d(mTag, "killing service");
		unregisterReceiver(mScreenReceiver);
		stopBgThread();
		super.onDestroy();
	}

	class MainServiceBinder extends Binder {
		void setRelativeLevel(int relativeLevel) {
			MainService.this.setRelativeLevel(relativeLevel);
		}
	}

	class ScreenReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				Log.d(mTag, "screen off");
				mBgThread.getHandler().sendEmptyMessage(
						BackgroundThread.ACTION_SCREEN_OFF);
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				Log.d(mTag, "screen on");
				mBgThread.getHandler().sendEmptyMessage(
						BackgroundThread.ACTION_SCREEN_ON);
			}
		}
	}
}
