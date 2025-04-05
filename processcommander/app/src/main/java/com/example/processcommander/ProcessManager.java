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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessManager {
    // Constants
    private static final int MAX_PROCESSES = 15;
    private static final int MAX_RUNNING_PROCESSES = 2;
    private static final long PROCESS_GEN_INTERVAL_MS = 5000; // Time between new process generation
    private static final String[] PROCESS_NAMES = {
            "Browser", "FileSystem", "Network", "Audio", "Video", 
            "SystemUI", "Kernel", "Memory", "Update", "Security", 
            "Backup", "Search", "Sync", "Bluetooth", "Wifi"
    };
    
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
    
    // Lists and collections
    private CopyOnWriteArrayList<Process> processes;
    private List<Process> runningProcesses;
    private Process selectedProcess;
    private Random random;
    private Vibrator vibrator;
    private Context context;
    
    // Flags and state
    private boolean emergencyEvent = false;
    private long emergencyStartTime = 0; // Track when emergency started
    private int emergencyTimeoutSeconds = 15; // How long before emergency escalates
    private int criticalPenaltyCount = 0; // Track how many critical tasks were ignored
    private int score = 0;
    private int processesCompleted = 0;
    private int emergencyEventsHandled = 0;
    private boolean gameOver = false;
    private static final int CRITICAL_GRACE_PERIOD = 5; // Grace period in seconds before critical processes can have penalties
    
    // Threading
    private ExecutorService threadPool;
    private volatile boolean isRunning = true;
    
    public ProcessManager(Context context) {
        this.context = context;
        this.processes = new CopyOnWriteArrayList<>();
        this.runningProcesses = new ArrayList<>();
        this.random = new Random();
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.threadPool = Executors.newFixedThreadPool(3); // 3 threads for background tasks
        
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
                        if (processes.size() < maxProcessesByDifficulty && !gameOver) {
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
    
    private void generateNewProcess() {
        // Don't generate if we're already at capacity
        // Capacity increases slightly as game progresses in easy mode
        int currentMaxProcesses = maxProcessesByDifficulty;
        if (difficultyLevel == 0) {
            // In easy mode, gradually increase the max processes cap over time
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed > 5) {
                currentMaxProcesses += Math.min(4, (int)(minutesElapsed / 5)); // +1 every 5 minutes, up to +4
            }
        }
        
        if (processes.size() >= currentMaxProcesses) {
            return;
        }
        
        // Generate random process attributes based on difficulty and progression
        String name = PROCESS_NAMES[random.nextInt(PROCESS_NAMES.length)] + "-" + random.nextInt(100);
        
        // Adjust priorities based on difficulty and game progression
        int priority;
        if (difficultyLevel == 0) { // Easy
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 3) {
                priority = random.nextInt(3) + 1; // 1-3 priority (very easy start)
            } else if (minutesElapsed < 8) {
                priority = random.nextInt(4) + 1; // 1-4 priority
            } else {
                priority = random.nextInt(5) + 1; // 1-5 priority (normal easy)
            }
        } else if (difficultyLevel == 2) { // Hard
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 2) {
                priority = random.nextInt(6) + 1; // 1-6 priority (easier start)
            } else if (minutesElapsed < 5) {
                priority = random.nextInt(7) + 2; // 2-8 priority
            } else {
                priority = random.nextInt(6) + 3; // 3-8 priority (gradually harder)
            }
        } else { // Medium
            priority = random.nextInt(8) + 1; // 1-8 priority
        }
        
        // Adjust burst times based on difficulty
        int cpuBurstTime;
        if (difficultyLevel == 0) { // Easy
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 5) {
                cpuBurstTime = (random.nextInt(6) + 7) * 1000; // 7-12 seconds (very easy start)
            } else {
                cpuBurstTime = (random.nextInt(6) + 5) * 1000; // 5-10 seconds (normal)
            }
        } else if (difficultyLevel == 2) { // Hard
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 3) {
                cpuBurstTime = (random.nextInt(5) + 4) * 1000; // 4-8 seconds (easier start)
            } else {
                cpuBurstTime = (random.nextInt(5) + 3) * 1000; // 3-7 seconds
            }
        } else { // Medium
            cpuBurstTime = (random.nextInt(8) + 3) * 1000; // 3-10 seconds
        }
        
        // Adjust memory requirements based on difficulty
        int memoryRequired;
        if (difficultyLevel == 0) { // Easy
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 5) {
                memoryRequired = (random.nextInt(80) + 40); // 40-120 MB (easier start)
            } else {
                memoryRequired = (random.nextInt(100) + 50); // 50-150 MB (normal)
            }
        } else if (difficultyLevel == 2) { // Hard
            float minutesElapsed = (System.currentTimeMillis() - gameStartTime) / 60000f;
            if (minutesElapsed < 3) {
                memoryRequired = (random.nextInt(100) + 70); // 70-170 MB (easier start)
            } else {
                memoryRequired = (random.nextInt(150) + 100); // 100-250 MB
            }
        } else { // Medium
            memoryRequired = (random.nextInt(150) + 75); // 75-225 MB
        }
        
        // Create and add the process
        Process process = new Process(name, priority, cpuBurstTime, memoryRequired);
        
        // Position the process on the screen using a grid-like pattern
        positionNewProcess(process);
        
        // Add to processes list
        processes.add(process);
    }
    
    private void positionNewProcess(Process process) {
        // Calculate available area for processes
        int availableWidth = screenWidth - 200; // 100px margin on each side
        int availableHeight = screenHeight - MARGIN_TOP - MARGIN_BOTTOM;
        
        // Use a grid-like approach for positioning
        int gridCols = 4; // Number of columns in the grid
        int gridRows = 4; // Number of rows in the grid
        
        int colWidth = availableWidth / gridCols;
        int rowHeight = availableHeight / gridRows;
        
        // Try to find an empty cell first
        boolean foundEmptyCell = false;
        
        // Create a 2D grid to track occupied cells
        boolean[][] occupiedCells = new boolean[gridRows][gridCols];
        
        // Mark cells as occupied based on existing processes
        for (Process p : processes) {
            int col = (int)((p.getX() - 100) / colWidth);
            int row = (int)((p.getY() - MARGIN_TOP) / rowHeight);
            
            // Check if within valid grid range
            if (col >= 0 && col < gridCols && row >= 0 && row < gridRows) {
                occupiedCells[row][col] = true;
            }
        }
        
        // Try to find an empty cell
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                if (!occupiedCells[row][col]) {
                    // Found an empty cell
                    float x = 100 + col * colWidth + colWidth / 2;
                    float y = MARGIN_TOP + row * rowHeight + rowHeight / 2;
                    
                    // Add some randomness to avoid perfect grid alignment
                    x += (random.nextFloat() - 0.5f) * colWidth * 0.5f;
                    y += (random.nextFloat() - 0.5f) * rowHeight * 0.5f;
                    
                    process.setPosition(x, y);
                    foundEmptyCell = true;
                    break;
                }
            }
            if (foundEmptyCell) break;
        }
        
        // If no empty cell was found, place randomly
        if (!foundEmptyCell) {
            float x = 100 + random.nextFloat() * availableWidth;
            float y = MARGIN_TOP + random.nextFloat() * availableHeight;
            process.setPosition(x, y);
        }
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
        processes.add(emergencyProcess);
    }
    
    public void update(float deltaTime) {
        if (gameOver) {
            return;
        }
        
        // Update difficulty progression based on game time
        updateDifficultyProgression();
        
        // Check emergency event timeout
        if (emergencyEvent) {
            long currentTime = System.currentTimeMillis();
            long emergencyDuration = (currentTime - emergencyStartTime) / 1000; // Duration in seconds
            
            // If emergency has been active too long without being handled
            // Only check for timeout if the grace period has passed
            if (emergencyDuration > emergencyTimeoutSeconds && emergencyDuration > CRITICAL_GRACE_PERIOD) {
                handleEmergencyTimeout();
            }
        }
        
        // Update all processes
        for (Process process : processes) {
            process.update(deltaTime);
        }
        
        // Update system resources
        updateResources();
        
        // Schedule processes
        scheduleProcesses();
        
        // Remove terminated processes
        Iterator<Process> iterator = processes.iterator();
        while (iterator.hasNext()) {
            Process process = iterator.next();
            if (process.getState() == Process.State.TERMINATED) {
                // Add score based on process completion (higher score for higher priority)
                score += process.getPriority() * 10;
                processesCompleted++;
                
                // Remove terminated process from running list
                runningProcesses.remove(process);
                
                // Remove from processes list with a delay
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000); // Keep terminated process visible for 1 second
                            processes.remove(process);
                            
                            // Clear selection if the terminated process was selected
                            if (process == selectedProcess) {
                                selectedProcess = null;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }).start();
            }
        }
        
        // Check if emergency has been handled
        if (emergencyEvent) {
            boolean criticalProcessesExist = false;
            for (Process process : processes) {
                if (process.getName().startsWith("CRITICAL-")) {
                    criticalProcessesExist = true;
                    
                    // Check if the critical process is running (unblocked)
                    if (process.getState() == Process.State.RUNNING) {
                        // Emergency is being handled, add score
                        emergencyEvent = false;
                        emergencyEventsHandled++;
                        score += 500; // Bonus for handling emergency
                        
                        // Reset critical penalty count when successfully handled
                        criticalPenaltyCount = 0;
                    } else if (process.getState() == Process.State.TERMINATED) {
                        // Emergency was handled successfully
                        emergencyEvent = false;
                        emergencyEventsHandled++;
                        score += 1000; // Bigger bonus for completing emergency
                        
                        // Reset critical penalty count when successfully handled
                        criticalPenaltyCount = 0;
                    }
                }
            }
            
            // If all critical processes are gone and emergency is still active
            if (!criticalProcessesExist && emergencyEvent) {
                emergencyEvent = false;
                
                // Emergency disappeared without being handled (unlikely but possible)
                // Still count this as a failure
                criticalPenaltyCount++;
                
                // Apply penalty but don't end game on first miss
                if (criticalPenaltyCount >= 3 || difficultyLevel > 0) {
                    gameOver = true;
                } else {
                    score = Math.max(0, score - 500); // Penalty
                }
            }
        }
        
        // Check for game over condition
        if ((usedCPU >= totalCPU || usedMemory >= totalMemory) && !gameOver) {
            // In easy mode, give a grace period before game over
            if (difficultyLevel == 0) {
                // Automatically terminate some lower priority processes
                sortAndTerminateLowPriorityProcesses();
            } else {
                gameOver = true;
            }
        }
    }
    
    // Helper method for easy mode: automatically terminate low priority processes
    private void sortAndTerminateLowPriorityProcesses() {
        // Get all running processes
        List<Process> allRunning = new ArrayList<>();
        for (Process p : processes) {
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
                runningProcesses.remove(p);
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
        // Calculate CPU usage based on running processes and their priorities
        usedCPU = 0;
        for (Process process : runningProcesses) {
            usedCPU += process.getPriority() * 10 * cpuUsageMultiplier * difficultyMultiplier;
        }
        
        // Calculate memory usage based on all non-terminated processes
        usedMemory = 0;
        for (Process process : processes) {
            if (process.getState() != Process.State.TERMINATED) {
                usedMemory += process.getMemoryRequired() * memoryUsageMultiplier * difficultyMultiplier;
            }
        }
    }
    
    private void scheduleProcesses() {
        // Limit number of running processes based on difficulty
        if (runningProcesses.size() < maxRunningProcessesByDifficulty) {
            // Sort processes by priority (highest first)
            ArrayList<Process> readyProcesses = new ArrayList<>();
            for (Process process : processes) {
                if (process.getState() == Process.State.READY) {
                    readyProcesses.add(process);
                }
            }
            
            if (!readyProcesses.isEmpty()) {
                Collections.sort(readyProcesses, new Comparator<Process>() {
                    @Override
                    public int compare(Process p1, Process p2) {
                        // Sort by priority (higher first)
                        return Integer.compare(p2.getPriority(), p1.getPriority());
                    }
                });
                
                // Start the highest priority process
                int canStart = maxRunningProcessesByDifficulty - runningProcesses.size();
                for (int i = 0; i < Math.min(canStart, readyProcesses.size()); i++) {
                    Process process = readyProcesses.get(i);
                    process.setState(Process.State.RUNNING);
                    runningProcesses.add(process);
                }
            }
        }
    }
    
    public void draw(Canvas canvas, Paint paint) {
        // Draw all processes
        for (Process process : processes) {
            process.draw(canvas, paint);
        }
    }
    
    public Process findProcessAtPosition(float x, float y) {
        // Iterate in reverse order to check top processes first
        for (int i = processes.size() - 1; i >= 0; i--) {
            Process process = processes.get(i);
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
            process.setState(Process.State.TERMINATED);
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
        for (Process process : processes) {
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
                positionNewProcess(warningProcess);
                processes.add(warningProcess);
            }
            
        } else if (criticalPenaltyCount >= 3) {
            // Third strike - game over on all difficulty levels
            gameOver = true;
        }
    }
} 