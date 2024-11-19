package com.example.audiologin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

public class  NotesActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_RECORD_AUDIO = 1;
    private EditText noteEditText;
    private Button voiceNoteButton;
    private Button logoutButton;
    private RecyclerView notesRecyclerView;
    private NotesAdapter notesAdapter;
    private ArrayList<String> notesList = new ArrayList<>();
    private String username;
    private TextToSpeech textToSpeech;
    private ActivityResultLauncher<Intent> speechRecognitionResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        noteEditText = findViewById(R.id.noteEditText);
        voiceNoteButton = findViewById(R.id.voiceNoteButton);
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        logoutButton = findViewById(R.id.logoutButton);

        username = getIntent().getStringExtra("USERNAME");
        notesAdapter = new NotesAdapter(notesList, note -> playNoteWithTTS(note));
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notesRecyclerView.setAdapter(notesAdapter);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());

                textToSpeech.speak("..Tap the upper button to dictate notes..", TextToSpeech.QUEUE_ADD, null, "utterance1");
                textToSpeech.speak("Tap the lower button to log out..", TextToSpeech.QUEUE_ADD, null, "utterance2");
                textToSpeech.speak("Tap the notes once to read the note..", TextToSpeech.QUEUE_ADD, null, "utterance3");
            } else {
                Toast.makeText(this, "TTS initialization failed.", Toast.LENGTH_SHORT).show();
            }
        });



        speechRecognitionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty()) {
                            noteEditText.setText(results.get(0));
                            saveNoteToFirebase(results.get(0));
                        } else {
                            Toast.makeText(this, "No speech detected. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        checkMicrophonePermission();
        loadNotesFromFirebase();

        voiceNoteButton.setOnClickListener(v -> startVoiceRecognition());

        logoutButton.setOnClickListener(v -> {
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            textToSpeech.speak("Logging out.", TextToSpeech.QUEUE_FLUSH, null, null);
            Toast.makeText(NotesActivity.this, "Logging out...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(NotesActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

    }

    private void checkMicrophonePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSION_RECORD_AUDIO
            );
        }
    }

    private void startVoiceRecognition() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to add a note");

            try {
                speechRecognitionResultLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Permission not granted. Please allow microphone access.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveNoteToFirebase(String note) {
        if (username != null && !username.isEmpty()) {
            DatabaseReference dbRef = FirebaseDatabase.getInstance("https://audiologin-951fa-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users")
                    .child(username)
                    .child("notes");

            String noteId = dbRef.push().getKey();
            if (noteId != null) {
                dbRef.child(noteId).setValue(note)
                        .addOnSuccessListener(aVoid -> Toast.makeText(NotesActivity.this, "Note saved successfully!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(NotesActivity.this, "Failed to save note.", Toast.LENGTH_SHORT).show());
            }
        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNotesFromFirebase() {
        if (username != null && !username.isEmpty()) {
            DatabaseReference dbRef = FirebaseDatabase.getInstance("https://audiologin-951fa-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users")
                    .child(username)
                    .child("notes");

            dbRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    notesList.clear();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String note = snapshot.getValue(String.class);
                        if (note != null) {
                            notesList.add(note);
                        }
                    }
                    notesAdapter.notifyDataSetChanged();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Toast.makeText(NotesActivity.this, "Failed to load notes.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void playNoteWithTTS(String note) {
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        textToSpeech.speak(note, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
