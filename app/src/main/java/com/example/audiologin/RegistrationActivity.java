package com.example.audiologin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RegistrationActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private GestureDetector gestureDetector;
    private List<Integer> selectedSounds;
    private EditText etUsername;
    private int[] soundFiles = {R.raw.cat, R.raw.cow, R.raw.crow, R.raw.sheep};
    private String[] animalNames = {"Cat", "Cow", "Crow", "Sheep"};
    private int soundIndex = 0;
    private boolean isSelectingSounds = false;
    private List<String> selectedAnimals;
    private int maxSounds = 2;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        dbRef = FirebaseDatabase.getInstance("https://audiologin-951fa-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users");

        etUsername = findViewById(R.id.etUsername);
        selectedSounds = new ArrayList<>();
        selectedAnimals = new ArrayList<>();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                startRegistrationInstructions();
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });

        etUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (tts != null && tts.isSpeaking()) {
                    tts.stop();
                }
                readUsernameAndStartSelection();
                return true;
            }
            return false;
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isSelectingSounds && selectedSounds.size() < maxSounds) {
                    addSoundSelection();
                }
                return true;
            }
        });
    }

    private void startRegistrationInstructions() {
        tts.speak("Please enter your username.", TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void readUsernameAndStartSelection() {
        String username = etUsername.getText().toString().trim();
        if (!username.isEmpty()) {
            dbRef.child(username).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    tts.speak("Username already exists. Please choose a different username.", TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    isSelectingSounds = true;
                    tts.speak("You entered " + username, TextToSpeech.QUEUE_ADD, null, null);
                    tts.speak("Double-tap to select two animals as your audio password.", TextToSpeech.QUEUE_ADD, null, null);
                    new Handler().postDelayed(this::announceAnimalsWithDelay, 5000);
                }
            }).addOnFailureListener(e -> {
                tts.speak("An error occurred while checking the username. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null);
                Log.e("Firebase", "Error checking username existence", e);
            });
        } else {
            tts.speak("Please enter a valid username.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    private void announceAnimalsWithDelay() {
        soundIndex = 0;
        Handler handler = new Handler();
        Runnable announceNextAnimal = new Runnable() {
            @Override
            public void run() {
                if (selectedSounds.size() < maxSounds) {
                    if (soundIndex >= animalNames.length) {
                        soundIndex = 0;
                    }
                    tts.speak(animalNames[soundIndex], TextToSpeech.QUEUE_FLUSH, null, null);
                    soundIndex++;
                    handler.postDelayed(this, 3000);
                }
            }
        };
        handler.post(announceNextAnimal);
    }


    private void addSoundSelection() {
        if (soundIndex > 0 && selectedSounds.size() < maxSounds) {
            int selectedIndex = soundIndex - 1;
            selectedSounds.add(soundFiles[selectedIndex]);
            selectedAnimals.add(animalNames[selectedIndex]);

            int progress = selectedSounds.size();
            tts.speak(progress == 1
                    ? "First sound selected: " + animalNames[selectedIndex]
                    : "Second sound selected: " + animalNames[selectedIndex], TextToSpeech.QUEUE_FLUSH, null, null);

            if (progress == maxSounds) {
                confirmSelectionAndCompleteRegistration();
            }
        }
    }

    private void confirmSelectionAndCompleteRegistration() {
        String username = etUsername.getText().toString().trim();

        if (!username.isEmpty()) {
            try {
                List<String> lowercaseAnimals = new ArrayList<>();
                for (String animal : selectedAnimals) {
                    lowercaseAnimals.add(animal.toLowerCase().trim()); // Convert to lowercase and trim spaces
                }
                String audioPassword = String.join(",", lowercaseAnimals);

                String encryptedAudioPassword = CryptoHelper.encrypt(audioPassword);

                dbRef.child(username).child("AudioLogin").setValue(encryptedAudioPassword)
                        .addOnSuccessListener(aVoid -> Log.d("Firebase", "Audio password saved successfully!"))
                        .addOnFailureListener(e -> Log.e("Firebase", "Failed to save audio password", e));

                String animal1 = selectedAnimals.get(0);
                String animal2 = selectedAnimals.get(1);
                tts.speak("You have selected " + animal1 + " and " + animal2 + " as your password.", TextToSpeech.QUEUE_FLUSH, null, null);

                tts.speak("Registration complete. Redirecting to login.", TextToSpeech.QUEUE_ADD, null, null);

                long ttsDuration = 7000;
                new Handler().postDelayed(() -> {
                    startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
                    finish();
                }, ttsDuration);

            } catch (Exception e) {
                tts.speak("Error during encryption. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null);
                Log.e("Encryption", "Error encrypting audio password", e);
            }
        } else {
            tts.speak("Failed to save data. Username is empty.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }
    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}