package comp3011;

public record VideoMetadata (int videoFrameDurationUs, double frameRate, int intFrameRate, int totalVideoFrames)
	
{
	public static final VideoMetadata UNKNOWN = new VideoMetadata(0, 0, 0, 0);
	
	public static VideoMetadata of(int totalVideoFrames, double frameRate) {
        int intRate = (int) Math.round(frameRate);
        int durationUs = frameRate > 0 ? (int) Math.round(1_000_000.0 / frameRate) : 0;
        return new VideoMetadata(totalVideoFrames, frameRate, intRate, durationUs);
    }
	}	
	
	

