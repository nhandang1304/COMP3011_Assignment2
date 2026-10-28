package test;

import java.nio.ByteBuffer;

import org.bytedeco.javacv.Frame;

import comp3011.FrameProcessor;
import comp3011.InfoFrame;
import comp3011.InfoVideo;

/**Provides helper methods for creating and manipulating test frames.
 AI assistance: OpenAI ChatGPT was used to help explain
 Java methods and improve code for developing this class.
 **/
final class FrameForTesting {
    static final String MEDIA_NAME = "test video.mp4";

    private FrameForTesting() {
    }
    // // Creates a frame where every pixel channel has the same value
    static Frame filled(int width, int height, int channels, int value) {
        Frame frame = new Frame(width, height, Frame.DEPTH_UBYTE, channels);
        ByteBuffer pixels = pixels(frame);
        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(i, (byte) value);
        }
        return frame;
    }

    static ByteBuffer pixels(Frame frame) {
        return (ByteBuffer) frame.image[0];
    }

    static int get(Frame frame, int x, int y, int channel) {
        return Byte.toUnsignedInt(pixels(frame).get(y * frame.imageStride + x * frame.imageChannels + channel));
    }
 // Sets the blue, green and red channel values of one pixel
    static void setBgr(Frame frame, int x, int y, int b, int g, int r) {
        int base = y * frame.imageStride + x * frame.imageChannels;
        pixels(frame).put(base, (byte) b);
        pixels(frame).put(base + 1, (byte) g);
        pixels(frame).put(base + 2, (byte) r);
    }
 // Creates a copy of the frame's pixel data
    static byte[] snapshot(Frame frame) {
        ByteBuffer pixels = pixels(frame);
        byte[] copy = new byte[pixels.capacity()];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = pixels.get(i);
        }
        return copy;
    }
    // Creates video metadata for the given frame
    static InfoVideo videoInfo(Frame frame, int totalFrames, double fps) {
        return new InfoVideo(MEDIA_NAME, totalFrames, frame.imageWidth, frame.imageHeight, frame.imageDepth,
                frame.imageChannels, frame.imageStride, fps, (int) Math.round(fps),
                (int) Math.round(1_000_000.0 / fps), 0);
    }
    // Initialises a frame processor
    static <T extends FrameProcessor> T initialised(T processor, Frame like, int totalFrames, double fps)
            throws Exception {
        processor.initialise(videoInfo(like, totalFrames, fps));
        return processor;
    }
    // Creates metadata for a specific frame number
    static InfoFrame info(int frameNumber) {
        return new InfoFrame(frameNumber, frameNumber * 40_000L);
    }

    // Creates a frame with a repeatable pixel pattern for testing effects
    static Frame patterned(int width, int height) {
        Frame frame = new Frame(width, height, Frame.DEPTH_UBYTE, 3);
        ByteBuffer pixels = pixels(frame);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean bright = x < width / 3 && y < height / 2;
                int base = y * frame.imageStride + x * 3;
                pixels.put(base, (byte) (bright ? 255 : (x * 4) & 255));
                pixels.put(base + 1, (byte) (bright ? 255 : (y * 5) & 255));
                pixels.put(base + 2, (byte) (bright ? 255 : ((x + y) * 3) & 255));
            }
        }
        return frame;
    }
}