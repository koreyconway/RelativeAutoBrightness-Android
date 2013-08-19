package org.sgnexus.relativeautobright;

class DefaultStrategy extends AutoBrightnessStrategy {

	// static private int THRESHOLD = 2;
	static private int MAX_LUX = 1000;

	DefaultStrategy() {
	}

	@Override
	int computeBrightness(Data data) {
		// int prevBrightness = data.getBrightness();
		float lux = data.getLux();
		int relativeLevel = data.getRelativeLevel();
		// int level = (int) (8 * lux / 10 + 12 * relativeLevel / 10);
		// int newBrightness = 0;

		if (lux < 10) {
			if (relativeLevel < 20) {
				return 0;
			} else {
				return relativeLevel / 2;
			}
		} else if (lux > MAX_LUX) {
			return 255;
		} else {
			return 70 + (relativeLevel / 2);
		}

		// newBrightness = (int) (200 * (Math.log(relativeLevel + 1) - Math
		// .log(lux + 1 + relativeLevel)));

		// newBrightness = level * Data.MAX_BRIGHTNESS / MAX_LUX;
		// newBrightness = Math.min(Math.max(newBrightness, MIN), MAX);
		//
		// // Try to keep the lowest brightness
		// if ((newBrightness < prevBrightness)
		// || ((newBrightness - prevBrightness) > THRESHOLD)) {
		// return newBrightness;
		// } else {
		// return prevBrightness;
		// }
	}

}
