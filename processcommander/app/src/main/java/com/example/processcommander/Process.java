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
        READY("Ready", Color.YELLOW),
        RUNNING("Running", Color.GREEN),
        BLOCKED("Blocked", Color.RED),
        TERMINATED("Terminated", Color.GRAY),
        CRITICAL("CRITICAL", Color.rgb(255, 50, 50));  // Brighter red for critical processes

        private final String label;
        private final int color;

        State(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public int getColor() {
            return color;
        }
    }

    // Process attributes
    private final String id;
    private final String name;
    private State state;
    private int priority;            // 1-10, higher means more important
    private int cpuBurstTime;        // Time needed to complete
    private int cpuTimeRemaining;    // Time left to complete
    private int memoryRequired;      // Memory needed by the process
    private long creationTime;       // When the process was created
    private float x, y;              // Position on screen
    private float targetX, targetY;  // Target position for animations
    private RectF bounds;            // Bounds for touch detection
    private boolean selected;        // Whether this process is selected by user

    // Visual properties
    private static final float PROCESS_SIZE = 220f;
    private static final float TEXT_SIZE = 30f;

    private float animationTime = 0f;  // Time variable for animations
    private boolean isCritical = false; // Flag for critical processes
    private float pulseScale = 1.0f;   // Scale factor for pulsing effect

    public Process(String name, int priority, int cpuBurstTime, int memoryRequired) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.state = State.READY;
        this.priority = priority;
        this.cpuBurstTime = cpuBurstTime;
        this.cpuTimeRemaining = cpuBurstTime;
        this.memoryRequired = memoryRequired;
        this.creationTime = System.currentTimeMillis();
        this.bounds = new RectF();
        this.selected = false;
        
        // Check if this is a critical process
        this.isCritical = name.startsWith("CRITICAL") || name.startsWith("WARNING");
        if (this.isCritical && name.startsWith("CRITICAL")) {
            this.state = State.CRITICAL; // Use special state for critical processes
        }
    }

    public void update(float deltaTime) {
        // Update position with smooth animation
        float speedFactor = 5.0f * deltaTime;
        x += (targetX - x) * speedFactor;
        y += (targetY - y) * speedFactor;
        
        // Update bounds for touch detection
        bounds.set(x - PROCESS_SIZE/2, y - PROCESS_SIZE/2, 
                  x + PROCESS_SIZE/2, y + PROCESS_SIZE/2);
                  
        // If process is running, decrease remaining time
        if (state == State.RUNNING) {
            cpuTimeRemaining -= (deltaTime * 1000); // Convert seconds to milliseconds
            if (cpuTimeRemaining <= 0) {
                cpuTimeRemaining = 0;
                state = State.TERMINATED;
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

    public void draw(Canvas canvas, Paint paint) {
        // Calculate effective size with pulse
        float effectiveSize = PROCESS_SIZE * pulseScale;
        
        // Draw process icon (circle with different colors based on state)
        paint.setColor(state.getColor());
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, effectiveSize/2, paint);
        
        // Draw border (thicker if selected or critical)
        paint.setStyle(Paint.Style.STROKE);
        if (isCritical) {
            // Draw attention-grabbing border for critical processes
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(selected ? 14f : 8f);
            canvas.drawCircle(x, y, effectiveSize/2, paint);
            
            // Draw second border with blinking effect for critical processes
            if ((int)(animationTime * 2) % 2 == 0) {
                paint.setColor(Color.YELLOW);
            } else {
                paint.setColor(Color.RED);
            }
            paint.setStrokeWidth(4f);
            canvas.drawCircle(x, y, effectiveSize/2 + 10, paint);
        } else {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(selected ? 12f : 6f);
            canvas.drawCircle(x, y, effectiveSize/2, paint);
        }
        
        // Draw process name
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(TEXT_SIZE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(name, x, y - 30, paint);
        
        // Draw process state
        if (isCritical) {
            // Make critical state text more noticeable
            paint.setColor(Color.YELLOW);
            paint.setTextSize(TEXT_SIZE * 1.2f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("CRITICAL - UNBLOCK NOW!", x, y + 10, paint);
        } else {
            canvas.drawText(state.getLabel(), x, y + 10, paint);
        }
        
        // Reset to normal text
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(TEXT_SIZE);
        paint.setColor(Color.WHITE);
        
        // Draw priority
        canvas.drawText("Priority: " + priority, x, y + 50, paint);
        
        // Draw progress indicator for running processes
        if (state == State.RUNNING) {
            float progress = (float) cpuTimeRemaining / cpuBurstTime;
            float progressWidth = PROCESS_SIZE * 0.8f;
            float progressHeight = 16f;
            
            RectF progressBg = new RectF(
                x - progressWidth/2, 
                y + 70,
                x + progressWidth/2, 
                y + 70 + progressHeight
            );
            
            // Background
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(progressBg, paint);
            
            // Progress
            paint.setColor(Color.GREEN);
            RectF progressFg = new RectF(progressBg);
            progressFg.right = progressFg.left + progressFg.width() * progress;
            canvas.drawRect(progressFg, paint);
        }
        
        // Draw memory info 
        if (state != State.TERMINATED) {
            paint.setTextSize(TEXT_SIZE * 0.9f);
            canvas.drawText("Mem: " + memoryRequired + "MB", x, y + 110, paint);
        }
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.bounds.set(x - PROCESS_SIZE/2, y - PROCESS_SIZE/2, 
                       x + PROCESS_SIZE/2, y + PROCESS_SIZE/2);
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
        // For critical processes, maintain visual style when being unblocked
        if (this.isCritical && state == State.READY) {
            this.state = State.RUNNING;
        } else {
            this.state = state;
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(10, priority)); // Clamp between 1-10
    }

    public int getCpuBurstTime() {
        return cpuBurstTime;
    }

    public int getCpuTimeRemaining() {
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
} 