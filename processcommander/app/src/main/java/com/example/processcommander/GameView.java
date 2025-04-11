package com.example.processcommander;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import java.util.ArrayList;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private SurfaceHolder surfaceHolder;
    private Thread gameThread;
    private volatile boolean isRunning;
    
    private Context context;
    private ProcessManager processManager;
    private Paint paint;
    
    // System UI margins
    private int statusBarHeight = 0;
    private int navigationBarHeight = 0;
    
    // Game state
    private long lastUpdateTime;
    private float deltaTime;
    private boolean gameOverHandled = false;
    private boolean showInstructions = true;  // Show instructions at startup
    private long instructionsTimer = 10000;   // Show instructions for 10 seconds
    private boolean gamePausedForInstructions = true; // Pause game while showing instructions
    private String gameOverReason = "";  // Reason for game over
    private float warningAnimTime = 0;  // Animation time for warnings
    private RectF criticalWarningRect; // Rectangle for critical warning display
    
    // Resource bars
    private RectF cpuBarBg, cpuBarFg;
    private RectF memoryBarBg, memoryBarFg;
    
    // Queue areas
    private RectF runningQueueArea;
    private RectF readyQueueArea;
    private RectF blockedQueueArea;
    private RectF newProcessArea;
    
    // Action popup
    private PopupWindow actionPopup;
    private Process selectedProcess;
    
    // Dragging state
    private boolean isDragging = false;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    
    // Constants for slot layout (Add these near other UI constants)
    private static final int SLOTS_PER_ROW = 3; 
    private static final float SLOT_SPACING = 15f;
    
    // UI Margins & Layout constants
    private static final int MARGIN_TOP = 150; // Adjusted top margin slightly
    private static final int MARGIN_BOTTOM = 50; // Reduced bottom margin as buttons are gone
    private static final int QUEUE_AREA_HEIGHT = 180; // Adjusted height for queues
    private static final int QUEUE_SPACING = 20;
    
    public GameView(Context context) {
        super(context);
        this.context = context;
        this.surfaceHolder = getHolder();
        this.surfaceHolder.addCallback(this);
        this.paint = new Paint();
        this.paint.setAntiAlias(true);
        
        // Calculate system UI margins
        calculateSystemUIMargins();
        
        // Initialize process manager
        this.processManager = new ProcessManager(context);
        
        // Initialize resource bars
        cpuBarBg = new RectF();
        cpuBarFg = new RectF();
        memoryBarBg = new RectF();
        memoryBarFg = new RectF();
        
        // Set focusable to receive touch events
        setFocusable(true);
    }
    
    // Calculate system UI margins for different devices
    private void calculateSystemUIMargins() {
        // Calculate status bar height
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        } else {
            statusBarHeight = 50; // Default fallback
        }
        
        // Calculate navigation bar height
        resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navigationBarHeight = getResources().getDimensionPixelSize(resourceId);
        } else {
            navigationBarHeight = 60; // Default fallback
        }
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Initialize UI elements based on actual dimensions
        updateUIElements(getWidth(), getHeight());

        // ---> Set Queue Area References in ProcessManager <--- 
        // Pass the calculated areas to the ProcessManager so it knows where to place processes
        if (processManager != null) {
             processManager.setQueueAreaReferences(newProcessArea, runningQueueArea, readyQueueArea, blockedQueueArea);
             processManager.repositionAllProcesses(); // Initial positioning in slots
        }
        
        // Initialize game loop
        if (gameThread == null || !gameThread.isAlive()) { // Check if thread is alive
            gameThread = new Thread(this);
        isRunning = true;
        gameThread.start();
        lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Update UI element sizes based on new dimensions
        updateUIElements(width, height);
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void updateUIElements(int width, int height) {
        // Calculate available height
        int availableHeight = height - MARGIN_TOP - MARGIN_BOTTOM - statusBarHeight - navigationBarHeight;
        int totalQueueSpacing = 3 * QUEUE_SPACING; // Spacing between 4 areas
        int calculatedQueueHeight = (availableHeight - totalQueueSpacing) / 4; // Divide height among 4 areas
        int queueHeight = Math.max(150, calculatedQueueHeight); // Ensure minimum height

        // Resource Bars (Top)
        float barWidth = width * 0.8f;
        float barHeight = 30f;
        float barMargin = (width - barWidth) / 2;
        cpuBarBg = new RectF(barMargin, MARGIN_TOP - 100, barMargin + barWidth, MARGIN_TOP - 100 + barHeight);
        cpuBarFg = new RectF(cpuBarBg);
        memoryBarBg = new RectF(barMargin, MARGIN_TOP - 50, barMargin + barWidth, MARGIN_TOP - 50 + barHeight);
        memoryBarFg = new RectF(memoryBarBg);

        // Queue Areas (Dynamic height, fixed width)
        float queueAreaWidth = width * 0.9f;
        float queueAreaMargin = (width - queueAreaWidth) / 2;
        float currentTop = (float) MARGIN_TOP;

        newProcessArea = new RectF(queueAreaMargin, currentTop, queueAreaMargin + queueAreaWidth, currentTop + queueHeight);
        currentTop += queueHeight + QUEUE_SPACING;
        runningQueueArea = new RectF(queueAreaMargin, currentTop, queueAreaMargin + queueAreaWidth, currentTop + queueHeight);
        currentTop += queueHeight + QUEUE_SPACING;
        readyQueueArea = new RectF(queueAreaMargin, currentTop, queueAreaMargin + queueAreaWidth, currentTop + queueHeight);
        currentTop += queueHeight + QUEUE_SPACING;
        blockedQueueArea = new RectF(queueAreaMargin, currentTop, queueAreaMargin + queueAreaWidth, currentTop + queueHeight);
    }
    
    @Override
    public void run() {
        while (isRunning) {
            // Calculate delta time
            long currentTime = System.currentTimeMillis();
            deltaTime = (currentTime - lastUpdateTime) / 1000.0f; // Convert to seconds
            lastUpdateTime = currentTime;
            
            // Limit deltaTime to avoid large jumps
            if (deltaTime > 0.1f) {
                deltaTime = 0.1f;
            }
            
            // Handle instructions
            if (showInstructions) {
                instructionsTimer -= (currentTime - lastUpdateTime);
                if (instructionsTimer <= 0) {
                    showInstructions = false;
                    gamePausedForInstructions = false;
                }
                
                // Just draw while instructions are showing, don't update game state
                draw();
                
                // Skip the rest of the loop if paused for instructions
                if (gamePausedForInstructions) {
                    try {
                        Thread.sleep(16); // ~60fps
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
            }
            
            // Update game state
            update(deltaTime);
            
            // Draw the game
            draw();
            
            // Check for game over
            if (processManager.isGameOver() && !gameOverHandled) {
                gameOverHandled = true;
                
                // Get the specific reason from ProcessManager
                String reason = processManager.getGameOverReason();
                
                // Notify game activity about game over
                if (context instanceof GameActivity) {
                    ((GameActivity) context).onGameOver(
                        processManager.getScore(),
                        processManager.getProcessesCompleted(),
                        processManager.getEmergencyEventsHandled(),
                        reason // Pass the reason
                    );
                }
            }
            
            // Control frame rate (optional)
            try {
                Thread.sleep(16); // ~60fps
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void update(float deltaTime) {
        // Update process manager
        processManager.update(deltaTime);
        
        // Update UI elements
        updateResourceBars();
        
        // Update animation time for warnings
        warningAnimTime += deltaTime;
    }
    
    private void updateResourceBars() {
        // Update CPU bar
        float cpuPercentage = (float) processManager.getUsedCPU() / processManager.getTotalCPU();
        cpuBarFg.right = cpuBarBg.left + cpuBarBg.width() * cpuPercentage;
        
        // Update Memory bar
        float memPercentage = (float) processManager.getUsedMemory() / processManager.getTotalMemory();
        memoryBarFg.right = memoryBarBg.left + memoryBarBg.width() * memPercentage;
    }
    
    private void draw() {
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.BLACK);
                
                // Draw resource bars FIRST
                drawResourceBars(canvas);
                
                // Draw Queue Backgrounds/Info SECOND
                drawQueueInfo(canvas, newProcessArea, "New Processes", processManager.getNewProcesses().size(), processManager.getMaxProcessesCapacity(), Color.DKGRAY);
                drawQueueInfo(canvas, runningQueueArea, "Running Queue", processManager.getRunningQueueSize(), processManager.getRunningQueueCapacity(), Color.rgb(0, 50, 0));
                drawQueueInfo(canvas, readyQueueArea, "Ready Queue", processManager.getReadyQueueSize(), processManager.getReadyQueueCapacity(), Color.rgb(0, 0, 50));
                drawQueueInfo(canvas, blockedQueueArea, "Blocked Queue", processManager.getBlockedQueueSize(), processManager.getBlockedQueueCapacity(), Color.rgb(50, 0, 0));

                // Draw Processes THIRD (on top of queues)
                processManager.draw(canvas, paint, this); // Pass GameView for blinking logic

                // Draw scores and status (includes instruction overlay logic)
                drawStatusInfo(canvas);
                
                // Draw warnings / game over
                if (processManager.isEmergencyEvent()) {
                    drawCriticalWarning(canvas);
                }
                if (processManager.isGameOver()) {
                    drawGameOver(canvas);
                }
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
    
    private void drawResourceBars(Canvas canvas) {
        // Draw CPU bar background with border
        paint.setColor(Color.DKGRAY);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(cpuBarBg, paint);
        
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f);
        canvas.drawRect(cpuBarBg, paint);
        
        // Set color based on usage (green->yellow->red)
        float cpuUsage = (float) processManager.getUsedCPU() / processManager.getTotalCPU();
        paint.setStyle(Paint.Style.FILL);
        if (cpuUsage < 0.5f) {
            paint.setColor(Color.GREEN);
        } else if (cpuUsage < 0.8f) {
            paint.setColor(Color.YELLOW);
        } else {
            paint.setColor(Color.RED);
        }
        canvas.drawRect(cpuBarFg, paint);
        
        // Draw CPU label
        paint.setColor(Color.WHITE);
        paint.setTextSize(24);  // Increased from 16
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("CPU: " + processManager.getUsedCPU() + "%", 
                       cpuBarBg.left, cpuBarBg.bottom + 30, paint);
        
        // Draw Memory bar background with border
        paint.setColor(Color.DKGRAY);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(memoryBarBg, paint);
        
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f);
        canvas.drawRect(memoryBarBg, paint);
        
        // Set color based on usage
        float memUsage = (float) processManager.getUsedMemory() / processManager.getTotalMemory();
        paint.setStyle(Paint.Style.FILL);
        if (memUsage < 0.5f) {
            paint.setColor(Color.GREEN);
        } else if (memUsage < 0.8f) {
            paint.setColor(Color.YELLOW);
        } else {
            paint.setColor(Color.RED);
        }
        canvas.drawRect(memoryBarFg, paint);
        
        // Draw Memory label
        paint.setColor(Color.WHITE);
        paint.setTextSize(24);  // Increased from 16
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Memory: " + processManager.getUsedMemory() + "/" + 
                       processManager.getTotalMemory() + " MB", 
                       memoryBarBg.left, memoryBarBg.bottom + 30, paint);
    }
    
    private void drawQueueInfo(Canvas canvas, RectF area, String queueName, int currentSize, int maxSize, int color) {
        // Draw queue area background
        paint.setColor(color);
        paint.setAlpha(180); // Slightly transparent background
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(area, 15f, 15f, paint);

        // Draw queue area border
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(area, 15f, 15f, paint);

        // Draw queue title
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(30);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(queueName, area.centerX(), area.top + 40, paint);

        // Draw queue capacity text
        paint.setTextSize(24);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(currentSize + " / " + maxSize, area.centerX(), area.top + 75, paint);
        
        // --- Draw Slot Outlines --- 
        float slotWidth = (area.width() - (SLOTS_PER_ROW + 1) * SLOT_SPACING) / SLOTS_PER_ROW;
        float slotHeight = (area.height() - 100 - (getRowCount(maxSize) + 1) * SLOT_SPACING) / getRowCount(maxSize); // Adjusted height calculation
        slotHeight = Math.min(slotHeight, slotWidth * 1.2f); // Keep slots reasonably proportioned
        
        paint.setColor(Color.argb(50, 255, 255, 255)); // Faint white for slot outlines
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        
        for (int i = 0; i < maxSize; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            
            float slotLeft = area.left + SLOT_SPACING + col * (slotWidth + SLOT_SPACING);
            float slotTop = area.top + 90 + SLOT_SPACING + row * (slotHeight + SLOT_SPACING); // Start below title/capacity text
            
            RectF slotRect = new RectF(slotLeft, slotTop, slotLeft + slotWidth, slotTop + slotHeight);
            canvas.drawRoundRect(slotRect, 10f, 10f, paint);
        }
        // --- End Slot Outlines ---

        paint.setTypeface(Typeface.DEFAULT); // Reset typeface
    }

    // Helper to get number of rows needed for slots
    private int getRowCount(int maxSize) {
        return (int) Math.ceil((double) maxSize / SLOTS_PER_ROW);
    }
    
    private void drawProcess(Canvas canvas, Process process) {
        float size = 150; // Bigger process size
        RectF bounds = new RectF(
            process.getX() - size/2,
            process.getY() - size/2,
            process.getX() + size/2,
            process.getY() + size/2
        );

        // Background color based on state
        int backgroundColor;
        if (process.isInterrupted()) {
            backgroundColor = Color.RED; // Red for interrupted
        } else if (process.isIOCompleted()) {
            backgroundColor = Color.GREEN; // Green for I/O completed
        } else {
            backgroundColor = Color.BLUE; // Default color
        }

        // Draw process background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(backgroundColor);
        paint.setAlpha(180);
        canvas.drawRoundRect(bounds, 20, 20, paint);

        // Draw border if selected
        if (process.isSelected()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);
            paint.setColor(Color.YELLOW);
            canvas.drawRoundRect(bounds, 20, 20, paint);
        }

        // Draw process info
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(24); // Bigger text
        paint.setTextAlign(Paint.Align.CENTER);

        // Draw process name
        canvas.drawText(process.getName(), bounds.centerX(), bounds.top + 40, paint);
        
        // Draw priority
        canvas.drawText("Priority: " + process.getPriority(), bounds.centerX(), bounds.centerY(), paint);
        
        // Draw CPU time remaining
        int secondsRemaining = (int)(process.getCpuTimeRemaining() / 1000);
        canvas.drawText(secondsRemaining + "s", bounds.centerX(), bounds.bottom - 40, paint);
    }
    
    // Draw a critical warning banner
    private void drawCriticalWarning(Canvas canvas) {
        // Animation color - flashing between red and yellow
        int warningColor;
        if ((int)(warningAnimTime * 2) % 2 == 0) {
            warningColor = Color.RED;
        } else {
            warningColor = Color.rgb(255, 200, 0); // Orange-yellow
        }
        
        // Draw background for warning
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(warningColor);
        canvas.drawRoundRect(criticalWarningRect, 15, 15, paint);
        
        // Draw border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4f);
        canvas.drawRoundRect(criticalWarningRect, 15, 15, paint);
        
        // Draw warning text
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        canvas.drawText("❗ CRITICAL EMERGENCY ❗", 
                       criticalWarningRect.centerX(), 
                       criticalWarningRect.centerY() - 15, 
                       paint);
        
        paint.setTextSize(32);
        canvas.drawText("Find and UNBLOCK the critical process!", 
                       criticalWarningRect.centerX(), 
                       criticalWarningRect.centerY() + 35, 
                       paint);
        
        // Reset text properties
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    
    private void drawGameOver(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // Semi-transparent background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, paint);
        
        // Game Over text
        paint.setColor(Color.RED);
        paint.setTextSize(80);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("GAME OVER", width/2, height/2 - 100, paint);
        
        // Reason for game over
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        String[] lines = splitTextIntoLines(gameOverReason, width - 100, paint);
        int y = height/2;
        for (String line : lines) {
            canvas.drawText(line, width/2, y, paint);
            y += 50;
        }
        
        // Score
        paint.setColor(Color.YELLOW);
        paint.setTextSize(60);
        canvas.drawText("Score: " + processManager.getScore(), width/2, height/2 + 150, paint);
        
        // Reset text properties
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    
    // Helper method to split text into lines
    private String[] splitTextIntoLines(String text, float maxWidth, Paint paint) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        ArrayList<String> lines = new ArrayList<>();
        
        for (String word : words) {
            if (paint.measureText(line + " " + word) <= maxWidth) {
                if (line.length() > 0) {
                    line.append(" ");
                }
                line.append(word);
            } else {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        
        return lines.toArray(new String[0]);
    }
    
    private void drawStatusInfo(Canvas canvas) {
        float fontSize = 32;
        float headerFontSize = 34;
        
        // If showing instructions, draw tutorial overlay
        if (showInstructions) {
            // Semi-transparent dark background
            paint.setColor(Color.argb(200, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            
            // Draw instruction box
            float boxMargin = 50;
            float boxWidth = getWidth() - 2 * boxMargin;
            float boxHeight = getHeight() - 2 * boxMargin;
            RectF instructionBox = new RectF(boxMargin, boxMargin, boxMargin + boxWidth, boxMargin + boxHeight);
            
            // Box background
            paint.setColor(Color.rgb(30, 30, 50));
            canvas.drawRoundRect(instructionBox, 20, 20, paint);
            
            // Box border
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(4);
            canvas.drawRoundRect(instructionBox, 20, 20, paint);
        
        // Title
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(headerFontSize * 1.2f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Process Commander Tutorial", getWidth()/2, boxMargin + 80, paint);
            
            // Instructions
            paint.setTextSize(fontSize * 0.9f);
            paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
            float instructionX = boxMargin + 50;
            float instructionY = boxMargin + 160;
            float lineSpacing = fontSize * 1.5f;
        
        String[] instructions = {
                "Welcome to Process Commander!",
                "",
                "Your goal is to manage system processes efficiently:",
                "",
                "• Drag processes between queues to manage them:",
                "  - Running Queue (Green): Active processes using CPU",
                "  - Ready Queue (Blue): Processes waiting to run",
                "  - Blocked Queue (Red): Processes waiting for I/O",
                "",
                "• Watch out for:",
                "  - Critical processes (Red text) - Handle immediately!",
                "  - Process interrupts (Yellow text) - Move to blocked queue",
                "  - Process starvation - Don't leave processes waiting too long",
                "  - CPU and Memory usage - Don't overload the system",
                "",
                "• Use the buttons at the bottom to:",
                "  - Terminate: Remove problematic processes",
                "  - Unblock: Move processes back to ready state",
                "",
                "Tap anywhere to start!"
            };
            
            for (String instruction : instructions) {
                canvas.drawText(instruction, instructionX, instructionY, paint);
                instructionY += lineSpacing;
            }
            
            // Draw pulsing "Tap to Start" at the bottom
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(fontSize);
            float alpha = (float) Math.abs(Math.sin(System.currentTimeMillis() / 500.0));
            paint.setColor(Color.argb((int)(255 * alpha), 255, 255, 255));
            canvas.drawText("TAP ANYWHERE TO START!", getWidth()/2, boxMargin + boxHeight - 50, paint);
            
            // Reset paint properties
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            return; // Don't draw other UI elements while showing instructions
        }
        
        // Regular UI drawing code
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        // CPU Usage
        paint.setTextSize(headerFontSize);
        canvas.drawText("CPU Usage:", cpuBarBg.left, cpuBarBg.top - 15, paint);
        paint.setTextSize(fontSize);
        int cpuPercentage = (int)((float)processManager.getUsedCPU() / processManager.getTotalCPU() * 100);
        canvas.drawText(cpuPercentage + "%", cpuBarBg.right + 20, cpuBarBg.top + cpuBarBg.height()/2 + fontSize/3, paint);
        
        // Memory Usage
        paint.setTextSize(headerFontSize);
        canvas.drawText("Memory Usage:", memoryBarBg.left, memoryBarBg.top - 15, paint);
        paint.setTextSize(fontSize);
        int memoryPercentage = (int)((float)processManager.getUsedMemory() / processManager.getTotalMemory() * 100);
        canvas.drawText(memoryPercentage + "%", memoryBarBg.right + 20, memoryBarBg.top + memoryBarBg.height()/2 + fontSize/3, paint);
        
        // Draw queue information
        float queueInfoY = memoryBarBg.bottom + 100;
        paint.setTextSize(headerFontSize);
        paint.setTextAlign(Paint.Align.CENTER);
        int screenWidth = getWidth();
        
        // Running Queue
        drawQueueInfo(canvas, runningQueueArea, "Running Queue", processManager.getRunningQueueSize(), 3, Color.rgb(60, 180, 60));
        
        // Ready Queue
        drawQueueInfo(canvas, readyQueueArea, "Ready Queue", processManager.getReadyQueueSize(), 5, Color.rgb(60, 60, 180));
        
        // Blocked Queue
        drawQueueInfo(canvas, blockedQueueArea, "Blocked Queue", processManager.getBlockedQueueSize(), 4, Color.rgb(180, 60, 60));
        
        // Reset color
        paint.setColor(Color.WHITE);
        
        // Draw score and stats
        paint.setTextSize(fontSize);
        float statsY = queueInfoY + 60;
        canvas.drawText("Score: " + processManager.getScore(), screenWidth/2, statsY, paint);
        statsY += fontSize + 10;
        canvas.drawText("Processes: " + processManager.getProcessesCompleted(), screenWidth/2, statsY, paint);
        
        // Draw emergency warning if active
        if (processManager.isEmergencyEvent()) {
            drawCriticalWarning(canvas);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle instruction screen touch first
        if (showInstructions && event.getAction() == MotionEvent.ACTION_DOWN) {
                showInstructions = false;
                gamePausedForInstructions = false;
                return true;
            }
            
        if (processManager.isGameOver() || gamePausedForInstructions) {
                return true;
            }
            
        float touchX = event.getX();
        float touchY = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = touchX;
                lastTouchY = touchY;
                
                // Check if touching a process to start dragging
                Process touchedProcess = processManager.findProcessAtPosition(touchX, touchY);
                if (touchedProcess != null) {
                    processManager.selectProcess(touchedProcess);
                    touchedProcess.setDragging(true); // Set dragging on the process itself
                    isDragging = true;
                    return true;
                }
                
                // If not touching a process or button, deselect
                processManager.selectProcess(null);
                isDragging = false;
                break; // Important: break here if no action taken
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging && processManager.getSelectedProcess() != null) {
                    Process draggedProcess = processManager.getSelectedProcess();
                    // Update position directly while dragging
                    draggedProcess.setPosition(touchX, touchY);
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
                if (isDragging && processManager.getSelectedProcess() != null) {
                    Process droppedProcess = processManager.getSelectedProcess();
                    droppedProcess.setDragging(false); // Stop dragging state
                    isDragging = false;
                    
                    // Check drop location and move process using ProcessManager
                    if (runningQueueArea.contains(touchX, touchY)) {
                        processManager.moveToRunningQueue(droppedProcess);
                    } else if (readyQueueArea.contains(touchX, touchY)) {
                        processManager.moveToReadyQueue(droppedProcess);
                    } else if (blockedQueueArea.contains(touchX, touchY)) {
                        processManager.moveToBlockedQueue(droppedProcess);
                    } else {
                        // Dropped outside a valid queue, reposition it based on its current state/queue
                        processManager.repositionProcessBasedOnCurrentState(droppedProcess);
                    }
                    
                    // Deselect after drop? Optional, but good practice
                    // processManager.selectProcess(null);
                    return true;
                }
                isDragging = false; // Ensure dragging flag is reset
                break;
        }
        
        return super.onTouchEvent(event); // Allow system handling if we didn't consume
    }
    
    public void pause() {
        isRunning = false;
        processManager.shutdown();
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void resume() {
        isRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
        lastUpdateTime = System.currentTimeMillis();
    }

    // Method for ProcessManager to get blink status (used in drawProcess)
    public boolean shouldBlink() {
        // Blink every half second
        return (System.currentTimeMillis() / 500) % 2 == 0;
    }
} 