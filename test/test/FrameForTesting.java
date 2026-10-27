package comp3011;

import java.nio.ByteBuffer;

import org.bytedeco.javacv.Frame;


final class TestFrames {
    static final String MEDIA_NAME = "test video.mp4";

    private TestFrames() {
    }

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

    static void setBgr(Frame frame, int x, int y, int b, int g, int r) {
        int base = y * frame.imageStride + x * frame.imageChannels;
        pixels(frame).put(base, (byte) b);
        pixels(frame).put(base + 1, (byte) g);
        pixels(frame).put(base + 2, (byte) r);
    }

    static byte[] snapshot(Frame frame) {
        ByteBuffer pixels = pixels(frame);
        byte[] copy = new byte[pixels.capacity()];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = pixels.get(i);
        }
        return copy;
    }

    static InfoVideo videoInfo(Frame frame, int totalFrames, double fps) {
        return new InfoVideo(MEDIA_NAME, totalFrames, frame.imageWidth, frame.imageHeight, frame.imageDepth,
                frame.imageChannels, frame.imageStride, fps, (int) Math.round(fps),
                (int) Math.round(1_000_000.0 / fps), 0);
    }

    static <T extends FrameProcessor> T initialised(T processor, Frame like, int totalFrames, double fps)
            throws Exception {
        processor.initialise(videoInfo(like, totalFrames, fps));
        return processor;
    }

    static InfoFrame info(int frameNumber) {
        return new InfoFrame(frameNumber, frameNumber * 40_000L);
    }


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