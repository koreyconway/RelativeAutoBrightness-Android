package org.sgnexus.relativeautobright;

import java.util.Observable;
import java.util.Observer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

class Data extends Observable implements OnSharedPreferenceChangeListener {
	final private String mTag = this.getClass().getSimpleName();
	static private Data sInstance;
	private SharedPreferences mPrefs;
	private Context mContext;
	private SettingsContentObserver mSettingsObserver;
	private boolean isListening = false;

	final static String SERVICE_ENABLED = "serviceEnabled";
	final static String RELATIVE_LEVEL = "relativeLevel";
	final static String LUX = "lux";
	final static String BRIGHTNESS = "brightness";
	final static String BRIGHTNESS_MODE = "brightnessMode";
	final static String SENSE_INTERVAL = "senseIntervalMs";

	static final int MIN_BRIGHTNESS = 0;
	static final int MAX_BRIGHTNESS = 255;
	static final int MIN_RELATIVE_LEVEL = 0;
	static final int MAX_RELATIVE_LEVEL = 100;

	// todo: put these in advanced preferences
	final static int LUX_DIFF_THRESHOLD = 1;
	final static int INCREASE_LEVEL = 5;
	final static int DEFAULT_SENSE_INTERVAL = 2000;

	private boolean mServiceEnabled;
	private int mRelativeLevel;
	private float mLux = 1.0f;
	private int mBrightness;
	private int mBrightnessMode;
	private int mSenseInterval;

	private Data(Context context) {
		mContext = context;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Set initial data values
		loadValuesFromPrefs();
	}

	static Data getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new Data(context);
		}

		return sInstance;
	}

	void loadValuesFromPrefs() {
		mServiceEnabled = mPrefs.getBoolean(SERVICE_ENABLED, false);
		mRelativeLevel = mPrefs.getInt(RELATIVE_LEVEL, 50);
		mBrightness = Settings.System.getInt(mContext.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, 0);
		mBrightnessMode = Settings.System.getInt(mContext.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
		mSenseInterval = Integer.parseInt(mPrefs.getString(SENSE_INTERVAL,
				"5000"));
	}

	void setRelativeLevel(int level) {
		setRelativeLevel(level, false);
	}

	void setRelativeLevel(int level, boolean saveInSharedPrefs) {
		level = Math.min(Math.max(level, MIN_RELATIVE_LEVEL),
				MAX_RELATIVE_LEVEL);

		if (level != mRelativeLevel) {
			mRelativeLevel = level;
			if (saveInSharedPrefs) {
				mPrefs.edit().putInt(RELATIVE_LEVEL, level).commit();
			}
			notifyObservers(RELATIVE_LEVEL);
		}
	}

	int getRelativeLevel() {
		return mRelativeLevel;
	}

	void setBrightnessMode(int mode) {
		int prevMode = getBrightnessMode();

		if (prevMode != mode) {
			Settings.System.putInt(mContext.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
			mBrightnessMode = mode;
			notifyObservers(BRIGHTNESS_MODE);
		}

	}

	int getBrightnessMode() {
		return mBrightnessMode;
	}

	void setServiceEnabled(boolean enabled) {
		if (enabled != mServiceEnabled) {
			mServiceEnabled = enabled;
			notifyObservers(SERVICE_ENABLED);
		}
	}

	boolean getServiceEnabled() {
		return mServiceEnabled;
	}

	void setBrightness(int brightness) {
		brightness = Math.min(Math.max(brightness, MIN_BRIGHTNESS),
				MAX_BRIGHTNESS);

		if (mBrightness != brightness) {
			mBrightness = brightness;
			Settings.System.putInt(mContext.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS, brightness);
			notifyObservers(BRIGHTNESS);
		}

	}

	int getBrightness() {
		return mBrightness;
	}

	void setLux(float lux) {
		if (Float.compare(mLux, lux) != 0) {
			mLux = lux;
			notifyObservers(LUX);
		}
	}

	float getLux() {
		return mLux;
	}

	void setSenseInterval(int intervalMs) {
		if (mSenseInterval != intervalMs) {
			mSenseInterval = intervalMs;
			notifyObservers(SENSE_INTERVAL);
		}
	}

	int getSenseInterval() {
		return mSenseInterval;
	}

	private void notifyObservers(String key) {
		setChanged();
		super.notifyObservers(key);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.d(mTag, "pref changed: " + key);
		if (key.equals(RELATIVE_LEVEL)) {
			setRelativeLevel(prefs.getInt(key, 0));
		} else if (key.equals(SERVICE_ENABLED)) {
			setServiceEnabled(prefs.getBoolean(key, false));
		} else if (key.equals(SENSE_INTERVAL)) {
			setSenseInterval(Integer.parseInt(prefs.getString(key, "5000")));
		}
	}

	private class SettingsContentObserver extends ContentObserver {

		public SettingsContentObserver() {
			super(new Handler());
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			if ("content://settings/system/screen_brightness".equals(uri
					.toString())) {
				int brightness = Settings.System.getInt(
						mContext.getContentResolver(),
						Settings.System.SCREEN_BRIGHTNESS, 0);

				if (brightness != mBrightness) {
					mBrightness = brightness;
					notifyObservers(BRIGHTNESS);
				}
			} else if ("content://settings/system/screen_brightness_mode"
					.equals(uri.toString())) {
				int brightnessMode = Settings.System.getInt(
						mContext.getContentResolver(),
						Settings.System.SCREEN_BRIGHTNESS_MODE, 0);

				if (brightnessMode != mBrightnessMode) {
					mBrightnessMode = brightnessMode;
					notifyObservers(BRIGHTNESS_MODE);
				}
			}
		}

	}

	@Override
	public void addObserver(Observer observer) {
		Log.d(mTag, "adding observer: #" + countObservers());
		if (!isListening) {
			startListening();
		}
		super.addObserver(observer);
	}

	private void startListening() {
		// Setup listeners
		if (!isListening) {
			Log.d(mTag, "setup data listeners");
			isListening = true;
			loadValuesFromPrefs();
			mPrefs.registerOnSharedPreferenceChangeListener(this);
			mSettingsObserver = new SettingsContentObserver();
			mContext.getContentResolver()
					.registerContentObserver(
							Uri.withAppendedPath(
									android.provider.Settings.System.CONTENT_URI,
									android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE),
							true, mSettingsObserver);
			mContext.getContentResolver()
					.registerContentObserver(
							Uri.withAppendedPath(
									android.provider.Settings.System.CONTENT_URI,
									android.provider.Settings.System.SCREEN_BRIGHTNESS),
							true, mSettingsObserver);
		}
	}

	@Override
	public synchronized void deleteObserver(Observer observer) {
		super.deleteObserver(observer);

		if (countObservers() == 0) {
			stopListening();
		}
	}

	@Override
	public synchronized void deleteObservers() {
		super.deleteObservers();
		stopListening();
	}

	private void stopListening() {
		// Remove listeners
		if (isListening) {
			Log.d(mTag, "removing data listeners");
			isListening = false;
			mPrefs.unregisterOnSharedPreferenceChangeListener(this);
			mContext.getContentResolver().unregisterContentObserver(
					mSettingsObserver);
		}
	}

}
