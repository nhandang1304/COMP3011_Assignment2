  package comp3011;

/**Stores basic information about the video's frame
   rate and duration
 **/
public record VideoFrameInfo (int videoFrameDurationUs, double frameRate, int intFrameRate, int totalVideoFrames)
{
	public static final VideoFrameInfo UNKNOWN = new VideoFrameInfo(0, 0, 0, 0);
	
	public static VideoFrameInfo of(int totalVideoFrames, double frameRate) {
        int intFrameRate = (int) Math.round(frameRate);
        int durationUs = frameRate > 0 ? (int) Math.round(1_000_000.0 / frameRate) : 0;
        return new VideoFrameInfo(durationUs, frameRate, intFrameRate, totalVideoFrames);
    }
	}	
	
	

