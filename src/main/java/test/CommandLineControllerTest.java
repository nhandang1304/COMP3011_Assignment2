package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import comp3011.CommandLineController;
import comp3011.FrameBlackAndWhiter;
import comp3011.FrameDuster;
import comp3011.FrameFlickerer;
import comp3011.FrameJitterer;
import comp3011.FrameProcessor;
import comp3011.FrameScratcher;

/**This test class is designed to validate the correctness of the {@link CommandLineController}.
 Tests short flags, long options, combined effect chaining, order preservation, 
 and robust error handling for invalid command-line arguments.
 **/
class CommandLineControllerTest {
	// Tests whether a single short character flag is correctly parsed 
	// into its corresponding frame processor instance
	@Test
	void testParseCharacterFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"-f"});
		int numberFrameProcessors = controller.getFrameProcessorList().size();
		FrameProcessor frameType = controller.getFrameProcessorList().get(0);
		assertEquals(1, numberFrameProcessors);
		assertInstanceOf(FrameFlickerer.class, frameType);
	}
	// Tests whether a standard long option flag is correctly parsed 
	// into its corresponding frame processor instance 
	@Test
	void testParseStringFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"--scratch-frames"});
		int numberFrameProcessors = controller.getFrameProcessorList().size();
		FrameProcessor frameType = controller.getFrameProcessorList().get(0);
		assertEquals(1, numberFrameProcessors);
		assertInstanceOf(FrameScratcher.class, frameType);
}
	// Tests complex mixing of long options, clustered short flags, and repetition
	@Test
	void testMixingParseFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"--jitter-frames", "--flicker-frames", "-fw", "--black-and-white", "-j", "-f", "-d"});
		List<FrameProcessor> frameProcessors = controller.getFrameProcessorList();
		
		assertEquals(8, frameProcessors.size());
		assertInstanceOf(FrameJitterer.class, frameProcessors.get(0));
		assertInstanceOf(FrameFlickerer.class, frameProcessors.get(1));
		assertInstanceOf(FrameFlickerer.class, frameProcessors.get(2));
		assertInstanceOf(FrameBlackAndWhiter.class, frameProcessors.get(3));
		assertInstanceOf(FrameBlackAndWhiter.class, frameProcessors.get(4));
		assertInstanceOf(FrameJitterer.class, frameProcessors.get(5));
		assertInstanceOf(FrameFlickerer.class, frameProcessors.get(6));
		assertInstanceOf(FrameDuster.class, frameProcessors.get(7));
		
		
	}
	// Tests error handling when an invalid or unrecognized option/character sequence is provided
	@Test
	void testInvalidParseFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"-fn9"});
		int numberFrameProcessors = controller.getFrameProcessorList().size();
		assertEquals(0, numberFrameProcessors);
		assertNotNull(controller.getErrorMessage());
		
	}
}
