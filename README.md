# COMP3011 Assignment 2 - Java Local Project

---

- Student name: Thi Thanh Nhan Dang
- Student ID: a2943401
- Student email: a2943401@adelaide.edu.au

---

## Overview
---
This project is an advanced, optimized multi-threaded video player built using JavaFX and FFmpeg (via JavaCV). It implements a robust Producer-Consumer concurrency architecture, supported by a bounded blocking queue and a worker thread pool.

---

## Design Architecture

**1. Producer-Consumer Concurrency Pipeline (FramePreparedProducer)**:
   - Offloads heavy frame decoding, effect processing, and image conversion away from the UI thread to background threads, which keeps the application responsive.
   
   - ArrayDeque<Pending> has peekFirst() to check the first frame, iterate through Pending tasks to cancel them during seek(), and use clear() to remove outdated frames. It provides operations at both ends without shifting elements, which is suitable for grabAndSubmitFrames() to add tasks using addLast() and deliverHead() to process the first frame.
   
   - ArrayBlockingQueue<PreparedFrame> is thread-safe, has a fixed capacity, and supports non-blocking offer() and poll() operations. Its capacity provides backpressure by preventing the producer from adding more frames when the queue is full. poll() allows the UI thread to retrieve a frame without waiting, returning null if no frame is available. It also uses a fixed-size array, reducing per-frame object allocation, and maintaining FIFO order.

**2.ClockManagement**

> Why I created this class? Because it follows the Single Responsibility Principle by managing playback timing separately from the model and producer. It centralises shared timing state, such as timestamps, playback start time, pause and seek positions, while using atomic variables to support thread-safe access without locking every frame read. Passing it to FramePreparedProducer through the constructor also avoids a direct dependency on VideoPlayerModel

- ClockManagement uses AtomicLong counters to manage playback timing safely across multiple threads without requiring locks for every time update. It also uses an AtomicInteger for generation tracking to identify outdated background tasks after operations such as seeking.

**3. Command-Line Interface (CLI)**:
- The parse() method processes command-line arguments by identifying combined short flags, long options, and video file paths. The convertOptionToString() method maps each short flag to its corresponding long option. Then, parseOptions() uses a switch-case statement to handle each option, adding the appropriate effect processor to the list in the order specified. If an invalid option is found, it clears the effect list and stores an error message.

**4. Testing**:

- CommandLineControllerTest is designed to validate the correctness of the CommandLineController.
 Tests short flags, long options, combined effect chaining, order preservation, 
 and robust error handling for invalid command-line arguments.
 - EffectConcurrentTest checks whether each video effect is thread-safe. In other words, it tests whether the same effect object produces the same output when multiple threads process frames at the same time, compared with processing them one by one.
 - FrameForTesting provides helper methods for creating and manipulating test frames.
 - FramePreparedProducerTest tests the producer's main behaviours without needing to load a real video file. It uses a FakeGrabber to simulate video frames, a NullConverter to avoid JavaFX image conversion, and a Recorder to track the order of effect processing.
 
**5. AI Assistant**:

- ChatGPT was used to help explain Java methods and improve code for developing FrameForTesting.
 
---