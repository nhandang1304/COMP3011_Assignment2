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
@Timeout(150)
class EffectConcurrentTest {
    private static final int W = 96;
    private static final int H = 64;
    private static final int FRAMES = 64;
    private static final int ROUNDS = 5;
    private static final int THREADS = 8;

   
    void checkEffectThreadSafety(Supplier<? extends FrameProcessor> factory) throws Exception {
        Frame like = patterned(W, H);
        FrameProcessor shared = initialised(factory.get(), like, FRAMES, 25);
        List<byte[]> reference = new ArrayList<>();
        for (int n = 0; n < FRAMES; n++) {
            Frame f = patterned(W, H);
            shared.process(f, info(n));
            reference.add(snapshot(f));
        }
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
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
                List<Future<byte[]>> results = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
                for (int n = 0; n < FRAMES; n++) {
                    assertArrayEquals(reference.get(n), results.get(n).get(), "round " + round + " frame " + n);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
   
}