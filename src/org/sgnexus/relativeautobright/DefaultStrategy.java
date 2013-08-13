package org.sgnexus.relativeautobright;

class DefaultStrategy extends AutoBrightnessStrategy {

	static private int THRESHOLD = 10;

	DefaultStrategy() {
	}

	@Override
	int computeBrightness(Data data) {
		int prevBrightness = data.getBrightness();
		int newBrightness = (int) ((data.getLux() / 30) + ((data
				.getRelativeLevel() - 50) * 3 / 7));

		// Try to keep the lowest brightness
		if ((newBrightness < prevBrightness)
				|| ((newBrightness - prevBrightness) > THRESHOLD)) {
			return newBrightness;
		} else {
			return prevBrightness;
		}
	}

}
