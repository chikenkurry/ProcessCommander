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
    
    // UI elements
    private RectF terminateButton;
    private RectF priorityUpButton;
    private RectF priorityDownButton;
    private RectF unblockButton;
    
    // Button labels
    private String terminateLabel = "Terminate";
    private String priorityUpLabel = "Priority +";
    private String priorityDownLabel = "Priority -";
    private String unblockLabel = "Unblock";
    
    // Button icons/symbols
    private String terminateSymbol = "✘";  // X symbol
    private String priorityUpSymbol = "▲";  // Up arrow
    private String priorityDownSymbol = "▼";  // Down arrow
    private String unblockSymbol = "►";  // Play button
    
    // Action popup
    private PopupWindow actionPopup;
    private Process selectedProcess;
    
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
        
        // Initialize action buttons
        terminateButton = new RectF();
        priorityUpButton = new RectF();
        priorityDownButton = new RectF();
        unblockButton = new RectF();
        
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
        isRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
        lastUpdateTime = System.currentTimeMillis();
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
        // Add top margin to avoid status bar interference
        float topMargin = statusBarHeight + 20; // Status bar height plus additional margin
        
        // Add bottom margin to avoid navigation bar interference
        float bottomMargin = navigationBarHeight + 20; // Navigation bar height plus additional margin
        
        // CPU Bar (top left)
        float barHeight = 60;
        float barWidth = width * 0.45f;
        float barMargin = 20;
        cpuBarBg.set(barMargin, topMargin, barMargin + barWidth, topMargin + barHeight);
        cpuBarFg.set(cpuBarBg);
        
        // Memory Bar (top right)
        memoryBarBg.set(width - barMargin - barWidth, topMargin, width - barMargin, topMargin + barHeight);
        memoryBarFg.set(memoryBarBg);
        
        // Action buttons (bottom)
        float buttonSize = 150;
        float buttonY = height - buttonSize - barMargin - bottomMargin;  // Account for navigation bar
        float buttonSpacing = 30;
        float buttonStartX = (width - (buttonSize * 4 + buttonSpacing * 3)) / 2;
        
        terminateButton.set(buttonStartX, buttonY, buttonStartX + buttonSize, buttonY + buttonSize);
        priorityUpButton.set(buttonStartX + buttonSize + buttonSpacing, buttonY, 
                          buttonStartX + buttonSize * 2 + buttonSpacing, buttonY + buttonSize);
        priorityDownButton.set(buttonStartX + buttonSize * 2 + buttonSpacing * 2, buttonY, 
                           buttonStartX + buttonSize * 3 + buttonSpacing * 2, buttonY + buttonSize);
        unblockButton.set(buttonStartX + buttonSize * 3 + buttonSpacing * 3, buttonY, 
                       buttonStartX + buttonSize * 4 + buttonSpacing * 3, buttonY + buttonSize);
        
        // Critical warning rectangle (center top)
        criticalWarningRect = new RectF(width * 0.1f, topMargin + barHeight + 40, 
                                      width * 0.9f, topMargin + barHeight + 140);
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
                
                // Set the game over reason
                if (processManager.getUsedCPU() >= processManager.getTotalCPU()) {
                    gameOverReason = "CPU OVERLOAD: Too many high-priority processes running simultaneously.";
                } else if (processManager.getUsedMemory() >= processManager.getTotalMemory()) {
                    gameOverReason = "MEMORY OVERLOAD: System ran out of available memory.";
                } else if (processManager.isEmergencyEvent()) {
                    gameOverReason = "CRITICAL FAILURE: Failed to handle emergency in time.";
                } else {
                    gameOverReason = "SYSTEM CRASH: Too many processes running simultaneously.";
                }
                
                // Notify game activity about game over
                if (context instanceof GameActivity) {
                    ((GameActivity) context).onGameOver(
                        processManager.getScore(),
                        processManager.getProcessesCompleted(),
                        processManager.getEmergencyEventsHandled(),
                        gameOverReason
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
                // Clear screen
                canvas.drawColor(Color.BLACK);
                
                // Draw resource bars
                drawResourceBars(canvas);
                
                // Draw processes
                processManager.draw(canvas, paint);
                
                // Draw action buttons
                drawActionButtons(canvas);
                
                // Draw scores and status
                drawStatusInfo(canvas);
                
                // Draw game over message if needed
                if (processManager.isGameOver()) {
                    drawGameOver(canvas);
                }
                
                // Draw instructions if needed
                if (showInstructions) {
                    drawInstructions(canvas);
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
    
    private void drawActionButtons(Canvas canvas) {
        // Button style - common properties
        float buttonRadius = 10f; // Rounded corners
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        // Terminate Button (Red X)
        drawButton(canvas, terminateButton, Color.rgb(200, 60, 60), terminateSymbol, terminateLabel);
        
        // Priority Up Button (Green arrow up)
        drawButton(canvas, priorityUpButton, Color.rgb(60, 180, 60), priorityUpSymbol, priorityUpLabel);
        
        // Priority Down Button (Blue arrow down)
        drawButton(canvas, priorityDownButton, Color.rgb(60, 120, 200), priorityDownSymbol, priorityDownLabel);
        
        // Unblock Button (Yellow Play)
        drawButton(canvas, unblockButton, Color.rgb(200, 180, 40), unblockSymbol, unblockLabel);
        
        // Reset typeface
        paint.setTypeface(Typeface.DEFAULT);
    }
    
    // Helper method to draw a button with consistent styling
    private void drawButton(Canvas canvas, RectF bounds, int color, String symbol, String label) {
        // Button background with border
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(bounds, 15f, 15f, paint);
        
        // Button border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(bounds, 15f, 15f, paint);
        
        // Button symbol
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(bounds.width() * 0.5f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(symbol, bounds.centerX(), bounds.centerY() + bounds.width() * 0.15f, paint);
        
        // Button label
        paint.setTextSize(bounds.width() * 0.15f);
        canvas.drawText(label, bounds.centerX(), bounds.bottom + 30, paint);
    }
    
    private void drawStatusInfo(Canvas canvas) {
        float fontSize = 32; // Increased font size
        float headerFontSize = 34; // Slightly larger for headers
        
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
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Memory Usage:", memoryBarBg.right, memoryBarBg.top - 15, paint);
        paint.setTextSize(fontSize);
        int memoryPercentage = (int)((float)processManager.getUsedMemory() / processManager.getTotalMemory() * 100);
        canvas.drawText(memoryPercentage + "%", memoryBarBg.left - 20, memoryBarBg.top + memoryBarBg.height()/2 + fontSize/3, paint);
        
        // Score and stats
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(fontSize);
        int screenWidth = getWidth();
        int y = (int)(memoryBarBg.bottom + 50);
        canvas.drawText("Score: " + processManager.getScore(), screenWidth/2, y, paint);
        y += fontSize + 10;
        canvas.drawText("Processes: " + processManager.getProcessesCompleted(), screenWidth/2, y, paint);
        
        // Draw emergency warning if active
        if (processManager.isEmergencyEvent()) {
            drawCriticalWarning(canvas);
        }
        
        // Selection instruction when no process is selected
        if (processManager.getSelectedProcess() == null) {
            paint.setTextSize(fontSize);
            paint.setColor(Color.YELLOW);
            y = getHeight() - 220; // Position above buttons
            canvas.drawText("Tap a process to select it", screenWidth/2, y, paint);
        } else {
            // Display selected process info
            Process selected = processManager.getSelectedProcess();
            paint.setTextSize(fontSize);
            paint.setColor(Color.YELLOW);
            y = getHeight() - 220; // Position above buttons
            canvas.drawText("Selected: " + selected.getName() + " (Priority: " + selected.getPriority() + ")", 
                           screenWidth/2, y, paint);
        }
        
        // Reset text properties
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
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
    
    private void drawInstructions(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // Semi-transparent background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, paint);
        
        // Title
        paint.setColor(Color.YELLOW);
        paint.setTextSize(70);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("HOW TO PLAY", width/2, height/4 - 80, paint);
        
        // Instructions text
        paint.setColor(Color.WHITE);
        paint.setTextSize(36);
        paint.setTypeface(Typeface.DEFAULT);
        
        String[] instructions = {
            "• Tap a process circle to select it",
            "• Manage selected process using buttons below:",
            "• " + terminateSymbol + " Terminate - Remove a process immediately",
            "• " + priorityUpSymbol + " Priority + - Increase process importance",
            "• " + priorityDownSymbol + " Priority - - Decrease process importance",
            "• " + unblockSymbol + " Unblock - Activate a blocked process",
            "• Monitor CPU and memory usage at top of screen",
            "• Higher priority processes use more CPU resources"
        };
        
        // Critical instructions with emphasis
        String[] criticalInstructions = {
            "❗ CRITICAL PROCESSES MUST BE UNBLOCKED IMMEDIATELY ❗",
            "• Ignoring critical processes causes system penalties",
            "• After 3 ignored critical processes, game over",
            "• Critical processes display flashing borders"
        };
        
        int y = height/4 - 20;
        for (String instruction : instructions) {
            canvas.drawText(instruction, width/2, y, paint);
            y += 50;
        }
        
        // Draw critical instructions with extra emphasis
        y += 20; // Add some spacing
        paint.setColor(Color.RED);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        for (String criticalInstruction : criticalInstructions) {
            canvas.drawText(criticalInstruction, width/2, y, paint);
            y += 50;
        }
        
        // Tap to continue
        paint.setColor(Color.CYAN);
        paint.setTextSize(40);
        canvas.drawText("TAP ANYWHERE TO START GAME", width/2, height - 100, paint);
        
        // Reset text properties
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float touchX = event.getX();
            float touchY = event.getY();
            
            // If instructions are shown, dismiss them on touch
            if (showInstructions) {
                showInstructions = false;
                gamePausedForInstructions = false;
                return true;
            }
            
            // If game is over, ignore touches
            if (processManager.isGameOver()) {
                return true;
            }
            
            // Check if a button was pressed (if process is selected)
            if (processManager.getSelectedProcess() != null) {
                if (terminateButton.contains(touchX, touchY)) {
                    processManager.terminateProcess(processManager.getSelectedProcess());
                    processManager.selectProcess(null);
                    return true;
                } else if (priorityUpButton.contains(touchX, touchY)) {
                    processManager.increasePriority(processManager.getSelectedProcess());
                    return true;
                } else if (priorityDownButton.contains(touchX, touchY)) {
                    processManager.decreasePriority(processManager.getSelectedProcess());
                    return true;
                } else if (unblockButton.contains(touchX, touchY)) {
                    processManager.unblockProcess(processManager.getSelectedProcess());
                    return true;
                }
            }
            
            // Check if a process was tapped
            Process touchedProcess = processManager.findProcessAtPosition(touchX, touchY);
            if (touchedProcess != null) {
                processManager.selectProcess(touchedProcess);
                
                // If this is a critical process and emergency is active, highlight unblock button
                if (touchedProcess.getName().startsWith("CRITICAL") && processManager.isEmergencyEvent()) {
                    // Visual feedback to help user know what to do
                    paint.setColor(Color.YELLOW);
                    paint.setStrokeWidth(8f);
                    paint.setStyle(Paint.Style.STROKE);
                    Canvas c = surfaceHolder.lockCanvas();
                    if (c != null) {
                        c.drawRect(unblockButton, paint);
                        surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            } else {
                processManager.selectProcess(null);
            }
            return true;
        }
        
        return super.onTouchEvent(event);
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
} 