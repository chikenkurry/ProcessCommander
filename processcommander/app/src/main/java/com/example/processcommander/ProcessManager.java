package com.example.processcommander;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Vibrator;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import android.graphics.Point;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessManager {
    // Constants
    private static final int MAX_PROCESSES = 15;
    private static final int MAX_RUNNING_PROCESSES = 4;
    private static final int RUNNING_QUEUE_SIZE = 3;
    private static final int READY_QUEUE_SIZE = 5;
    private static final int BLOCKED_QUEUE_SIZE = 4;
    private static final long PROCESS_GEN_INTERVAL_MS = 5000; // Time between new process generation
    private static final String[] PROCESS_NAMES = {
            "Browser", "FileSystem", "Network", "Audio", "Video", 
            "SystemUI", "Kernel", "Memory", "Update", "Security", 
            "Backup", "Search", "Sync", "Bluetooth", "Wifi"
    };
    
    // Android context and system services
    private Context context;
    private Random random;
    private Vibrator vibrator;
    
    // Difficulty settings
    private static final String PREFS_NAME = "ProcessCommanderPrefs";
    private static final String PREF_DIFFICULTY = "difficulty";
    private int difficultyLevel; // 0=easy, 1=medium, 2=hard
    
    // Difficulty progression
    private long gameStartTime;
    private float difficultyMultiplier = 1.0f; // Increases as game progresses
    private static final float EASY_MODE_DIFFICULTY_INCREASE_RATE = 0.05f; // 5% more difficult per minute
    private static final float HARD_MODE_DIFFICULTY_INCREASE_RATE = 0.1f; // 10% more difficult per minute
    
    // Resource adjustment based on difficulty
    private float cpuUsageMultiplier;
    private float memoryUsageMultiplier;
    private int maxProcessesByDifficulty;
    private int maxRunningProcessesByDifficulty;
    private long emergencyIntervalMin; // min time between emergencies in ms
    private long emergencyIntervalMax; // max time between emergencies in ms
    
    // System resources
    private int totalCPU = 100;
    private int usedCPU = 0;
    private int totalMemory = 1024; // MB
    private int usedMemory = 0;
    
    // Screen dimensions for process positioning
    private int screenWidth;
    private int screenHeight;
    private static final int MARGIN_TOP = 200; // Space at top for UI elements
    private static final int MARGIN_BOTTOM = 250; // Space at bottom for buttons
    
    // Process queues
    private CopyOnWriteArrayList<Process> newProcesses; // New list for processes with no state
    private CopyOnWriteArrayList<Process> runningQueue;
    private CopyOnWriteArrayList<Process> readyQueue;
    private CopyOnWriteArrayList<Process> blockedQueue;
    private Process selectedProcess;

    // Starvation prevention
    private static final long STARVATION_THRESHOLD_MS = 30000; // 30 seconds
    private ConcurrentHashMap<Process, Long> processWaitTimes;
    
    // Flags and state
    private boolean emergencyEvent = false;
    private long emergencyStartTime = 0; // Track when emergency started
    private int emergencyTimeoutSeconds = 15; // How long before emergency escalates
    private int criticalPenaltyCount = 0; // Track how many critical tasks were ignored
    private int score = 0;
    private int processesCompleted = 0;
    private int emergencyEventsHandled = 0;
    private boolean gameOver = false;
    private String gameOverReason = ""; // Add field to store reason
    private static final int CRITICAL_GRACE_PERIOD = 5; // Grace period in seconds before critical processes can have penalties
    
    // Threading
    private ExecutorService threadPool;
    private volatile boolean isRunning = true;
    
    // Priority system
    private int nextProcessPriority = 10; // Start with highest priority
    private boolean initialPriorityPhase = true; // Track if we're still in the initial 1-10 phase
    
    // References to GameView's queue areas (need to be set)
    private RectF newProcessAreaRef;
    private RectF runningQueueAreaRef;
    private RectF readyQueueAreaRef;
    private RectF blockedQueueAreaRef;
    
    private static final int SLOTS_PER_ROW = 3; 
    private static final float SLOT_SPACING = 15f;
    
    public ProcessManager(Context context) {
        this.context = context;
        this.newProcesses = new CopyOnWriteArrayList<>(); // Initialize new list
        this.runningQueue = new CopyOnWriteArrayList<>();
        this.readyQueue = new CopyOnWriteArrayList<>();
        this.blockedQueue = new CopyOnWriteArrayList<>();
        this.processWaitTimes = new ConcurrentHashMap<>();
        this.random = new Random();
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.threadPool = Executors.newFixedThreadPool(3);
        
        // Track game start time for difficulty progression
        this.gameStartTime = System.currentTimeMillis();
        
        // Load difficulty settings
        loadDifficultySettings();
        
        // Get screen dimensions for better process placement
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
        
        // Start the process generator thread
        startProcessGeneratorThread();
        
        // Start the emergency event generator thread
        startEmergencyEventThread();
        
        // Start the starvation checker thread
        startStarvationCheckerThread();
    }
    
    private void loadDifficultySettings() {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        difficultyLevel = settings.getInt(PREF_DIFFICULTY, 1); // Default to medium
        
        // Set resource adjustments based on difficulty
        switch (difficultyLevel) {
            case 0: // Easy - starts very easy but becomes harder
                cpuUsageMultiplier = 0.3f;
                memoryUsageMultiplier = 0.4f;
                maxProcessesByDifficulty = 6;
                maxRunningProcessesByDifficulty = 3;
                emergencyIntervalMin = 45000; // 45 seconds
                emergencyIntervalMax = 90000; // 90 seconds
                break;
            case 1: // Medium (default)
                cpuUsageMultiplier = 1.0f;
                memoryUsageMultiplier = 1.0f;
                maxProcessesByDifficulty = MAX_PROCESSES;
                maxRunningProcessesByDifficulty = MAX_RUNNING_PROCESSES;
                emergencyIntervalMin = 15000; // 15 seconds
                emergencyIntervalMax = 45000; // 45 seconds
                break;
            case 2: // Hard - starts easier than before but ramps up quickly
                cpuUsageMultiplier = 1.2f; // Reduced from 1.5f
                memoryUsageMultiplier = 1.1f; // Reduced from 1.3f
                maxProcessesByDifficulty = MAX_PROCESSES;
                maxRunningProcessesByDifficulty = MAX_RUNNING_PROCESSES;
                emergencyIntervalMin = 12000; // 12 seconds
                emergencyIntervalMax = 30000; // 30 seconds
                break;
        }
    }
    
    private void startProcessGeneratorThread() {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        // Adjust interval based on difficulty
                        long interval = PROCESS_GEN_INTERVAL_MS;
                        if (difficultyLevel == 0) { // Easy mode
                            interval += 2000; // Longer interval between processes
                        } else if (difficultyLevel == 2) { // Hard mode
                            interval -= 1000; // Shorter interval between processes
                        }
                        
                        Thread.sleep(interval);
                        if (newProcesses.size() + runningQueue.size() + readyQueue.size() + blockedQueue.size() < maxProcessesByDifficulty && !gameOver) {
                            generateNewProcess();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }
    
    private void startEmergencyEventThread() {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        // Wait between emergency events based on difficulty
                        int randomInterval = random.nextInt((int)(emergencyIntervalMax - emergencyIntervalMin));
                        Thread.sleep(emergencyIntervalMin + randomInterval);
                        
                        if (!gameOver && !emergencyEvent) {
                            // In easy mode, don't trigger emergencies if player is already struggling
                            if (difficultyLevel == 0 && (usedCPU > totalCPU * 0.7f || usedMemory > totalMemory * 0.7f)) {
                                // Skip this emergency to give player a break
                                Thread.sleep(emergencyIntervalMax);
                                continue;
                            }
                            
                            triggerEmergencyEvent();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }
    
    private void startStarvationCheckerThread() {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        Thread.sleep(1000); // Check every second
                        checkForStarvation();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }
    
    private void checkForStarvation() {
        long currentTime = System.currentTimeMillis();
        
        // Check ready queue for starvation
        for (Process process : readyQueue) {
            Long waitStartTime = processWaitTimes.get(process);
            if (waitStartTime != null && currentTime - waitStartTime > STARVATION_THRESHOLD_MS) {
                // Process is starving, force it into running queue if possible
                if (process.getPriority() < 5) {
                    process.setPriority(process.getPriority() + 1); // Increase priority
                }
                
                if (runningQueue.size() < RUNNING_QUEUE_SIZE) {
                    moveToRunningQueue(process);
                } else if (!process.getName().startsWith("CRITICAL")) {
                    // If not critical, penalize the player
                    score = Math.max(0, score - 200);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                            vibrator.vibrate(500);
                        }
                    }
            } else {
                    // Critical process is starving
                    criticalPenaltyCount++;
                    applyPenalty();
                }
            }
        }
    }
    
    public void moveToRunningQueue(Process process) {
        if (process == null || runningQueue.size() >= RUNNING_QUEUE_SIZE) return;
        removeProcessFromAllQueues(process);
        if (!runningQueue.contains(process)) {
            runningQueue.add(process);
            process.setState(Process.State.RUNNING);
            processWaitTimes.remove(process);
            repositionAllProcesses(); // Reposition ALL after move
        }
    }

    public void moveToReadyQueue(Process process) {
        if (process == null || readyQueue.size() >= READY_QUEUE_SIZE) return;
        removeProcessFromAllQueues(process);
        if (!readyQueue.contains(process)) {
            readyQueue.add(process);
            process.setState(Process.State.READY);
            processWaitTimes.putIfAbsent(process, System.currentTimeMillis());
            repositionAllProcesses(); // Reposition ALL after move
        }
    }

    public void moveToBlockedQueue(Process process) {
        if (process == null || blockedQueue.size() >= BLOCKED_QUEUE_SIZE) return;
        removeProcessFromAllQueues(process);
        if (!blockedQueue.contains(process)) {
            blockedQueue.add(process);
            process.setState(Process.State.BLOCKED);
            processWaitTimes.remove(process);
            repositionAllProcesses(); // Reposition ALL after move
        }
    }
    
    private void removeProcessFromAllQueues(Process process) {
        newProcesses.remove(process);
        runningQueue.remove(process);
        readyQueue.remove(process);
        blockedQueue.remove(process);
    }
    
    public void repositionAllProcesses() {
        repositionProcessesInList(newProcesses, newProcessAreaRef, Process.State.NEW, MAX_PROCESSES); // Use MAX_PROCESSES for new area capacity
        repositionProcessesInList(runningQueue, runningQueueAreaRef, Process.State.RUNNING, RUNNING_QUEUE_SIZE);
        repositionProcessesInList(readyQueue, readyQueueAreaRef, Process.State.READY, READY_QUEUE_SIZE);
        repositionProcessesInList(blockedQueue, blockedQueueAreaRef, Process.State.BLOCKED, BLOCKED_QUEUE_SIZE);
    }
    
    private void repositionProcessesInList(List<Process> processList, RectF area, Process.State state, int queueCapacity) {
        if (area == null) return; // Don't reposition if the area reference isn't set yet
        for (int i = 0; i < processList.size(); i++) {
            Process process = processList.get(i);
            // Calculate target slot position based on index 'i'
            PointF targetPos = calculateSlotPosition(i, area, queueCapacity);
            process.setPosition(targetPos.x, targetPos.y); // Snap immediately for now
            process.setTargetPosition(targetPos.x, targetPos.y);
            process.setDragging(false); // Ensure not dragging
        }
    }

    private PointF calculateSlotPosition(int slotIndex, RectF area, int queueCapacity) {
        if (area == null) {
            // Default position if area is not yet defined (e.g., during initialization)
            return new PointF(screenWidth / 2, screenHeight / 2); 
        }
        
        // Use queueCapacity to determine layout, not maxSize directly if it differs (like for New area)
        int maxSlots = queueCapacity;
        int rowCount = (int) Math.ceil((double) maxSlots / SLOTS_PER_ROW);

        float areaContentWidth = area.width() - 2 * SLOT_SPACING;
        float areaContentHeight = area.height() - 90 - 2 * SLOT_SPACING; // Available height below title

        float slotWidth = areaContentWidth / SLOTS_PER_ROW - SLOT_SPACING;
        float slotHeight = areaContentHeight / rowCount - SLOT_SPACING;
        slotHeight = Math.min(slotHeight, slotWidth * 1.2f); // Maintain aspect ratio

        int targetRow = slotIndex / SLOTS_PER_ROW;
        int targetCol = slotIndex % SLOTS_PER_ROW;

        float slotLeft = area.left + SLOT_SPACING + targetCol * (slotWidth + SLOT_SPACING);
        float slotTop = area.top + 90 + SLOT_SPACING + targetRow * (slotHeight + SLOT_SPACING);

        // Calculate the center of the target slot
        float targetX = slotLeft + slotWidth / 2;
        float targetY = slotTop + slotHeight / 2;

        return new PointF(targetX, targetY);
    }
    
    private void generateNewProcess() {
        String name = PROCESS_NAMES[random.nextInt(PROCESS_NAMES.length)];
        
        // Generate random priority between 1 and 10
        int priority = random.nextInt(10) + 1;
        
        // Generate CPU burst time (in milliseconds)
        long cpuBurstTime = (5 + random.nextInt(16)) * 1000; // 5-20 seconds
        
        // Generate memory requirement
        int memoryRequired = 100 + random.nextInt(401); // 100-500 MB
        
        // Create new process with no state
        Process newProcess = new Process(
            name,
            priority,
            cpuBurstTime,
            memoryRequired
        );
        
        // Add to list FIRST
        newProcesses.add(newProcess);
        
        // THEN reposition all to find its slot
        repositionAllProcesses();
    }
    
    private void triggerEmergencyEvent() {
        emergencyEvent = true;
        emergencyStartTime = System.currentTimeMillis();
        
        // Determine timeout based on difficulty
        if (difficultyLevel == 0) { // Easy
            emergencyTimeoutSeconds = 20; // 20 seconds before consequences
        } else if (difficultyLevel == 2) { // Hard
            emergencyTimeoutSeconds = 10; // 10 seconds before consequences
        } else { // Medium
            emergencyTimeoutSeconds = 15; // 15 seconds before consequences
        }
        
        // Vibrate the device to alert the user
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }
        
        // Generate a critical process based on difficulty
        String name = "CRITICAL-" + random.nextInt(100);
        
        // Adjust priority based on difficulty
        int priority;
        if (difficultyLevel == 0) { // Easy
            priority = 8; // Not max priority in easy mode
        } else if (difficultyLevel == 2) { // Hard
            priority = 10; // Highest priority
        } else { // Medium
            priority = 9; // High priority
        }
        
        // Adjust burst time based on difficulty
        int cpuBurstTime;
        if (difficultyLevel == 0) { // Easy
            cpuBurstTime = (random.nextInt(5) + 5) * 1000; // 5-9 seconds - more time to respond
        } else if (difficultyLevel == 2) { // Hard
            cpuBurstTime = (random.nextInt(4) + 1) * 1000; // 1-4 seconds - less time
        } else { // Medium
            cpuBurstTime = (random.nextInt(4) + 2) * 1000; // 2-5 seconds
        }
        
        // Adjust memory required based on difficulty
        int memoryRequired;
        if (difficultyLevel == 0) { // Easy
            memoryRequired = (random.nextInt(50) + 50); // 50-100 MB
        } else if (difficultyLevel == 2) { // Hard
            memoryRequired = (random.nextInt(150) + 100); // 100-250 MB
        } else { // Medium
            memoryRequired = (random.nextInt(100) + 50); // 50-150 MB
        }
        
        Process emergencyProcess = new Process(name, priority, cpuBurstTime, memoryRequired);
        emergencyProcess.setState(Process.State.BLOCKED); // Start in blocked state
        
        // Position the process prominently on screen (center)
        float x = screenWidth / 2;
        float y = screenHeight / 2;
        emergencyProcess.setPosition(x, y);
        
        // Add to processes list
        runningQueue.add(emergencyProcess);
    }
    
    public void update(float deltaTime) {
        if (gameOver) {
            return;
        }
        
        // Update difficulty progression
        updateDifficultyProgression();
        
        // List to hold processes that completed in this frame
        List<Process> completedProcesses = new ArrayList<>();

        // List to hold processes that need to move from Blocked to Ready
        List<Process> readyToUnblock = new ArrayList<>();

        // Check for process interrupts & Update running processes
        Iterator<Process> iterator = runningQueue.iterator();
        while (iterator.hasNext()) {
            Process process = iterator.next();
            
            // Process Interrupts (Check first)
            if (!process.isInterrupted() && random.nextFloat() < 0.05f * deltaTime) { // 5% chance per second
                process.setInterrupted(true); // Mark as interrupted
                // Don't move immediately, let state handling logic manage it
            }
            
            // Update process logic (e.g., decrementing CPU time)
            process.update(deltaTime); 
            
            // Check for completion AFTER updating
            if (process.getState() == Process.State.RUNNING && process.getCpuTimeRemaining() <= 0) {
                completedProcesses.add(process); // Add to list for later removal
            }
            
            // Handle state changes (like moving interrupted processes)
            if (process.isInterrupted() && process.getState() == Process.State.RUNNING) {
                 // We could potentially move it to blocked queue here, 
                 // but let's stick to user/button actions for moves for now.
                 // The state is already set to BLOCKED inside process.setInterrupted(true)
                 // We just need to ensure it stops consuming CPU etc.
            }
        }

        // --- Process Completed Processes --- 
        if (!completedProcesses.isEmpty()) {
            for (Process completedProcess : completedProcesses) {
                if (runningQueue.contains(completedProcess)) { // Check if still in running queue
                    completedProcess.setState(Process.State.TERMINATED); // Mark as terminated
                    runningQueue.remove(completedProcess); // Now remove it safely
                    processesCompleted++;
                    score += completedProcess.getPriority() * 100; 
                    processWaitTimes.remove(completedProcess);
                }
            }
            repositionAllProcesses(); // Reposition after handling completions
        }
        // --- End Processing Completions ---

        // Randomly complete I/O for blocked processes & Check for auto-unblock
        for (Process process : blockedQueue) {
            // Ensure it's actually blocked due to an interrupt before randomly completing I/O
            if (process.getState() == Process.State.BLOCKED && process.isInterrupted()) { 
                 if (!process.isIOCompleted() && random.nextFloat() < 0.1f * deltaTime) { // 10% chance per second
                    process.setIOCompleted(true); // Mark I/O as done
                    // Automatically move to Ready queue now
                    readyToUnblock.add(process);
                }
            }
        }
        
        // --- Auto-move processes from Blocked to Ready --- 
        if (!readyToUnblock.isEmpty()) {
            for (Process process : readyToUnblock) {
                moveToReadyQueue(process); // This also calls repositionAllProcesses
            }
            // No need to call repositionAllProcesses here, as moveToReadyQueue does it.
        }

        // Update resources
        updateResources();
        
        // Check for game over condition
        checkGameOverCondition();
    }
    
    // Helper method for easy mode: automatically terminate low priority processes
    private void sortAndTerminateLowPriorityProcesses() {
        // Get all running processes
        List<Process> allRunning = new ArrayList<>();
        for (Process p : runningQueue) {
            if (p.getState() == Process.State.RUNNING) {
                allRunning.add(p);
            }
        }
        
        // Sort by priority (lowest first)
        Collections.sort(allRunning, new Comparator<Process>() {
            @Override
            public int compare(Process p1, Process p2) {
                return Integer.compare(p1.getPriority(), p2.getPriority());
            }
        });
        
        // Terminate up to 2 lowest priority processes
        int count = 0;
        for (Process p : allRunning) {
            if (count < 2) {
                p.setState(Process.State.TERMINATED);
                runningQueue.remove(p);
                count++;
            } else {
                break;
            }
        }
    }
    
    // Update difficulty based on elapsed game time
    private void updateDifficultyProgression() {
        // Calculate how many minutes the game has been running
        long currentTime = System.currentTimeMillis();
        float minutesElapsed = (currentTime - gameStartTime) / 60000f;
        
        // Adjust difficulty multiplier based on game mode
        if (difficultyLevel == 0) { // Easy mode - gradually gets harder
            difficultyMultiplier = 1.0f + (minutesElapsed * EASY_MODE_DIFFICULTY_INCREASE_RATE);
            
            // Cap the max difficulty for easy mode to avoid it becoming too hard
            difficultyMultiplier = Math.min(difficultyMultiplier, 2.0f);
            
            // After 10 minutes, start reducing max running processes to increase challenge
            if (minutesElapsed > 10) {
                int processReduction = (int)(minutesElapsed - 10) / 5; // Every 5 mins after 10 mins
                maxRunningProcessesByDifficulty = Math.max(2, 3 - processReduction);
            }
        } else if (difficultyLevel == 2) { // Hard mode - starts easier, gets much harder
            difficultyMultiplier = 1.0f + (minutesElapsed * HARD_MODE_DIFFICULTY_INCREASE_RATE);
            
            // Cap the hard mode difficulty to avoid it becoming impossible
            difficultyMultiplier = Math.min(difficultyMultiplier, 2.5f);
        }
        
        // No progression for medium mode - it stays consistent
    }
    
    private void updateResources() {
        usedCPU = 0;
        usedMemory = 0;
        
        // Calculate for all processes including new ones
        for (Process process : newProcesses) {
            usedMemory += process.getMemoryRequired() * memoryUsageMultiplier * difficultyMultiplier * 0.3;
        }
        
        for (Process process : runningQueue) {
            usedCPU += process.getPriority() * 10 * cpuUsageMultiplier * difficultyMultiplier;
                usedMemory += process.getMemoryRequired() * memoryUsageMultiplier * difficultyMultiplier;
            }
        
        for (Process process : readyQueue) {
            usedMemory += process.getMemoryRequired() * memoryUsageMultiplier * difficultyMultiplier * 0.5;
        }
        
        for (Process process : blockedQueue) {
            usedMemory += process.getMemoryRequired() * memoryUsageMultiplier * difficultyMultiplier * 0.3;
        }
    }
    
    public void draw(Canvas canvas, Paint paint, GameView gameView) {
        // Draw all processes in their respective areas
        for (Process process : newProcesses) {
            process.draw(canvas, paint, gameView);
        }
        for (Process process : runningQueue) {
            process.draw(canvas, paint, gameView);
        }
        for (Process process : readyQueue) {
            process.draw(canvas, paint, gameView);
        }
        for (Process process : blockedQueue) {
            process.draw(canvas, paint, gameView);
        }
    }
    
    public Process findProcessAtPosition(float x, float y) {
        // Check new processes first
        for (Process process : newProcesses) {
            if (process.contains(x, y)) {
                return process;
            }
        }
        // Then check other queues
        for (Process process : runningQueue) {
            if (process.contains(x, y)) {
                return process;
            }
        }
        for (Process process : readyQueue) {
            if (process.contains(x, y)) {
                return process;
            }
        }
        for (Process process : blockedQueue) {
            if (process.contains(x, y)) {
                return process;
            }
        }
        return null;
    }
    
    public void selectProcess(Process process) {
        // Deselect previous process
        if (selectedProcess != null) {
            selectedProcess.setSelected(false);
        }
        
        // Select new process
        if (process != null) {
            process.setSelected(true);
        }
        
        selectedProcess = process;
    }
    
    public void increasePriority(Process process) {
        if (process != null) {
            process.setPriority(process.getPriority() + 1);
        }
    }
    
    public void decreasePriority(Process process) {
        if (process != null) {
            process.setPriority(process.getPriority() - 1);
        }
    }
    
    public void unblockProcess(Process process) {
        if (process != null && process.getState() == Process.State.BLOCKED) {
            process.setState(Process.State.READY);
            
            // If this is an emergency process, give immediate feedback
            if (process.getName().startsWith("CRITICAL")) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(200);
                    }
                }
            }
        }
    }
    
    public void terminateProcess(Process process) {
        if (process != null) {
            removeProcessFromAllQueues(process); // Remove first
            processWaitTimes.remove(process);
            process.setState(Process.State.TERMINATED); // Mark as terminated (optional)
            
            // Penalty for terminating critical process
            if (process.getName().startsWith("CRITICAL")) {
                criticalPenaltyCount++;
                applyPenalty();
            }
            
            repositionAllProcesses(); // Reposition remaining processes
        }
    }
    
    public void shutdown() {
        isRunning = false;
        threadPool.shutdown();
    }
    
    // Getters
    public int getTotalCPU() {
        return totalCPU;
    }
    
    public int getUsedCPU() {
        return usedCPU;
    }
    
    public int getTotalMemory() {
        return totalMemory;
    }
    
    public int getUsedMemory() {
        return usedMemory;
    }
    
    public int getScore() {
        return score;
    }
    
    public int getProcessesCompleted() {
        return processesCompleted;
    }
    
    public int getEmergencyEventsHandled() {
        return emergencyEventsHandled;
    }
    
    public boolean isEmergencyEvent() {
        return emergencyEvent;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public Process getSelectedProcess() {
        return selectedProcess;
    }
    
    // Handle emergency timeout - called when emergency is ignored too long
    private void handleEmergencyTimeout() {
        // Check if the grace period has passed since emergency start
        long currentTime = System.currentTimeMillis();
        long emergencyDuration = (currentTime - emergencyStartTime) / 1000;
        
        if (emergencyDuration <= CRITICAL_GRACE_PERIOD) {
            // Still within grace period, don't apply penalties yet
            return;
        }
        
        // Even in easy mode, ignored emergencies have consequences
        criticalPenaltyCount++;
        
        // Increment the emergency's priority or create secondary emergencies
        boolean criticalFound = false;
        for (Process process : runningQueue) {
            if (process.getName().startsWith("CRITICAL-") && process.getState() == Process.State.BLOCKED) {
                criticalFound = true;
                
                // Make the critical process more demanding
                process.setPriority(Math.min(10, process.getPriority() + 2));
                process.setMemoryRequired((int)(process.getMemoryRequired() * 1.5f));
                
                // Visual feedback that emergency is escalating
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(500);
                    }
                }
                
                // Reset timeout
                emergencyStartTime = System.currentTimeMillis();
            }
        }
        
        // Apply penalties based on how many times emergencies have been ignored
        applyPenalty();
    }
    
    private void applyPenalty() {
        if (criticalPenaltyCount == 1) {
            // First warning - resource penalties
            usedCPU += totalCPU * 0.2f; // 20% CPU penalty
            usedMemory += totalMemory * 0.15f; // 15% memory penalty
            score = Math.max(0, score - 300); // Score penalty
            
        } else if (criticalPenaltyCount == 2) {
            // Second warning - spawn additional processes and bigger penalties
            usedCPU += totalCPU * 0.3f; // 30% CPU penalty
            usedMemory += totalMemory * 0.25f; // 25% memory penalty
            score = Math.max(0, score - 500); // Bigger score penalty
            
            // Generate random problematic processes
            for (int i = 0; i < 2; i++) {
                String name = "WARNING-" + random.nextInt(100);
                Process warningProcess = new Process(name, 4, 8000, 120);
                repositionAllProcesses();
                runningQueue.add(warningProcess);
            }
            
        } else if (criticalPenaltyCount >= 3) {
            // Third strike - game over on all difficulty levels
            gameOver = true;
            gameOverReason = "CRITICAL FAILURE: Ignored 3 critical processes."; // Set reason
        }
    }
    
    // Getters for queue sizes
    public int getRunningQueueSize() {
        return runningQueue.size();
    }
    
    public int getReadyQueueSize() {
        return readyQueue.size();
    }
    
    public int getBlockedQueueSize() {
        return blockedQueue.size();
    }

    private void checkGameOverCondition() {
        if ((usedCPU >= totalCPU || usedMemory >= totalMemory) && !gameOver) {
            // In easy mode, give a grace period before game over
            if (difficultyLevel == 0) {
                // Automatically terminate some lower priority processes
                sortAndTerminateLowPriorityProcesses();
            } else {
                gameOver = true;
                if (usedCPU >= totalCPU) { // Set reason based on condition
                     gameOverReason = "CPU OVERLOAD: System resources exceeded.";
                } else {
                     gameOverReason = "MEMORY OVERLOAD: System resources exceeded.";
                }
            }
        }
    }

    // Method to set the queue area references from GameView
    public void setQueueAreaReferences(RectF newArea, RectF runningArea, RectF readyArea, RectF blockedArea) {
        this.newProcessAreaRef = newArea;
        this.runningQueueAreaRef = runningArea;
        this.readyQueueAreaRef = readyArea;
        this.blockedQueueAreaRef = blockedArea;
    }

    // Add this method to handle drops outside queues
    public void repositionProcessBasedOnCurrentState(Process process) {
        if (process == null) return;
        
        // Find which list it *should* be in based on its state
        // (We assume the state is correct even if dropped outside)
        // This implicitly calls repositionAllProcesses through the move methods
        switch (process.getState()) {
            case NEW:
                // Should technically not happen if it was dragged, but handle anyway
                if (!newProcesses.contains(process)) { // Add it back if somehow removed
                    removeProcessFromAllQueues(process);
                    newProcesses.add(process);
                    repositionAllProcesses(); 
                } else {
                    repositionAllProcesses(); // Just reposition existing
                }
                break;
            case RUNNING:
                moveToRunningQueue(process); 
                break;
            case READY:
                moveToReadyQueue(process); 
                break;
            case BLOCKED:
                moveToBlockedQueue(process); 
                break;
            case TERMINATED:
                // Should not be draggable, but remove if found
                removeProcessFromAllQueues(process);
                repositionAllProcesses();
                break;
        }
    }

    // Getters for queue capacities
    public int getMaxProcessesCapacity() { // Renamed for clarity
        return MAX_PROCESSES;
    }
    public int getRunningQueueCapacity() {
        return RUNNING_QUEUE_SIZE;
    }
    public int getReadyQueueCapacity() {
        return READY_QUEUE_SIZE;
    }
    public int getBlockedQueueCapacity() {
        return BLOCKED_QUEUE_SIZE;
    }

    // Getter for the new processes list
    public List<Process> getNewProcesses() {
        return newProcesses;
    }

    // Getter for the game over reason
    public String getGameOverReason() {
        return gameOverReason;
    }
} 