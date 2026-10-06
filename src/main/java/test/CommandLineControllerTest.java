package test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import comp3011.CommandLineController;
import comp3011.FrameFlickerer;
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
}
