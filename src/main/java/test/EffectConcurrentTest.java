package test;
import static test.FrameForTesting.info;
import static test.FrameForTesting.initialised;
import static test.FrameForTesting.patterned;
import static test.FrameForTesting.snapshot;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import comp3011.FrameBlackAndWhiter;
import comp3011.FrameBleeder;
import comp3011.FrameDuster;
import comp3011.FrameFlickerer;
import comp3011.FrameJitterer;
import comp3011.FrameNumberer;
import comp3011.FramePepperer;
import comp3011.FrameProcessor;
import comp3011.FrameScratcher;
import comp3011.FrameVignetter;
import comp3011.FrameYellower;

/**
Effects are shared by the worker pool that one instance of each effect processes many frames at the same time.
For every effect, concurrent output must be byte-identical to
single-threaded output. A shared scratch buffer (as in FrameBleeder and FrameJitterer) that loses its lock would show up here as corrupted frames. Races are probabilistic, so each effect is hammered several rounds.
*/

class EffectConcurrentTest {
    private static final int W = 96;
    private static final int H = 64;
    private static final int FRAMES = 64;
    private static final int ROUNDS = 5;
    private static final int THREADS = 8;
    
    // Checks whether one shared effect instance produces identical results
    // when frames are processed sequentially and concurrently
    void checkEffectThreadSafety(Supplier<? extends FrameProcessor> factory) throws Exception {
        Frame like = patterned(W, H);
        FrameProcessor shared = initialised(factory.get(), like, FRAMES, 25);
        
     // Stores the expected output of each frame when processed sequentially.
        List<byte[]> reference = new ArrayList<>();
     // Processes frames one at a time
        for (int n = 0; n < FRAMES; n++) {
            Frame f = patterned(W, H);
            shared.process(f, info(n));
            reference.add(snapshot(f));
        }
     // Creates a fixed-size thread pool
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
        	 // Repeats the concurrent test to increase the chance of detecting race conditions
            for (int round = 0; round < ROUNDS; round++) {
                List<Callable<byte[]>> tasks = new ArrayList<>();
                for (int n = 0; n < FRAMES; n++) {
                    int frameNumber = n;
                    tasks.add(() -> {
                        Frame f = patterned(W, H);
                        shared.process(f, info(frameNumber));
                        return snapshot(f);
                    });
                }
             // Runs all tasks concurrently and waits up to 60 seconds for completion
                List<Future<byte[]>> results = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
             // Compares each concurrent result with its sequential reference output
                for (int n = 0; n < FRAMES; n++) {
                    assertArrayEquals(reference.get(n), results.get(n).get(), "round " + round + " frame " + n);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
    
    /** These tests checks whether some effects are safe to use across multiple threads**/
    @Test 
    void numbererIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameNumberer::new); }
    @Test 
    void scratcherIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameScratcher::new); }
    @Test 
    void flickererIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameFlickerer::new); }
    @Test 
    void blackAndWhiterIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameBlackAndWhiter::new); }
    @Test 
    void yellowerIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameYellower::new); }
    @Test 
    void vignetterIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameVignetter::new); }
    @Test 
    void dusterIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameDuster::new); }
    @Test 
    void peppererIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FramePepperer::new); }
    @Test 
    void jittererIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameJitterer::new); }
    @Test 
    void bleederIsThreadSafe() throws Exception { 
    	checkEffectThreadSafety(FrameBleeder::new); }
   
}