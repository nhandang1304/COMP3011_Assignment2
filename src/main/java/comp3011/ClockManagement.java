package comp3011;

public class ClockManagement {
	
	private long firstTimestampUs;
	private long logicalPlaybackBaseUs;
	private long currentTimestampUs;
	private long playbackStartNs;
	private long pauseStartedNs;
	private long relativeSeekBaseUs;
	ClockManagement(long noSeekRequest){
		this.firstTimestampUs = noSeekRequest;
		this.relativeSeekBaseUs = noSeekRequest;
	}
	
	public void resumePlaybackClock(long now) {
		if (pauseStartedNs > 0 && playbackStartNs > 0) {
			playbackStartNs += now - pauseStartedNs;
		}
		pauseStartedNs = 0;
	}
	public void resetPlaybackClock(long logicalTimestampUs, long noSeekRequest) {
		currentTimestampUs = logicalTimestampUs;
		relativeSeekBaseUs = logicalTimestampUs;
		firstTimestampUs = noSeekRequest;
		logicalPlaybackBaseUs = logicalTimestampUs;
		playbackStartNs = 0;
		
	}
}
