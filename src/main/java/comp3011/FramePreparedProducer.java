package comp3011;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
	private BlockingQueue<PreparedFrame> frameQueue = new ArrayBlockingQueue<>(10);
	private final BiConsumer<Long, Frame> queueAudio;
	private final Thread producerThread;
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
	    
	    this.producerThread = new Thread(this::produceFrames, "Video-Producer-Thread");
        this.producerThread.setDaemon(true);
        this.producerThread.start();
	    
	   
	}
	public void clearQueue() {
        frameQueue.clear();
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
	private synchronized void produceFrames() {
        int windowSize = 10;
        Deque<Future<PreparedFrame>> slidingWindow = new ArrayDeque<>(windowSize);

        try {
            while (playbackOpen.getAsBoolean()) {
                
                while (slidingWindow.size() < windowSize && playbackOpen.getAsBoolean()) {
                    Frame frame = grabFrame();
                    if (frame == null) {
                        finishPlayback.run();
                        break;
                    }
                    long timestampUs = grabber.getTimestamp();
                    int frameNumber = grabber.getFrameNumber();

                    if (audioAvailable && frame.samples != null) {
                        queueAudio.accept(timestampUs, frame);
                    }
                    if (frame.image == null|| frame.imageWidth <= 0 || frame.imageHeight <= 0) {
                        continue;
                    }

                    synchronized (this) {
                        if (!frameProcessorsInitialised) {
                            initialiseFrameProcessors(new InfoVideo(
                                    mediaName, videoFrameInfo.totalVideoFrames(), frame.imageWidth,
                                    frame.imageHeight, frame.imageDepth, frame.imageChannels, frame.imageStride, 
                                    videoFrameInfo.frameRate(), videoFrameInfo.intFrameRate(), 
                                    videoFrameInfo.videoFrameDurationUs(), grabber.getPixelFormat()
                            ));
                            frameProcessorsInitialised = true;
                        }

                        if (clock.getFirstTimestampUs() == ClockManagement.NO_SEEK_REQUEST) {
                            clock.setFirstTimestampUs(timestampUs);
                            clock.setPlaybackStartNs(System.nanoTime());
                            if (pauseRequested.getAsBoolean()) {
                                clock.setPauseStartedNs(clock.getPlaybackStartNs());
                            }
                        }
                    }

                    long relativeTimestampUs = Math.max(0, timestampUs - clock.getFirstTimestampUs());
                    Frame clonedFrame = frame.clone(); 

                    Future<PreparedFrame> future = threadPool.submit(() -> {
                        InfoFrame info = new InfoFrame(frameNumber, timestampUs);
                        processFrame(clonedFrame, info);

                        Image image = converter.convert(clonedFrame);
                        long logicalTimestampUs = clock.getLogicalPlaybackBaseUs() + relativeTimestampUs;
                        long targetTimeNs = clock.getPlaybackStartNs() + (relativeTimestampUs * 1000L);

                        return new PreparedFrame(
                                image, 
                                frameNumber, 
                                timestampUs, 
                                logicalTimestampUs, 
                                targetTimeNs, 
                                System.nanoTime()
                        );
                    });

                    slidingWindow.addLast(future);
                }

               
                if (!slidingWindow.isEmpty() && playbackOpen.getAsBoolean()) {
                    Future<PreparedFrame> oldestFuture = slidingWindow.pollFirst();
                    if (oldestFuture != null) {
                        PreparedFrame prepared = oldestFuture.get();
                        if (prepared != null) {
                            frameQueue.put(prepared);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdownNow();
        }
    }
	public void shutdown() {
		if (producerThread != null) {
			producerThread.interrupt();
		}
		if (threadPool != null ) {
			threadPool.shutdownNow();
		}
	}
    public BlockingQueue<PreparedFrame> readNextVideoFrame() {
        return frameQueue;
    }
}