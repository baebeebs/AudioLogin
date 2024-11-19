package com.example.audiologin;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private Handler handler;
    private EditText etUsername;
    private GestureDetector gestureDetector;
    private String[] animalNames = {"cat", "cow", "crow", "sheep"};
    private int soundIndex = 0;
    private List<String> loginAnimals;
    private List<String> storedAnimals;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsernameLogin);
        loginAnimals = new ArrayList<>();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.speak("Please enter your username and press Enter to start login.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        handler = new Handler();
        shuffleSounds();

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (soundIndex > 0 && soundIndex <= animalNames.length) {
                    String selectedAnimal = animalNames[soundIndex - 1];
                    loginAnimals.add(selectedAnimal.toLowerCase().trim());
                    tts.speak(selectedAnimal + " selected.", TextToSpeech.QUEUE_FLUSH, null, null);

                    if (loginAnimals.size() == 2) {
                        handler.removeCallbacksAndMessages(null);
                        tts.speak("You have selected two sounds. Please wait for verification.", TextToSpeech.QUEUE_FLUSH, null, null);
                        verifyLogin();
                    }
                }
                return true;
            }
        });

        etUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                startLoginProcess();
                return true;
            }
            return false;
        });
    }

    private void shuffleSounds() {
        List<String> animalList = new ArrayList<>();
        Collections.addAll(animalList, animalNames);
        Collections.shuffle(animalList);
        animalList.toArray(animalNames);
    }

    private void startLoginProcess() {
        username = etUsername.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            tts.speak("Username cannot be empty. Please enter your username.", TextToSpeech.QUEUE_FLUSH, null, null);
            return;
        }

        DatabaseReference dbRef = FirebaseDatabase.getInstance("https://audiologin-951fa-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users")
                .child(username)
                .child("AudioLogin");

        tts.speak("Retrieving saved audio password.", TextToSpeech.QUEUE_FLUSH, null, null);

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    String encryptedPassword = snapshot.getValue(String.class);
                    if (encryptedPassword != null) {
                        String decryptedPassword = CryptoHelper.decrypt(encryptedPassword);
                        storedAnimals = new ArrayList<>();
                        Collections.addAll(storedAnimals, decryptedPassword.split(","));
                        tts.speak("Saved audio for " + username + " retrieved. Please select 2 of your saved animal sounds.", TextToSpeech.QUEUE_FLUSH, null, null);
                        handler.postDelayed(() -> playAllAnimalSounds(), 6000);

                    } else {
                        tts.speak("No audio password found for this username.", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                } catch (Exception e) {
                    tts.speak("Failed to retrieve password. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tts.speak("Failed to retrieve data. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

    }

    private void playAllAnimalSounds() {
        soundIndex = 0;
        loginAnimals.clear();

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (loginAnimals.size() < 2) {
                    if (soundIndex >= animalNames.length) {
                        soundIndex = 0;
                    }
                    String animal = animalNames[soundIndex];
                    playSound(animal);
                    soundIndex++;
                    handler.postDelayed(this, 2000);
                } else {
                    handler.removeCallbacksAndMessages(null);
                    tts.speak("You have selected two sounds. Please wait for verification.", TextToSpeech.QUEUE_FLUSH, null, null);
                    verifyLogin();
                }
            }
        });
    }

    private void playSound(String animal) {
        int soundResId = getSoundResourceId(animal);
        if (soundResId != -1) {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, soundResId);
            mediaPlayer.start();
            handler.postDelayed(() -> {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                }
            }, 2000);
        }
    }

    private int getSoundResourceId(String animal) {
        switch (animal.toLowerCase()) {
            case "cat":
                return R.raw.cat;
            case "cow":
                return R.raw.cow;
            case "crow":
                return R.raw.crow;
            case "sheep":
                return R.raw.sheep;
            default:
                return -1;
        }
    }

    private void verifyLogin() {
        List<String> formattedLoginAnimals = new ArrayList<>();
        for (String animal : loginAnimals) {
            formattedLoginAnimals.add(animal.toLowerCase().trim());
        }

        if (formattedLoginAnimals.equals(storedAnimals)) {
            tts.speak("Login successful. Welcome" + username + "!", TextToSpeech.QUEUE_FLUSH, null, null);
            handler.postDelayed(() -> {
                Intent intent = new Intent(LoginActivity.this, NotesActivity.class);
                intent.putExtra("USERNAME", username);
                startActivity(intent);
                finish();
            }, 2000);
        } else {
            tts.speak("Login failed. Incorrect audio password.", TextToSpeech.QUEUE_FLUSH, null, null);
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
