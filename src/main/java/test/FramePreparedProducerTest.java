package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import comp3011.ClockManagement;
import comp3011.FramePreparedProducer;
import comp3011.FrameProcessor;
import comp3011.InfoFrame;
import comp3011.VideoFrameInfo;
import javafx.scene.image.Image;


class FramePreparedProducerTest {
    private static final int W = 16;
    private static final int H = 8;
    private static final int US_PER_FRAME = 40_000;
    static final class FakeGrabber extends FFmpegFrameGrabber {
        final int total;
        private final Frame template;
        private volatile int next;
        private volatile int lastDelivered = -1;
        FakeGrabber(int total) {
            this(total, W, H);
        }
        FakeGrabber(int total, int width, int height) {
            super("fakeVideo.mp4");
            this.total = total;
            this.template = FrameForTesting.patterned(width, height);
        }
        @Override
        public Frame grabImage() {
            return nextFrame();
        }
        @Override
        public Frame grab() {
            return nextFrame();
        }
        private Frame nextFrame() {
            if (next >= total) {
                return null;
            }
            lastDelivered = next++;
            return template;
        }
        @Override
        public long getTimestamp() {
            return (long) lastDelivered * US_PER_FRAME;
        }
        @Override
        public int getFrameNumber() {
            return lastDelivered;
        }
        @Override
        public int getPixelFormat() {
            return 0;
        }
        @Override
        public void setTimestamp(long timestamp) {
            next = (int) (timestamp / US_PER_FRAME);
        }
    }
}