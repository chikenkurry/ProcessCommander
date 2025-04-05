package com.example.processcommander;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    // Database info
    private static final String DATABASE_NAME = "processcommander.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table names
    private static final String TABLE_SCORES = "scores";
    
    // Common column names
    private static final String KEY_ID = "id";
    
    // SCORES table column names
    private static final String KEY_SCORE = "score";
    private static final String KEY_PROCESSES_COMPLETED = "processes_completed";
    private static final String KEY_EMERGENCIES_HANDLED = "emergencies_handled";
    private static final String KEY_DATE = "date";
    
    // Table create statements
    private static final String CREATE_TABLE_SCORES = "CREATE TABLE " + TABLE_SCORES + "("
            + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + KEY_SCORE + " INTEGER,"
            + KEY_PROCESSES_COMPLETED + " INTEGER,"
            + KEY_EMERGENCIES_HANDLED + " INTEGER,"
            + KEY_DATE + " DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ")";
    
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create tables
        db.execSQL(CREATE_TABLE_SCORES);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older tables if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCORES);
        
        // Create tables again
        onCreate(db);
    }
    
    // Score table methods
    
    /**
     * Save a new score to the database
     */
    public long saveScore(int score, int processesCompleted, int emergenciesHandled) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put(KEY_SCORE, score);
        values.put(KEY_PROCESSES_COMPLETED, processesCompleted);
        values.put(KEY_EMERGENCIES_HANDLED, emergenciesHandled);
        
        // Insert row
        long id = db.insert(TABLE_SCORES, null, values);
        
        return id;
    }
    
    /**
     * Get all scores ordered by highest score first
     */
    public List<Score> getAllScores() {
        List<Score> scores = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SCORES + " ORDER BY " + KEY_SCORE + " DESC";
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        // Loop through all rows and add to list
        if (cursor.moveToFirst()) {
            do {
                Score score = new Score();
                score.setId(cursor.getInt(cursor.getColumnIndex(KEY_ID)));
                score.setScore(cursor.getInt(cursor.getColumnIndex(KEY_SCORE)));
                score.setProcessesCompleted(cursor.getInt(cursor.getColumnIndex(KEY_PROCESSES_COMPLETED)));
                score.setEmergenciesHandled(cursor.getInt(cursor.getColumnIndex(KEY_EMERGENCIES_HANDLED)));
                score.setDate(cursor.getString(cursor.getColumnIndex(KEY_DATE)));
                
                scores.add(score);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        
        return scores;
    }
    
    /**
     * Get top 10 high scores
     */
    public List<Score> getTopScores(int limit) {
        List<Score> scores = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SCORES + " ORDER BY " + KEY_SCORE + " DESC LIMIT " + limit;
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        // Loop through all rows and add to list
        if (cursor.moveToFirst()) {
            do {
                Score score = new Score();
                score.setId(cursor.getInt(cursor.getColumnIndex(KEY_ID)));
                score.setScore(cursor.getInt(cursor.getColumnIndex(KEY_SCORE)));
                score.setProcessesCompleted(cursor.getInt(cursor.getColumnIndex(KEY_PROCESSES_COMPLETED)));
                score.setEmergenciesHandled(cursor.getInt(cursor.getColumnIndex(KEY_EMERGENCIES_HANDLED)));
                score.setDate(cursor.getString(cursor.getColumnIndex(KEY_DATE)));
                
                scores.add(score);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        
        return scores;
    }
    
    /**
     * Delete all scores
     */
    public void deleteAllScores() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SCORES, null, null);
    }
    
    /**
     * Close database
     */
    public void closeDB() {
        SQLiteDatabase db = this.getReadableDatabase();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
    
    /**
     * Score model class for storing score data
     */
    public static class Score {
        private int id;
        private int score;
        private int processesCompleted;
        private int emergenciesHandled;
        private String date;
        
        public Score() {
        }
        
        public Score(int score, int processesCompleted, int emergenciesHandled) {
            this.score = score;
            this.processesCompleted = processesCompleted;
            this.emergenciesHandled = emergenciesHandled;
        }
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }
        
        public int getScore() {
            return score;
        }
        
        public void setScore(int score) {
            this.score = score;
        }
        
        public int getProcessesCompleted() {
            return processesCompleted;
        }
        
        public void setProcessesCompleted(int processesCompleted) {
            this.processesCompleted = processesCompleted;
        }
        
        public int getEmergenciesHandled() {
            return emergenciesHandled;
        }
        
        public void setEmergenciesHandled(int emergenciesHandled) {
            this.emergenciesHandled = emergenciesHandled;
        }
        
        public String getDate() {
            return date;
        }
        
        public void setDate(String date) {
            this.date = date;
        }
    }
} 