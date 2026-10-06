package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import comp3011.CommandLineController;
import comp3011.FrameBlackAndWhiter;
import comp3011.FrameDuster;
import comp3011.FrameFlickerer;
import comp3011.FrameJitterer;
import comp3011.FrameProcessor;
import comp3011.FrameScratcher;

class CommandLineControllerTest {
	
	@Test
	void testParseCharacterFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"-f"});
		int numberFrameProcessors = controller.getFrameList().size();
		FrameProcessor frameType = controller.getFrameList().get(0);
		assertEquals(1, numberFrameProcessors);
		assertInstanceOf(FrameFlickerer.class, frameType);
	}
	@Test
	void testParseStringFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"--scratch-frames"});
		int numberFrameProcessors = controller.getFrameList().size();
		FrameProcessor frameType = controller.getFrameList().get(0);
		assertEquals(1, numberFrameProcessors);
		assertInstanceOf(FrameScratcher.class, frameType);
}
	@Test
	void testMixingParseFrameProcessor() {
		CommandLineController controller = new CommandLineController(new String[] {"--jitter-frames", "--flicker-frames", "-fw", "--black-and-white", "-j", "-f", "-d"});
		List<FrameProcessor> frameProcessors = controller.getFrameList();
		
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
}
