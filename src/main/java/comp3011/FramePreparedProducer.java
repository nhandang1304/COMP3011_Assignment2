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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;


import javafx.scene.image.Image;

public class FramePreparedProducer {
	private static final int MAX_PENDING_FRAMES = 8;
	 
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

	private ExecutorService threadPool = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors(), runnable -> {
		Thread t = new Thread(runnable, "Worker of Frame");
		t.setDaemon(true);
		return t;});
	private BlockingQueue<PreparedFrame> frameQueue = new ArrayBlockingQueue<>(MAX_PENDING_FRAMES);
	private final Deque<Future<Pending>> slidingWindow = new ArrayDeque<>(MAX_PENDING_FRAMES);
	private final BiConsumer<Long, Frame> queueAudio;
	private final Consumer<Exception> errorHandler;
	
	private final Object windowLock = new Object();
	private final Object deliveryLock = new Object();
	private final ReentrantLock grabLock = new ReentrantLock();
	private final AtomicInteger processingVersion = new AtomicInteger();
	private final AtomicInteger framePermits = new AtomicInteger();
	
	private final Thread producerThread;
	private volatile boolean endOfStream; 
	private volatile boolean drained; 
	
	public FramePreparedProducer(String mediaName, JavaFXFrameConverter converter, FFmpegFrameGrabber grabber,  VideoFrameInfo videoFrameInfo, 
								ClockManagement clock, List<FrameProcessor> frameProcessors, BooleanSupplier pauseRequested, boolean audioAvailable, 
								BooleanSupplier playbackOpen, BiConsumer<Long, Frame> queueAudio, Consumer<Exception> errorHandler){
		this.converter = converter;
		this.grabber = grabber;
		this.clock = clock;
		this.videoFrameInfo = videoFrameInfo;
		this.frameProcessors = frameProcessors;
		this.audioAvailable = audioAvailable;
		this.pauseRequested = pauseRequested;
		this.playbackOpen = playbackOpen;
		this.mediaName = mediaName;

	    this.queueAudio = queueAudio;
		this.errorHandler = errorHandler;
	    
	    this.producerThread = new Thread(this::produceFrames, "Video-Producer-Thread");
        this.producerThread.setDaemon(true);
        
        
	   
	}
	
	public void start() {
		producerThread.start();
	}
	public PreparedFrame pollFrame() {
		return frameQueue.poll();
	}
	public boolean isFinished() {
		return drained && frameQueue.isEmpty();
	}
	private Frame grabFrame() throws Exception {
		if (audioAvailable) {
			return grabber.grab();
		}
		return grabber.grabImage();
	}
	
	private int windowSize() {
		synchronized (windowLock) {
			return slidingWindow.size();
		}
	}
	private void markDrainedIfStillAtEnd() {
		grabLock.lock();
		try {	
		if (endOfStream && windowSize() == 0) {
				drained = true;
			}
		} finally{
			grabLock.unlock();
		}
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

        try {
        	if (!playbackOpen.getAsBoolean() || endOfStream) {
				return;
			}
			if (pauseRequested.getAsBoolean() && framePermits.get() == 0) {
				return;
			}
			Frame frame = grabFrame();
            if (frame == null) {
            	endOfStream = true;
            	return;
             
            }
	
            long timestampUs = grabber.getTimestamp();
            int frameNumber = grabber.getFrameNumber();

            if (audioAvailable && frame.samples != null) {
                queueAudio.accept(timestampUs, frame);
            }
            if (frame.image == null|| frame.imageWidth <= 0 || frame.imageHeight <= 0) {
                return;
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
        

            long relativeUs = Math.max(0, timestampUs - clock.getFirstTimestampUs());
			long logicalUs = clock.getLogicalPlaybackBaseUs() + relativeUs;
			Frame clonedFrame = frame.clone();
			int gen = processingVersion.get();

			Future<PreparedFrame> future = threadPool.submit(() -> {
            InfoFrame info = new InfoFrame(frameNumber, timestampUs);
            processFrame(clonedFrame, info);

            Image image;
            synchronized(converter) {
            	image = converter.convert(clonedFrame);
            }
           
            return new PreparedFrame(
                    image, 
                    frameNumber, 
                    timestampUs, 
                    logicalUs, 
                    relativeUs, 
                    System.nanoTime()
            );
        });
			synchronized (windowLock) {
				slidingWindow.addLast(new Pending(gen, future));
			}
			framePermits.updateAndGet(p -> Math.max(0, p - 1));}
        finally {
        	grabLock.unlock();
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