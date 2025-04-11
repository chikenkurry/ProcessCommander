package com.example.processcommander;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity {
    private GameView gameView;
    private DatabaseHelper dbHelper;
    public static final String EXTRA_SCORE = "com.example.processcommander.SCORE";
    public static final String EXTRA_COMPLETED = "com.example.processcommander.COMPLETED";
    public static final String EXTRA_EMERGENCIES = "com.example.processcommander.EMERGENCIES";
    public static final String EXTRA_GAME_OVER_REASON = "com.example.processcommander.GAME_OVER_REASON"; // Key for reason
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Full screen settings
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
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
    public void onGameOver(int score, int processesCompleted, int emergenciesHandled, String reason) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SCORE, score);
        resultIntent.putExtra(EXTRA_COMPLETED, processesCompleted);
        resultIntent.putExtra(EXTRA_EMERGENCIES, emergenciesHandled);
        resultIntent.putExtra(EXTRA_GAME_OVER_REASON, reason); // Add reason to intent
        setResult(Activity.RESULT_OK, resultIntent); // Set result OK
        finish(); // Close GameActivity and return to MainActivity
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