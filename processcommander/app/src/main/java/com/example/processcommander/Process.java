package com.example.processcommander;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.UUID;

public class Process {
    // Process states
    public enum State {
        NEW("New"),
        READY("Ready"),
        RUNNING("Running"),
        BLOCKED("Blocked"),
        TERMINATED("Terminated");
        
        private String label;
        
        State(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }

    // Process attributes
    private final String id;
    private String name;
    private State state;
    private int priority;            // 1-10, higher means more important
    private long cpuBurstTime;        // Time needed to complete
    private long cpuTimeRemaining;    // Time left to complete
    private int memoryRequired;      // Memory needed by the process
    private long creationTime;       // When the process was created
    private float x, y;              // Position on screen
    private float targetX, targetY;  // Target position for animations
    private RectF bounds;            // Bounds for touch detection
    private boolean selected;        // Whether this process is selected by user
    private boolean isCritical;
    private boolean hasInterrupt;
    private boolean isIOCompleted;   // Added field for I/O completion status
    private String interruptReason;
    private float size;              // Size of the process visual representation
    private boolean dragging;        // Added field
    private boolean ioCompleted;     // Added field for I/O completion status

    // Visual properties
    private static final float DEFAULT_PROCESS_SIZE = 120f;
    private static final float TEXT_SIZE = 24f;

    private float animationTime = 0f;  // Time variable for animations
    private float pulseScale = 1.0f;   // Scale factor for pulsing effect

    public Process(String name, int priority, long cpuBurstTime, int memoryRequired) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.state = State.NEW;
        this.priority = priority;
        this.cpuBurstTime = cpuBurstTime;
        this.cpuTimeRemaining = cpuBurstTime;
        this.memoryRequired = memoryRequired;
        this.creationTime = System.currentTimeMillis();
        this.bounds = new RectF();
        this.selected = false;
        this.isCritical = name.startsWith("CRITICAL-");
        this.hasInterrupt = false;
        this.isIOCompleted = false;
        this.interruptReason = "";
        this.size = DEFAULT_PROCESS_SIZE;
        this.dragging = false; // Initialize dragging state
        this.ioCompleted = false; // Initialize ioCompleted state
    }

    public void update(float deltaTime) {
        // Update position with smooth animation
        float speedFactor = 5.0f * deltaTime;
        x += (targetX - x) * speedFactor;
        y += (targetY - y) * speedFactor;
        
        // Update bounds for touch detection
        bounds.set(x - size/2, y - size/2, x + size/2, y + size/2);
                  
        // If process is running, decrease remaining time
        if (state == State.RUNNING) {
            cpuTimeRemaining -= deltaTime * 1000; // Convert to milliseconds
            
            // Random chance to generate interrupt
            if (!hasInterrupt && !isCritical && Math.random() < 0.02 * deltaTime) { // 2% chance per second
                generateInterrupt();
            }
        }
        
        // Update animation time
        animationTime += deltaTime;
        
        // Update pulse effect for critical processes
        if (isCritical && state != State.TERMINATED) {
            // Pulsing effect
            pulseScale = 1.0f + 0.2f * (float)Math.sin(animationTime * 5.0);
        } else {
            pulseScale = 1.0f;
        }
    }

    private void generateInterrupt() {
        hasInterrupt = true;
        String[] interruptTypes = {
            "I/O Request",
            "Network Access",
            "Disk Operation",
            "User Input",
            "Device Signal"
        };
        interruptReason = interruptTypes[(int)(Math.random() * interruptTypes.length)];
        state = State.BLOCKED;
    }

