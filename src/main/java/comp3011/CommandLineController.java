/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. <student name and student number insert here upon modification>
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Created in the VideoPlayerApp's main function to receive and parse the video player's command-line arguments.
 *
 * <p>
 * Extracts the video file and supported launch options, reports invalid input
 * or help text, and exposes the resulting launch configuration via methods to the
 * {@link VideoPlayerApp}.
 * </p>
 */
public class CommandLineController {
    private final String[] args;
    private List<FrameProcessor> frameList;
    private boolean helpRequested;
    private boolean audioRequested;
    private boolean maximiseRequested;
    private Integer displayId;
    private File videoFile;
    private String errorMessage;

    public CommandLineController(String[] args) {
        this.args = args.clone();
        this.frameList = new ArrayList<>();
        parse();
        if (errorMessage != null) {
            System.out.println(errorMessage);
        }
        if (helpRequested) {
            printHelp();
        }
    }
    public List<FrameProcessor>getFrameList() {
    	return frameList;
    }
    public File getVideoFile() {
        return videoFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return errorMessage == null ? 0 : 1;
    }

    public Integer getDisplayId() {
        return displayId;
    }

    public boolean isAudioRequested() {
        return audioRequested;
    }

    public boolean isMaximiseRequested() {
        return maximiseRequested;
    }

    public boolean shouldLaunchApplication() {
        return errorMessage == null && videoFile != null;
    }
    
    private boolean parseOptions(String option) {
    	String stringOption = option;
    	switch (stringOption) {
		case "--help": helpRequested = true; break;
		case "--audio": audioRequested = true; break;
		case "--monitor-1": setDisplayId(1); break;
		case "--monitor-2": setDisplayId(2); break;
		case "--maximise": maximiseRequested = true; break;
		case "--number-frames": frameList.add(new FrameNumberer()); break;
		case "--scratch-frames": frameList.add(new FrameScratcher()); break;
		case "--flicker-frames": frameList.add(new FrameFlickerer()); break;
		case "--black-and-white": frameList.add(new FrameBlackAndWhiter()); break;
		case "--yellow-frames": frameList.add(new FrameYellower()); break;
		case "--vignette-frames": frameList.add(new FrameVignetter()); break;
		case "--dust-frames": frameList.add(new FrameDuster()); break;
		case "--mottle-frames": frameList.add(new FrameMottler()); break;
		case "--jitter-frames": frameList.add(new FrameJitterer()); break;
		case "--bleed-frames": frameList.add(new FrameBleeder()); break;
		case "--pepper-frames": frameList.add(new FramePepperer()); break;	
		default: errorMessage = "Unknown option: " + stringOption; return false;       		
		}
    	return true;
    }
    
    private String convertOptionToString(char option) {
    	switch (option) {
		case 'h': return "--help";
		case 'a': return "--audio";
		case '1': return "--monitor-1"; 
		case '2': return "--monitor-2"; 
		case 'x': return"--maximise"; 
		case 'n': return"--number-frames"; 
		case 's': return"--scratch-frames";
		case 'f': return"--flicker-frames"; 
		case 'w': return"--black-and-white"; 
		case 'y': return"--yellow-frames"; 
		case 'v': return"--vignette-frames"; 
		case 'd': return"--dust-frames"; 
		case 'm': return"--mottle-frames"; 
		case 'j': return"--jitter-frames"; 
		case 'b': return"--bleed-frames"; 
		case 'p': return"--pepper-frames";
		default: return Character.toString(option);        		
		}
    }
    private void parse() {
        List<String> videoFiles = new ArrayList<>();
        
        for (String arg : args) {
            if (arg.startsWith("-") && !arg.startsWith("--")) {
            	for (int i=1; i< arg.length(); i++) {
            		String stringOption = convertOptionToString(arg.charAt(i));
            		if (!parseOptions(stringOption)) {            			
            			break;
            		}
            		
            	}
            }
            else if (arg.startsWith("--")) {
            	parseOptions(arg);
            } else {
                videoFiles.add(arg);
            }
        }

        if (videoFiles.size() > 1) {
        	System.out.println("Size of vid: " + videoFiles.size());
        	
            errorMessage = "Usage: VideoPlayer [options] [video-file]";
        } else if (videoFiles.size() == 1) {
        	System.out.println("Size of vid: " + videoFiles.size());
        	 System.out.println("Size of frame: " + frameList.size()); 
            videoFile = new File(videoFiles.get(0));
            if (!videoFile.isFile()) {
                errorMessage = "File not found: " + videoFile.getPath();
                videoFile = null;
            }
        } else {
        	System.out.println("Size of vid: " + videoFiles.size());
            if (!helpRequested) {
                errorMessage = "No video file specified.";
            }
        }
    }

    private void setDisplayId(int displayId) {
        if (this.displayId != null && this.displayId != displayId) {
            errorMessage = "Only one display option can be used";
            return;
        }
        this.displayId = displayId;
    }

    private void printHelp() {
        System.out.println("Usage: VideoPlayer [options] [video-file]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help         Show this help message");
        System.out.println("  -a, --audio        Play audio");
        System.out.println("  -x, --maximise     Open the player maximised");
        System.out.println("  -1, --monitor-1    Open the player on display 1");
        System.out.println("  -2, --monitor-2    Open the player on display 2");
        System.out.println();
        System.out.println("Frame processors:");
        System.out.println("-n, --number-frames    Render the frame number onto each frame");
        System.out.println("-s, --scratch-frames   Render vertical film scratches");
        System.out.println("-f, --flicker-frames   Randomly dim frames");
        System.out.println("-w, --black-and-white  Convert frames to black and white");
        System.out.println("-y, --yellow-frames    Apply a warmer colour temperature");
        System.out.println("-v, --vignette-frames  Darken the frame edges");
        System.out.println("-d, --dust-frames      Render dust and hair marks");
        System.out.println("-j, --jitter-frames    Randomly displace frames by a few pixels");
        System.out.println("-m, --mottle-frames    Add cloudy emulsion mottling");
        System.out.println("-b, --bleed-frames     Bleed light into frames");
        System.out.println("-p, --pepper-frames    Pepper frames with dark spots/blotches");
    }
}
