package comp3011;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
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

/** This class manages multi-threaded frame production and effect processing pipeline for the video player.
It implements a robust Producer-Consumer concurrency architecture using a bounded sliding window 
and worker thread pool.**/

public class FramePreparedProducer {
	// Maximum number of pending frames allowed in the pipeline queue and sliding window.
	private static final int MAX_PENDING_FRAMES = 8;
	 
	// This record is created to track pending processing (frame) tasks and identify outdated results after seeking.
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
	
	// Thread pool processes video frames concurrently.
	// Number of threads in thread pool is based on the available CPU processors
	private ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), runnable -> {
		Thread t = new Thread(runnable, "Worker of Frame");
		t.setDaemon(true); // This allows the application to exit when all non-daemon threads have finished.
		return t;});
	
	// Uses a thread-safe blocking queue to pass prepared frames between worker threads and the model/view.
	// The fixed capacity prevents the queue from growing beyond MAX_PENDING_FRAMES.
	private BlockingQueue<PreparedFrame> frameQueue = new ArrayBlockingQueue<>(MAX_PENDING_FRAMES);
	
	// Deque helps add tasks at the end and retrieve them from the front, 
	// so frames can be processed in order while multiple tasks are running asynchronously.
	private final Deque<Pending> slidingWindow = new ArrayDeque<>(MAX_PENDING_FRAMES);
	private final BiConsumer<Long, Frame> queueAudio;
	private final Consumer<Exception> errorHandler;
	
	// Synchronization locks and atomic state trackers.
	private final Object windowLock = new Object();
	private final Object deliveryLock = new Object();
	private final ReentrantLock grabLock = new ReentrantLock();
	private final AtomicInteger processingVersion = new AtomicInteger();
	private final AtomicInteger framePermits = new AtomicInteger();
	
	private final Thread producerThread;
	private volatile boolean endOfStream; 
	private volatile boolean drained; 
	
	// Constructor of FramePreparedProducer with all necessary playback resources, 
	// dependencies, and callback handlers.
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
	    
		// Initialize and configure the background video producer thread
	    this.producerThread = new Thread(this::produceFrames, "Video Producer Thread");
        this.producerThread.setDaemon(true);

	}
	// This method starts the background producer thread to begin fetching and processing frames.
	public void start() {
		producerThread.start();
	}
	
	// This method retrieves and removes the next prepared frame from the delivery queue (ready for display, or null if empty).
	public PreparedFrame pollFrame() {
		return frameQueue.poll();
	}
	
	// This method checks whether all frames have been fully processed and the stream is completely drained.
	public boolean isFinished() {
		return drained && frameQueue.isEmpty();
	}
	
	// This method grabs the next frame from the FFmpeg grabber.
	private Frame grabFrame() throws Exception {
		if (audioAvailable) {
			return grabber.grab();
		}
		return grabber.grabImage();
	}
	
	// This method returns the current size of the sliding window safely under synchronization.
	private int windowSize() {
		synchronized (windowLock) {
			return slidingWindow.size();
		}
	}
	
	// This method marks the stream as fully drained if the end of stream has been reached 
	// and all pending background tasks have finished.
	private void markDrainedIfStillAtEnd() {
		// Lock to safely check the producer's state
		grabLock.lock();
		try {	
		if (endOfStream && windowSize() == 0) {
				drained = true;
			}
		} finally{
			// Releases the lock even if an exception occurs.
			grabLock.unlock();
		}
	}
	// This method grants permission for one frame to be processed while paused.
	public void allowFrameWhilePaused() {
		framePermits.set(1);
	}
	
	// This method performs a thread-safe seek operation by cancelling outdated tasks,
	// clearing pending frames, and resetting playback state.
	public void seek(long grabTimestampUs, boolean showFrameWhilePaused, Runnable resetState) throws Exception {
		
		// Increments the version so results from previous processing tasks can be identified as outdated.
		processingVersion.incrementAndGet(); 
		grabLock.lock();
		try {
			
			grabber.setTimestamp(grabTimestampUs);
			
			// Removes frames that were prepared before seeking.
			synchronized (deliveryLock) {
				frameQueue.clear();
			}
			synchronized (windowLock) {
				
				// Cancels outstanding tasks so outdated frames are not unnecessarily processed
				for (Pending p : slidingWindow) {
					p.future().cancel(true);
				}
				// Removes all pending tasks from the sliding window.
				slidingWindow.clear();
			}
			endOfStream = false;
			drained = false;
			
			// Allows one preview frame if requested.
			// Otherwise, disables frame production while paused.
			if (showFrameWhilePaused) {
			    framePermits.set(1);
			} else {
			    framePermits.set(0);
			}
			resetState.run();
		} finally {
			grabLock.unlock();
		}
	}
	// This initializes all frame processing effect filters with video metadata
	private void initialiseFrameProcessors(InfoVideo videoInfo) throws Exception {
		for (FrameProcessor processor : frameProcessors) {
			processor.initialise(videoInfo);
		}
	}
	
	// This method applies all frame processors to the given frame
	private void processFrame(Frame frame, InfoFrame info) throws Exception {
		for (FrameProcessor processor : frameProcessors) {
			
			processor.process(frame, info);
		}
	}
	
	// This method is main loop executed by the producer thread, managing frame grabbing, 
	// background task submission, and head delivery.
	private void produceFrames() {
		try {
			while (playbackOpen.getAsBoolean()) {
				// Delivers the next frame if the oldest pending task is ready.
				if (deliverHead(false)) {
					continue;
				}
				
				// Checks whether playback is paused and no preview frame is permitted
				boolean paused = pauseRequested.getAsBoolean() && framePermits.get() == 0;
				
				// Grabs and submits new frames when playback is active,
	            // the video has not ended, and the processing window has space.
				if (!paused && !endOfStream && windowSize() < MAX_PENDING_FRAMES) {
					grabAndSubmitFrames();
					continue;
				}
				// If tasks remain pending, waits for the oldest task to finish
	            // and deliver its frame.
				if (windowSize() > 0) {
					deliverHead(true);
					continue;
				}
				 // Marks the producer as drained when the video has ended
				if (endOfStream) {
					markDrainedIfStillAtEnd();
				}
				// Briefly waits before checking again to avoid continuously
	            // running the loop when no work is available.
				Thread.sleep(2);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			if (playbackOpen.getAsBoolean()) {
				errorHandler.accept(e);
			}
		} finally {
			// Stops worker threads when the producer loop exits.
			threadPool.shutdownNow();
		}
	}
	
	// This method grab a raw frame from the decoder and submits frame processing
	// and image conversion tasks to the worker thread pool.
	private void grabAndSubmitFrames() throws Exception {
		 // Locks frame grabbing to prevent conflicts with seeking or other operations
		grabLock.lock();
        try {
        	// Stops grabbing if playback is closed or the video has ended
        	if (!playbackOpen.getAsBoolean() || endOfStream) {
				return;
			}
        	// Prevents grabbing frames while paused unless a preview frame is allowed
			if (pauseRequested.getAsBoolean() && framePermits.get() == 0) {
				return;
			}
			Frame frame = grabFrame();
			
			// Marks the end of the video if no frame is returned.
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
            	InfoVideo videoInfo = new InfoVideo(mediaName, videoFrameInfo.totalVideoFrames(), frame.imageWidth,
						frame.imageHeight, frame.imageDepth, frame.imageChannels, frame.imageStride,
						videoFrameInfo.frameRate(), videoFrameInfo.intFrameRate(),
						videoFrameInfo.videoFrameDurationUs(), grabber.getPixelFormat());
            	initialiseFrameProcessors(videoInfo);
                frameProcessorsInitialised = true;
            }
         // Sets the initial playback timestamp and clock when no seek is pending.
            if (clock.getFirstTimestampUs() == ClockManagement.NO_SEEK_REQUEST) {
                clock.setFirstTimestampUs(timestampUs);
                clock.setPlaybackStartNs(System.nanoTime());
             // Records the pause start time if playback is currently paused.
                if (pauseRequested.getAsBoolean()) {
                    clock.setPauseStartedNs(clock.getPlaybackStartNs());
                }
            }
        
            // Calculates the frame timestamp to the first frame.
            long relativeUs = Math.max(0, timestampUs - clock.getFirstTimestampUs());
			long logicalUs = clock.getLogicalPlaybackBaseUs() + relativeUs;
			
			// Clones the frame so background processing does not modify the decoder's frame.
			Frame clonedFrame = frame.clone();
			int gen = processingVersion.get();
			
			// Submits frame processing and image conversion asynchronously to a worker thread
			Future<PreparedFrame> future = threadPool.submit(() -> {
            InfoFrame info = new InfoFrame(frameNumber, timestampUs);
            processFrame(clonedFrame, info);

            Image image;
         // Synchronises access to the converter to prevent concurrent conversion conflicts.
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
			// Consumes one permit to display a preview frame while playback is paused, if a permit is available.
			framePermits.updateAndGet(p -> Math.max(0, p - 1));}
        finally {
        	grabLock.unlock();
        }
        
    }
	
	// This method shuts down the producer thread and worker thread pool
	public void shutdown() {
		if (producerThread != null) {
			producerThread.interrupt();
		}
		if (threadPool != null ) {
			threadPool.shutdownNow();
		}
	}
	// This method offers a successfully prepared frame to the output queue
	private void offerFrame(int frameGeneration, PreparedFrame frame) throws InterruptedException {
		while (playbackOpen.getAsBoolean()) {
			synchronized (deliveryLock) {
				if (frameGeneration != processingVersion.get()) {
					return; // Discard stale frames from previous seek generations.
				}
				if (frameQueue.offer(frame)) {
					return;
				}
			}
			Thread.sleep(2); 
		}
	}
	// This method processes the head of the sliding window, 
	// awaiting its future result and pushing it to the delivery queue.
	private boolean deliverHead(boolean wait) throws Exception {
		Pending head;
		synchronized (windowLock) {
			head = slidingWindow.peekFirst();
			if (head == null || (!wait && !head.future().isDone())) {
				return false;
			}
			// Removes the task from the sliding window for result retrieval
			slidingWindow.pollFirst();
		}
 
		PreparedFrame prepared;
		try {
			prepared = head.future().get();
		} catch (CancellationException e) {
			return true; 
		} catch (ExecutionException e) {
			if (head.generation() != processingVersion.get()) {
				return true; 
			}
			if (e.getCause() instanceof Exception) {
			    throw (Exception) e.getCause();
			} else {
			    throw e;
			}
		}
		// Delivers the prepared frame if a valid result was returned.
		if (prepared != null) {
			offerFrame(head.generation(), prepared);
		}
		return true;
	}

}