    public void draw(Canvas canvas, Paint paint, GameView gameView) {
        // Determine visual properties based on state, selection, etc.
        int color = getColorForState();
        int alpha = 255;
        Paint.Style style = Paint.Style.FILL;
        float strokeWidth = 4f;
        boolean isBlinking = false;

        // Blinking logic for blocked processes with completed I/O
        if (state == State.BLOCKED && isIOCompleted) {
            if (gameView.shouldBlink()) {
                alpha = 100; // Dim when blinking off
                isBlinking = true;
            } else {
                alpha = 255; // Full opacity when blinking on
            }
        }
        
        // Process background
        paint.setColor(color);
        paint.setAlpha(alpha); 
        paint.setStyle(style);
        RectF bounds = getBounds();
        float cornerRadius = 15f;
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);

        // Border (thicker if selected or critical)
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255); // Border always full opacity
        if (selected) {
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(strokeWidth * 1.5f);
        } else if (name.startsWith("CRITICAL")) {
            paint.setColor(Color.RED);
            paint.setStrokeWidth(strokeWidth * 1.5f);
        } else {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(strokeWidth);
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);

        // Text color (adjust for blinking)
        if (isBlinking && alpha < 200) {
            paint.setColor(Color.LTGRAY); // Dim text when background is dim
        } else if (name.startsWith("CRITICAL")) {
            paint.setColor(Color.RED); // Critical text is red
        } else {
            paint.setColor(Color.WHITE);
        }
        paint.setAlpha(255); // Text always full opacity relative to its color
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        
        // Draw Process Name (adjust size)
        float nameTextSize = size * 0.18f;
        paint.setTextSize(nameTextSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(name, x, y - nameTextSize * 0.5f, paint);

        // Draw Priority and State (adjust size)
        float infoTextSize = size * 0.15f;
        paint.setTextSize(infoTextSize);
        paint.setTypeface(Typeface.DEFAULT);
        String stateLabel = (state == State.BLOCKED && isIOCompleted) ? "IO Done!" : state.getLabel(); // Show IO Done state
        canvas.drawText("P:" + priority + " | " + stateLabel, x, y + infoTextSize * 1.2f, paint);

        // Draw Interrupt Reason if any
        if (hasInterrupt && !interruptReason.isEmpty()) {
             paint.setColor(Color.YELLOW);
             paint.setTextSize(infoTextSize * 0.9f);
             canvas.drawText(interruptReason, x, y + infoTextSize * 2.4f, paint);
        }
        
        // Reset paint defaults
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setStrokeWidth(1f);
    }

    private int getColorForState() {
        switch (state) {
            case NEW:
                return Color.GRAY;
            case RUNNING:
                return Color.GREEN;
            case READY:
                return Color.BLUE;
            case BLOCKED:
                 return (isIOCompleted) ? Color.CYAN : Color.MAGENTA; // Cyan if IO Done, Magenta otherwise
            case TERMINATED:
                return Color.DKGRAY;
            default:
                return Color.WHITE;
        }
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        updateBounds();
    }

    public void setTargetPosition(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    public boolean contains(float touchX, float touchY) {
        return bounds.contains(touchX, touchY);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(5, priority));
    }

    public long getCpuBurstTime() {
        return cpuBurstTime;
    }

    public long getCpuTimeRemaining() {
        return cpuTimeRemaining;
    }

    public int getMemoryRequired() {
        return memoryRequired;
    }

    public void setMemoryRequired(int memoryRequired) {
        this.memoryRequired = memoryRequired;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public boolean isInterrupted() {
        return hasInterrupt;
    }

    public void clearInterrupt() {
        hasInterrupt = false;
        interruptReason = "";
    }

    public boolean isIOCompleted() {
        return isIOCompleted;
    }

    public void setIOCompleted(boolean completed) {
        this.isIOCompleted = completed;
    }

    public void setSize(float size) {
        this.size = size;
        // Update bounds when size changes
        updateBounds();
    }

    public float getSize() {
        return size;
    }

    public void setInterrupted(boolean interrupted) {
        this.hasInterrupt = interrupted;
        if (interrupted) {
            this.state = State.BLOCKED;
        }
    }

    private void updateBounds() {
        bounds.set(x - size/2, y - size/2, x + size/2, y + size/2);
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    private RectF getBounds() {
        return bounds;
    }
} 