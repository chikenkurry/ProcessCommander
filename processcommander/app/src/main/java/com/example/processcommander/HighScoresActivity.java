package com.example.processcommander;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class HighScoresActivity extends Activity {

    private ListView highScoresListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_high_scores);

        highScoresListView = findViewById(R.id.highScoresListView);
        Button backButton = findViewById(R.id.backButton);

        displayDummyHighScores();

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Close this activity and return to the previous one (MainActivity)
            }
        });
    }

    private void displayDummyHighScores() {
        // For now, just display some dummy scores until the database is fully implemented
        List<String> scoreEntries = new ArrayList<>();
        scoreEntries.add("1. Score: 15000 - CPU Overload");
        scoreEntries.add("2. Score: 12500 - Memory Exceeded");
        scoreEntries.add("3. Score: 10200 - Critical Process Failures");
        scoreEntries.add("4. Score: 8700 - CPU Overload");
        scoreEntries.add("5. Score: 7200 - Memory Exceeded");
        scoreEntries.add("6. Score: 6500 - Critical Process Failures");
        scoreEntries.add("7. Score: 5800 - CPU Overload");
        scoreEntries.add("8. Score: 4200 - Memory Exceeded");
        scoreEntries.add("9. Score: 3600 - Process Starvation");
        scoreEntries.add("10. Score: 2900 - Critical Process Failures");

        // Create a simple array adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_1, // Built-in layout for simple text items
            scoreEntries
        );

        highScoresListView.setAdapter(adapter);
    }

    /* 
    // When you implement the real database functionality later:
    private void loadHighScoresFromDatabase() {
        // Get scores from the database
        // Create and set adapter
    }
    */
}
