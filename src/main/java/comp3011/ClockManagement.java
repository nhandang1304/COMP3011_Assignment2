package comp3011;
import java.util.concurrent.atomic.AtomicLong;
public class ClockManagement {
	static final long NO_SEEK_REQUEST = -1;
	private final AtomicLong firstTimestampUs = new AtomicLong(NO_SEEK_REQUEST);
	private final AtomicLong logicalPlaybackBaseUs = new AtomicLong(0);
	private final AtomicLong currentTimestampUs = new AtomicLong(0);
	private final AtomicLong playbackStartNs = new AtomicLong(0);
	private final AtomicLong pauseStartedNs = new AtomicLong(0);
	private final AtomicLong relativeSeekBaseUs = new AtomicLong(NO_SEEK_REQUEST);
	
	public void resumePlaybackClock(long now) {
		
		long pauseStarted = pauseStartedNs.get();
		long playbackStart = playbackStartNs.get();

		if (pauseStarted > 0 && playbackStart > 0) {
			playbackStartNs.addAndGet(now - pauseStarted);
		}
		pauseStartedNs.set(0L);
	}
	public void resetPlaybackClock(boolean pauseRequested, long logicalTimestampUs, long noSeekRequest) {
		currentTimestampUs.set(logicalTimestampUs);
		relativeSeekBaseUs.set(logicalTimestampUs);
		firstTimestampUs.set(noSeekRequest);
		logicalPlaybackBaseUs.set(logicalTimestampUs);
		playbackStartNs.set(0L);
		pauseStartedNs.set(pauseRequested ? System.nanoTime() : 0L);
	}
	public void setFirstTimestampUs(long timestamp) {
		firstTimestampUs.set(timestamp);
	}
	public void setLogicalPlaybackBaseUs(long timestamp) {
		logicalPlaybackBaseUs.set(timestamp);
	}
	public void setCurrentTimestampUs(long timestamp) {
		currentTimestampUs.set(timestamp);
	}
	public void setPlaybackStartNs(long timestamp) {
		playbackStartNs.set(timestamp);
	}
	public void setPauseStartedNs(long timestamp) {
		pauseStartedNs.set(timestamp);
	}
	public void setRelativeSeekBaseUs(long timestamp) {
		relativeSeekBaseUs.set(timestamp);
	}
	
	public long getFirstTimestampUs() {
		return firstTimestampUs.get();
	}
	public long getLogicalPlaybackBaseUs() {
		return logicalPlaybackBaseUs.get();
	}
	public long getCurrentTimestampUs() {
		return currentTimestampUs.get();
	}
	public long getPlaybackStartNs() {
		return playbackStartNs.get();
	}
	public long getPauseStartedNs() {
		return pauseStartedNs.get();
	}
	public long getRelativeSeekBaseUs() {
		return relativeSeekBaseUs.get();
	}
}
