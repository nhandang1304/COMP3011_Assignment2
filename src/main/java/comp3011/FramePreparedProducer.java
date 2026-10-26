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
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;


import javafx.scene.image.Image;

public class FramePreparedProducer {
	private static final int WINDOW_SIZE = 8;
	 
	private record Pending(int generation, Future<PreparedFrame> future) {
	}
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
	private ExecutorService threadPool = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors(), runnable -> {
		Thread t = new Thread(runnable, "Worker of Frame");
		t.setDaemon(true);
		return t;});
	private BlockingQueue<PreparedFrame> frameQueue = new ArrayBlockingQueue<>(WINDOW_SIZE);
	private final Deque<Future<PreparedFrame>> slidingWindow = new ArrayDeque<>(8);
	private final BiConsumer<Long, Frame> queueAudio;
	private final Consumer<Exception> errorHandler;
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
	
	private void produceFrames() {
		
		
        int windowSize = 8;

        try {
            while (playbackOpen.getAsBoolean()) {
            	
            	synchronized (this) {
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

            	}
            	Future<PreparedFrame> oldestFuture = null;
                synchronized (this) {
                    if (!slidingWindow.isEmpty() && playbackOpen.getAsBoolean()) {
                        oldestFuture = slidingWindow.pollFirst();
                    }
                }

                if (oldestFuture != null) {
                    PreparedFrame prepared = oldestFuture.get();
                    if (prepared != null) {
                        frameQueue.put(prepared);
                    }
                }
            }
        } catch (InterruptedException e) {
            
            Thread.currentThread().interrupt();
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
    public void flush() {
        frameQueue.clear();
        for (Future<PreparedFrame> future : slidingWindow) {
            future.cancel(true); 
        }
        slidingWindow.clear();
    }
}