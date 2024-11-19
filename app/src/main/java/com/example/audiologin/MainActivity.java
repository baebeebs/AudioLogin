package com.example.audiologin;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.speak(" Welcome to Voice Vault. Swipe up to log in, or swipe down to register.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
        gestureDetector = new GestureDetector(this, new GestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e2.getY() < e1.getY()) {
                tts.speak("You have selected Login", TextToSpeech.QUEUE_FLUSH, null, null);
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            } else if (e2.getY() > e1.getY()) {
                tts.speak("You have selected Register", TextToSpeech.QUEUE_FLUSH, null, null);
                startActivity(new Intent(MainActivity.this, RegistrationActivity.class));
            }
            return true;
        }
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
