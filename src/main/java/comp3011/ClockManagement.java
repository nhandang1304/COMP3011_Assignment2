package comp3011;

public class ClockManagement {
	private static final long NO_SEEK_REQUEST = -1;
	private long firstTimestampUs = NO_SEEK_REQUEST;
	private long logicalPlaybackBaseUs;
	private long currentTimestampUs;
	private long playbackStartNs;
	private long pauseStartedNs;
	private long relativeSeekBaseUs = NO_SEEK_REQUEST;
	ClockManagement(){};
	
	private void resumePlaybackClock(long now) {
		if (pauseStartedNs > 0 && playbackStartNs > 0) {
			playbackStartNs += now - pauseStartedNs;
		}
		pauseStartedNs = 0;
	}
//	private void resetPlaybackClock(long logicalTimestampUs) {
//		pendingAudio.clear();
//		preparedFrame = null;
//		currentTimestampUs = logicalTimestampUs;
//		relativeSeekBaseUs = logicalTimestampUs;
//		firstTimestampUs = NO_SEEK_REQUEST;
//		logicalPlaybackBaseUs = logicalTimestampUs;
//		playbackStartNs = 0;
//		pauseStartedNs = pauseRequested ? System.nanoTime() : 0;
//	}
}
