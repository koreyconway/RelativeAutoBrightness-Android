package org.sgnexus.relativeautobright;

abstract class AutoBrightnessStrategy {
	final int MIN = 0;
	final int MAX = 255;

	abstract int computeBrightness(Data data);
}
