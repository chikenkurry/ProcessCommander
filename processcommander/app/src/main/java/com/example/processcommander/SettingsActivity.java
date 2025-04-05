package com.example.processcommander;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "ProcessCommanderPrefs";
    private static final String PREF_DIFFICULTY = "difficulty";
    private static final String PREF_VIBRATION = "vibration";
    private static final String PREF_SOUND = "sound";
    
    private RadioGroup difficultyRadioGroup;
    private RadioButton easyRadioButton;
    private RadioButton mediumRadioButton;
    private RadioButton hardRadioButton;
    private Switch vibrationSwitch;
    private Switch soundSwitch;
    private Button saveSettingsButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialize UI elements
        difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        easyRadioButton = findViewById(R.id.easyRadioButton);
        mediumRadioButton = findViewById(R.id.mediumRadioButton);
        hardRadioButton = findViewById(R.id.hardRadioButton);
        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        soundSwitch = findViewById(R.id.soundSwitch);
        saveSettingsButton = findViewById(R.id.saveSettingsButton);
        
        // Load existing settings
        loadSettings();
        
        // Set up save button
        saveSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                Toast.makeText(SettingsActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load difficulty setting
        int difficulty = settings.getInt(PREF_DIFFICULTY, 1); // Default to medium
        switch (difficulty) {
            case 0:
                easyRadioButton.setChecked(true);
                break;
            case 1:
                mediumRadioButton.setChecked(true);
                break;
            case 2:
                hardRadioButton.setChecked(true);
                break;
        }
        
        // Load other settings
        vibrationSwitch.setChecked(settings.getBoolean(PREF_VIBRATION, true));
        soundSwitch.setChecked(settings.getBoolean(PREF_SOUND, true));
    }
    
    private void saveSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        
        // Save difficulty setting
        int difficulty;
        int selectedId = difficultyRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.easyRadioButton) {
            difficulty = 0;
        } else if (selectedId == R.id.mediumRadioButton) {
            difficulty = 1;
        } else {
            difficulty = 2;
        }
        editor.putInt(PREF_DIFFICULTY, difficulty);
        
        // Save other settings
        editor.putBoolean(PREF_VIBRATION, vibrationSwitch.isChecked());
        editor.putBoolean(PREF_SOUND, soundSwitch.isChecked());
        
        // Apply changes
        editor.apply();
    }
} 