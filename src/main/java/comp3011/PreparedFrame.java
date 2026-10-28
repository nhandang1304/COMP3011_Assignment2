package comp3011;

import javafx.scene.image.Image;


public record PreparedFrame(Image image, int frameNumber, long mediaTimestampUs, long logicalTimestampUs,
			long relativeTimestampUs, long preparedAtNs) {
	}

