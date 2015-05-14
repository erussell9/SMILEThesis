package com.teamroboface.smilethesis;

/**
 * Created by heirlab4 on 5/14/15.
 */
import java.util.List;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Toast;

import com.teamroboface.smilethesis.R;
import com.teamroboface.smilethesis.FaceActivity;

public class StartSmile extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_smile);

        // Set up start button - when clicked, it starts face activity
        final View startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(
                        StartSmile.this,
                        FaceActivity.class);
                startActivity(intent);//This will start the FaceActivity, where animations and voice recognition take place
                finish();//end the start screen intent, so that the app will exit when the home button is pressed from
                //face_activity instead of returning to the home screen
            }
        });

        // Disable button and alert user if no recognition service is present
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0) {
            startButton.setEnabled(false);
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(this, "Voice Recognizer Not Present", duration);
            toast.show();
        }
    }
}