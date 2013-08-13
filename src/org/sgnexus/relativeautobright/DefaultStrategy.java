package org.sgnexus.relativeautobright;

public class DefaultStrategy extends AutoBrightnessStrategy {

	public DefaultStrategy() {
	}

	@Override
	int computeBrightness(Data data) {
		return (int) ((data.getLux() / 30) + ((data.getRelativeLevel() - 50) * 3 / 7));
	}

}
