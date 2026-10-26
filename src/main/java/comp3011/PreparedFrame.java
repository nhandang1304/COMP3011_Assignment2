package comp3011;

import javafx.scene.image.Image;


record PreparedFrame(Image image, int frameNumber, long mediaTimestampUs, long logicalTimestampUs,
			long relativeTimestampUs, long preparedAtNs) {
	}

