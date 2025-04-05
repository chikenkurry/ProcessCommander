package com.example.processcommander;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity {
    private GameView gameView;
    private DatabaseHelper dbHelper;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on during gameplay
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Initialize database helper
        dbHelper = new DatabaseHelper(this);
        
        // Create and set the game view
        gameView = new GameView(this);
        setContentView(gameView);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }
    
    // Called by GameView when game is over
    public void onGameOver(final int score, final int processesCompleted, final int emergenciesHandled, final String reason) {
        // Save score to database
        long scoreId = dbHelper.saveScore(score, processesCompleted, emergenciesHandled);
        
        // Show game over dialog
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(GameActivity.this);
                
                // Set title and icon based on reason for game over
                if (reason.contains("CRITICAL FAILURE")) {
                    builder.setTitle("⚠️ CRITICAL FAILURE ⚠️");
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                } else {
                    builder.setTitle("GAME OVER");
                }
                
                // Build a more detailed message with specific advice based on the reason
                StringBuilder message = new StringBuilder();
                message.append("Reason: ").append(reason).append("\n\n");
                
                // Add specific advice based on the reason
                if (reason.contains("CRITICAL FAILURE")) {
                    message.append("Critical processes must be immediately unblocked!\n")
                           .append("Ignoring critical processes leads to system failure.\n\n");
                } else if (reason.contains("CPU OVERLOAD")) {
                    message.append("Try terminating lower priority processes or reducing the number of running processes.\n\n");
                } else if (reason.contains("MEMORY OVERLOAD")) {
                    message.append("Try terminating processes to free up memory resources.\n\n");
                }
                
                // Add score information
                message.append("Final Score: ").append(score).append("\n")
                       .append("Processes Completed: ").append(processesCompleted).append("\n")
                       .append("Emergencies Handled: ").append(emergenciesHandled);
                
                builder.setMessage(message.toString());
                builder.setPositiveButton("Return to Menu", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                
                // Add retry button if game ended due to critical failure
                if (reason.contains("CRITICAL FAILURE")) {
                    builder.setNegativeButton("Try Again", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Recreate the activity to restart the game
                            recreate();
                        }
                    });
                }
                
                builder.setCancelable(false);
                builder.show();
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close database connection
        if (dbHelper != null) {
            dbHelper.closeDB();
        }
    }
} 