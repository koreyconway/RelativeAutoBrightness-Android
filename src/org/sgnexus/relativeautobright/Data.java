package org.sgnexus.relativeautobright;

import java.util.HashMap;
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

public class Data extends Observable implements
		OnSharedPreferenceChangeListener {
	final private String mTag = this.getClass().getSimpleName();
	static private Data sInstance;
	private SharedPreferences mPrefs;
	private Context mContext;
	private SettingsContentObserver mSettingsObserver;

	// private LinkedList<Observer> observers = new LinkedList<Observer>();

	private HashMap<String, Object> values = new HashMap<String, Object>();

	final public static String SERVICE_ENABLED = "serviceEnabled";
	final public static String RELATIVE_LEVEL = "relativeLevel";
	final public static String LUX = "lux";
	final public static String BRIGHTNESS = "brightness";
	final public static String BRIGHTNESS_MODE = "brightnessMode";
	final public static String SENSE_INTERVAL = "senseIntervalMs";

	public static final int MIN_BRIGHTNESS = 0;
	public static final int MAX_BRIGHTNESS = 255;
	public static final int MIN_RELATIVE_LEVEL = 0;
	public static final int MAX_RELATIVE_LEVEL = 100;

	// todo: put these in advanced preferences
	final public static int LUX_DIFF_THRESHOLD = 0;
	final public static int INCREASE_LEVEL = 10;
	final public static int DEFAULT_SENSE_INTERVAL = 2000;

	private Data(Context context) {
		mContext = context;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Set initial data values
		putBoolean(SERVICE_ENABLED, mPrefs.getBoolean(SERVICE_ENABLED, false));
		putInt(RELATIVE_LEVEL, mPrefs.getInt(RELATIVE_LEVEL, 50));
		putInt(LUX, 1);
		putInt(BRIGHTNESS, Settings.System.getInt(
				mContext.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, 0));
		putInt(BRIGHTNESS_MODE, Settings.System.getInt(
				mContext.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS_MODE, 0));
		putInt(SENSE_INTERVAL,
				Integer.parseInt(mPrefs.getString(SENSE_INTERVAL, "5000")));

		// Setup listeners
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		mSettingsObserver = new SettingsContentObserver();
		mContext.getContentResolver()
				.registerContentObserver(
						Uri.withAppendedPath(
								android.provider.Settings.System.CONTENT_URI,
								android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE),
						true, mSettingsObserver);
		mContext.getContentResolver().registerContentObserver(
				Uri.withAppendedPath(
						android.provider.Settings.System.CONTENT_URI,
						android.provider.Settings.System.SCREEN_BRIGHTNESS),
				true, mSettingsObserver);
	}

	static public Data getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new Data(context);
		}

		return sInstance;
	}

	public void setRelativeLevel(int level) {
		setRelativeLevel(level, false);
	}

	public void setRelativeLevel(int level, boolean saveInSharedPrefs) {
		level = Math.min(Math.max(level, MIN_RELATIVE_LEVEL),
				MAX_RELATIVE_LEVEL);

		if (saveInSharedPrefs) {
			mPrefs.edit().putInt(RELATIVE_LEVEL, level).commit();
			putInt(BRIGHTNESS_MODE, level);
		}

	}

	public int getRelativeLevel() {
		return getInt(RELATIVE_LEVEL);
	}

	public void setBrightnessMode(int mode) {
		int prevMode = getBrightnessMode();

		if (prevMode != mode) {
			Settings.System.putInt(mContext.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
			putInt(BRIGHTNESS_MODE, mode);
		}

	}

	public int getBrightnessMode() {
		return getInt(BRIGHTNESS_MODE);
	}

	public void setServiceEnabled(boolean enabled) {
		putBoolean(SERVICE_ENABLED, enabled);
	}

	public boolean getServiceEnabled() {
		return getBoolean(SERVICE_ENABLED);
	}

	public void setBrightness(int brightness) {
		int prevBrightness = getBrightness();
		brightness = Math.min(Math.max(brightness, MIN_BRIGHTNESS),
				MAX_BRIGHTNESS);

		if (prevBrightness != brightness) {
			Settings.System.putInt(mContext.getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS, brightness);
			putInt(BRIGHTNESS, brightness);
		}

	}

	public int getBrightness() {
		return getInt(BRIGHTNESS);
	}

	public void setLux(float lux) {
		putFloat(LUX, lux);
	}

	public float getLux() {
		return getFloat(LUX);
	}

	// public void setSenseInterval(int intervalMs) {
	// putInt(SENSE_INTERVAL, intervalMs);
	// Log.d(mTag, "sense interval set to: " + intervalMs + " ms");
	// }

	public int getSenseInterval() {
		return getInt(SENSE_INTERVAL);
	}

	private void put(String key, Object value) {
		Log.d(mTag, "Putting: " + key);

		if (!value.equals(values.get(key))) {
			values.put(key, value);
			setChanged();
			notifyObservers(key);
		}
	}

	private void putBoolean(String key, Boolean value) {
		put(key, value);
	}

	private boolean getBoolean(String key) {
		return ((Boolean) values.get(key)).booleanValue();
	}

	private void putInt(String key, Integer value) {
		put(key, value);
	}

	private int getInt(String key) {
		return ((Integer) values.get(key)).intValue();
	}

	private void putFloat(String key, Float value) {
		put(key, value);
	}

	private float getFloat(String key) {
		return ((Float) values.get(key)).floatValue();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals(RELATIVE_LEVEL)) {
			putInt(key, prefs.getInt(key, 0));
		} else if (key.equals(SERVICE_ENABLED)) {
			putBoolean(key, prefs.getBoolean(key, false));
		} else if (key.equals(SENSE_INTERVAL)) {
			putInt(key, Integer.parseInt(prefs.getString(key, "5000")));
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
				putInt(BRIGHTNESS, Settings.System.getInt(
						mContext.getContentResolver(),
						Settings.System.SCREEN_BRIGHTNESS, 0));
			} else if ("content://settings/system/screen_brightness_mode"
					.equals(uri.toString())) {
				putInt(BRIGHTNESS_MODE, Settings.System.getInt(
						mContext.getContentResolver(),
						Settings.System.SCREEN_BRIGHTNESS_MODE, 0));
			}
		}

	}

	@Override
	public synchronized void deleteObserver(Observer observer) {
		super.deleteObserver(observer);

		if (countObservers() == 0) {
			onFinish();
		}
	}

	@Override
	public synchronized void deleteObservers() {
		super.deleteObservers();
		onFinish();
	}

	public void onFinish() {
		mPrefs.unregisterOnSharedPreferenceChangeListener(this);
		mContext.getContentResolver().unregisterContentObserver(
				mSettingsObserver);
	}

}
