package comp3011;

import java.lang.reflect.Array;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;


import javafx.scene.image.Image;

public class FramePreparedProducer {
	private PreparedFrame preparedFrame;
	private boolean frameProcessorsInitialised;
	private JavaFXFrameConverter converter;
	private FFmpegFrameGrabber grabber;
	private VideoFrameInfo videoFrameInfo;
	private final ClockManagement clock;
	private final List<FrameProcessor> frameProcessors;
	private boolean audioAvailable;
	private BooleanSupplier pauseRequested;
	private BooleanSupplier playbackOpen;
	private String mediaName;
	private final Runnable finishPlayback;
	private ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	BlockingQueue<Frame> frameQueue = new ArrayBlockingQueue<>(8);
	private final BiConsumer<Long, Frame> queueAudio;
	public FramePreparedProducer(String mediaName, JavaFXFrameConverter converter, FFmpegFrameGrabber grabber,  VideoFrameInfo videoFrameInfo, 
								ClockManagement clock, List<FrameProcessor> frameProcessors, BooleanSupplier pauseRequested, boolean audioAvailable, 
								BooleanSupplier playbackOpen, Runnable finishPlayback, BiConsumer<Long, Frame> queueAudio){
		this.converter = converter;
		this.grabber = grabber;
		this.clock = clock;
		this.videoFrameInfo = videoFrameInfo;
		this.frameProcessors = frameProcessors;
		this.audioAvailable = audioAvailable;
		this.pauseRequested = pauseRequested;
		this.playbackOpen = playbackOpen;
		this.mediaName = mediaName;
		this.finishPlayback = finishPlayback;
	    this.queueAudio = queueAudio;
	    
	    
	    
	   
	}
	
	private Frame grabFrame() throws Exception {
		if (audioAvailable) {
			return grabber.grab();
		}
		return grabber.grabImage();
	}
//	private void prepareNextFrame() {
//		if (!playbackOpen.getAsBoolean() || preparedFrame != null) {
//			return;
//		}
//
//		try {
//			preparedFrame = readNextVideoFrame();
//		} catch (Exception e) {
//			handlePlaybackError(e);
//		}
//	}
	private void initialiseFrameProcessors(InfoVideo videoInfo) throws Exception {
		for (FrameProcessor processor : frameProcessors) {
			processor.initialise(videoInfo);
		}
	}
	private void processFrame(Frame frame, InfoFrame info) throws Exception {
		for (FrameProcessor processor : frameProcessors) {
			
			processor.process(frame, info);
		}
	}
	public PreparedFrame readNextVideoFrame() throws Exception {
		 Future<Frame>[] frameList = new Future[8];
		while (playbackOpen.getAsBoolean()) {
			for (int i = 0; i< frameList.length; i++) {
				
				frameList[i] = threadPool.submit(() -> {
				Frame frame = grabFrame();
				if (frame == null) {
					finishPlayback.run();
					return null;
				}

				long timestampUs = grabber.getTimestamp();
				if (audioAvailable && frame.samples != null) {
					queueAudio.accept(timestampUs, frame);
				}

				if (frame.image == null) {
					continue;
				}
				frameQueue.put(frame);
	}
			}
			
			
			
					);
			
			if (!frameProcessorsInitialised) {
				initialiseFrameProcessors(new InfoVideo(mediaName, videoFrameInfo.totalVideoFrames(), frame.imageWidth,
						frame.imageHeight, frame.imageDepth, frame.imageChannels, frame.imageStride, videoFrameInfo.frameRate(),
						videoFrameInfo.intFrameRate(), videoFrameInfo.videoFrameDurationUs(), grabber.getPixelFormat()));
				frameProcessorsInitialised = true;
			}

			if (clock.getFirstTimestampUs() == ClockManagement.NO_SEEK_REQUEST) {
				clock.setFirstTimestampUs(timestampUs);
				clock.setPlaybackStartNs(System.nanoTime()); 
				if (pauseRequested.getAsBoolean()) {
					clock.setPauseStartedNs(clock.getPlaybackStartNs());
				}
			}

			int frameNumber = grabber.getFrameNumber();
			InfoFrame info = new InfoFrame(frameNumber, timestampUs);
			processFrame(frame, info);

			Image image = converter.convert(frame);
			long relativeTimestampUs = Math.max(0, timestampUs - clock.getFirstTimestampUs());
			long logicalTimestampUs = clock.getLogicalPlaybackBaseUs() + relativeTimestampUs;
			long targetTimeNs = clock.getPlaybackStartNs() + relativeTimestampUs * 1_000L;

			return new PreparedFrame(image, frameNumber, timestampUs, logicalTimestampUs, targetTimeNs,
					System.nanoTime());
		}

		return null;
	}
}
