package com.example.processcommander;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

public class MainActivity extends Activity {

    private RadioGroup difficultyRadioGroup;
    private TextView lastScoreTextView; // TextView to display last score/reason
    private static final int GAME_ACTIVITY_REQUEST_CODE = 1; // Request code for starting GameActivity
    private static final String PREFS_NAME = "ProcessCommanderPrefs";
    private static final String PREF_DIFFICULTY = "difficulty";
    private static final String PREF_LAST_SCORE = "lastScore";
    private static final String PREF_LAST_REASON = "lastReason"; // Key to save reason

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startButton = findViewById(R.id.startButton);
        Button highScoresButton = findViewById(R.id.highScoresButton);
        difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        lastScoreTextView = findViewById(R.id.lastScoreTextView); // Initialize TextView

        // Load saved difficulty
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedDifficulty = settings.getInt(PREF_DIFFICULTY, 1); // Default to medium
        if (savedDifficulty == 0) {
            difficultyRadioGroup.check(R.id.radioEasy);
        } else if (savedDifficulty == 2) {
            difficultyRadioGroup.check(R.id.radioHard);
        } else {
            difficultyRadioGroup.check(R.id.radioMedium);
        }
        
        // Load and display last score and reason
        int lastScore = settings.getInt(PREF_LAST_SCORE, -1);
        String lastReason = settings.getString(PREF_LAST_REASON, "");
        updateLastScoreDisplay(lastScore, lastReason);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Save selected difficulty
                int selectedId = difficultyRadioGroup.getCheckedRadioButtonId();
                int difficulty = 1; // Default to medium
                if (selectedId == R.id.radioEasy) {
                    difficulty = 0;
                } else if (selectedId == R.id.radioHard) {
                    difficulty = 2;
                }
                saveDifficultySetting(difficulty);

                // Start game activity
                Intent intent = new Intent(MainActivity.this, GameActivity.class);
                startActivityForResult(intent, GAME_ACTIVITY_REQUEST_CODE); // Use startActivityForResult
            }
        });

        highScoresButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, HighScoresActivity.class);
                startActivity(intent);
            }
        });
    }
    
    private void saveDifficultySetting(int difficulty) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(PREF_DIFFICULTY, difficulty);
        editor.apply();
    }
    
    private void saveLastScore(int score, String reason) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(PREF_LAST_SCORE, score);
        editor.putString(PREF_LAST_REASON, reason); // Save the reason
        editor.apply();
    }
    
    private void updateLastScoreDisplay(int score, String reason) {
         if (score >= 0) {
            lastScoreTextView.setText("Last Game: Score " + score + " (" + reason + ")");
            lastScoreTextView.setVisibility(View.VISIBLE);
        } else {
            lastScoreTextView.setVisibility(View.GONE);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GAME_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                int score = data.getIntExtra(GameActivity.EXTRA_SCORE, 0);
                //int completed = data.getIntExtra(GameActivity.EXTRA_COMPLETED, 0);
                //int emergencies = data.getIntExtra(GameActivity.EXTRA_EMERGENCIES, 0);
                String reason = data.getStringExtra(GameActivity.EXTRA_GAME_OVER_REASON);
                if (reason == null) {
                    reason = "Game Ended"; // Default reason if null
                }
                
                // Save and display the score and reason
                saveLastScore(score, reason);
                updateLastScoreDisplay(score, reason);
                
                // Optional: Show a Toast message as well
                Toast.makeText(this, "Game Over! Reason: " + reason + " Score: " + score, Toast.LENGTH_LONG).show();
            }
        }
    }
} 