package com.teamroboface.smilethesis;

/**
 * Created by heirlab4 on 5/14/15.
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.Random;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.teamroboface.smilethesis.R;
import com.teamroboface.smilethesis.EmotionalFace.Emotion;


public class FaceActivity extends Activity implements TextToSpeech.OnInitListener, SensorEventListener {

    private double speechPitch;
    private double speechRate;
    private static ImageView imgvw;
    private static Emotion currentEmotion;
    private String currentResponse;
    private long timeCompletedSpeaking;
    private TextToSpeech tts;
    private View speakButton;
    private SpeechRecognizer speechRecogNormal;
    private SpeechRecognizer speechRecogLearn;
    private SpeechRecognizer speechRecogCommand;
    private SpeechRecognizer speechRecogConvo;
    private BluetoothService bluetooth;
    private ProgressBar progress;
    private ToneGenerator tone;
    private boolean isListening;
    private listenerLearn learnListener;
    private listenerCommand commandListener;
    private listenerConvo convoListener;
    private static EmotionalFace face;
    private Sensor accelerometer;
    private SensorManager accelerometerManager;
    private boolean cameraMode;

    //TODO
    private int ROBOT_PORT = 9923;				// This should match the port number defined in the C++ server on the robot
    private String ROBOT_IP = "10.0.0.255";




    /**----------------------------------------------------------------------------------------------
     * Initialize face and speech recognition.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_activity);

		/*
		 * Set up text-to-speech
		 */
        tts = new TextToSpeech(this, this);

		/*
		 *  Initialize view containing face
		 */
        imgvw = (ImageView)this.findViewById(R.id.animationImage);
        imgvw.setVisibility(ImageView.VISIBLE);


		/*
		 *  Initialize face in neutral expression
		 */
        face = new EmotionalFace();
        try {
            face.loadWordDatabase(this);
        } catch (IOException e) {
            Log.e("exception", "Error loading saved keywords from memory: " + e.getMessage());
            speakOut("Error loading saved keywords from memory.");
        }
        try {
            face.loadResponseDatabase(this);
        } catch (IOException e) {
            Log.e("exception", "Error loading saved responses from memory: " + e.getMessage());
            speakOut("Error loading saved responses from memory.");
        }
        currentEmotion = Emotion.NEUTRAL;
        currentResponse = "";
        int initialExpression = EmotionalFace.transitions[currentEmotion.ordinal()][currentEmotion.ordinal()];
        imgvw.setBackgroundResource(initialExpression);
        AnimationDrawable initialanimation = (AnimationDrawable) imgvw.getBackground();
        initialanimation.start();

		/*
		 * Create speech recognizer for normal listening
		 */
        isListening = false;
        speechRecogNormal = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecogNormal.setRecognitionListener(new listenerNormal());

		/*
		 * Create speech recognizer for learning listening
		 */
        learnListener = new listenerLearn();
        speechRecogLearn = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecogLearn.setRecognitionListener(learnListener);

		/*
		 * Create speech recognizer for command mode listening
		 */
        commandListener = new listenerCommand();
        speechRecogCommand = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecogCommand.setRecognitionListener(commandListener);

        /*
		 * Create speech recognizer for conversation mode listening
		 */
        convoListener = new listenerConvo();
        speechRecogConvo = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecogConvo.setRecognitionListener(convoListener);

        /*
         * Create bluetooth service instance for activation if enter conversation mode
         */
        bluetooth = new BluetoothService(this);
        bluetooth.startBluetooth();

		/*
		 * Create progress bar to indicate when listening, and tone to indicate when stopped listening
		 */
        progress = (ProgressBar) findViewById(R.id.progressBar1);
        tone = new ToneGenerator(AudioManager.STREAM_DTMF, 100);

		/*
		 *  Create button so that a tap anywhere on the face starts normal listening
		 */
        speakButton = findViewById(R.id.animationImage);
        speakButton.setEnabled(true);
        speakButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
				/* **************** TO DISABLE CONVERSATION INTERFACE, COMMENT OUT THIS BLOCK ************/
                if (isListening) {
                    speechRecogNormal.cancel();						// If currently listening, cancel listening
                    speechRecogLearn.cancel();
                    speechRecogCommand.cancel();
                    speechRecogConvo.cancel();
                    learnListener.setStep(0);
                    convoListener.setStep(0);
                    isListening = false;
                    progress.setProgress(100);
                } else if (System.currentTimeMillis() - timeCompletedSpeaking > 750) {	// don't respond to any touches noticed while app is busy speaking
                    startListening(speechRecogNormal);				// this starts the activity for normal listening (i.e. not learning new words)
                }
 				/* ****************************************************************************************/
            }
        });

		/*
		 * Create accelerometer handlers
		 */
        accelerometerManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = accelerometerManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        accelerometerManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

    }


    /**----------------------------------------------------------------------------------------------
     * Helper method starts the speech listening activity for the passed speech recognizer.
     */
    private void startListening (SpeechRecognizer speechRecog) {
        while (tts.isSpeaking()){
            // Do nothing while TTS is speaking.
        }
        isListening = true;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"com.example.smile2");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
        speechRecog.startListening(intent);
    }

    /**----------------------------------------------------------------------------------------------
     * The normal speech listener opens when startListeningNormal() is called. It listens until speech seems
     * to be over, then sends the audio to the server and returns a set of possible matches in a string ArrayList.
     * The reactions to these matches are also defined within this inner class.
     * @author eliserussell
     *
     */
    class listenerNormal implements RecognitionListener
    {
        // Implement non-needed methods as empty methods.
        public void onReadyForSpeech(Bundle params) {}
        public void onPartialResults(Bundle partialResults) {}
        public void onEvent(int eventType, Bundle params) {}
        public void onBufferReceived(byte[] buffer) {}
        public void onEndOfSpeech() {}
        public void onBeginningOfSpeech() {}

        // Change progress bar to reflect changes in voice decibel level heard - this
        // makes it clear when the app is listening and when it is not.
        public void onRmsChanged(float rmsdB)
        {
            int prog = progress.getProgress();
            int goal = (int)rmsdB * 4 + 10;
            while (prog != goal) {
                if (prog < goal)
                    prog ++;
                else
                    prog --;
                progress.setProgress(prog);
            }
        }

        // When voice timeout, unrecognizeable speech, or audio recording error is encountered,
        // play tone and start listening again.  For all other errors, announce error number and stop
        // recognition cycle.
        public void onError(int error)
        {
            isListening = false;
            progress.setProgress(100);
            if ((error == 6) || (error == 7) || (error == 3)) {
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
                SystemClock.sleep(250);
                startListening(speechRecogNormal);
            } else {
                currentResponse = "Recognizer error number " + error;
                animate(Emotion.CONFUSED);
            }
        }

        // Normal conversational results have been recognized.  Empty progress bar and parse for keywords to react to.
        public void onResults(Bundle results)
        {
            isListening = false;
            progress.setProgress(0);
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); // Get list of phrases that are possible hits from voice recognizer
            String topHit = matches.get(0).toLowerCase(Locale.ENGLISH); // Only use the top hit, and control for case sensitivity - RESTRICTED TO ENGLISH LANGUAGE
            ArrayList<String> phrasesPresent = new ArrayList<String>(); // This list will contain all matching keyphrases and keywords, in order of recognizing them

            // If the top hit is recognized is a match in and of itself, make this the only item on the list.
            if (face.wordDictionary.containsKey(topHit)) {
                phrasesPresent.add(topHit);

                // Otherwise, search for keyphrases and then keywords.
            } else {

                for (String keyphrase : face.wordDictionary.keySet()) {                     // For all the keys in wordDictionary,
                    if ((keyphrase.contains(" ")) && (containsPhrase(topHit, keyphrase)))   // if the key is a phrase and it is contained in the top hit,
                        phrasesPresent.add(keyphrase);                                      // add it to the list.
                }


                // Parse top hit and search word by word for any keywords; add found keywords to the list.  They will come after any phrases recognized.
                String[] topHitSplit = topHit.split(" ");
                for (String word : topHitSplit) {
                    if (face.wordDictionary.containsKey(word))
                        phrasesPresent.add(word);
                }
            }



            // Now animate the appropriate expression for the FIRST keyword/phrase on the list (maybe later we'll figure out something to do with the rest)
            if (!topHit.equals("stop")) {

                // GOTCHA: if defining any hard-coded commands here (as in "learn" or "clear memory"),
                // these keys must also be hard-coded in the keyword lists or saved files.
                if (phrasesPresent.size() > 0) {

                    // Keyword "Learn" starts learning speech listener
                    if (phrasesPresent.get(0).equals("learn")) {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                startListening(speechRecogLearn);
                            }
                        };
                        currentResponse = "Okay, teach me something.";
                        animate(Emotion.HAPPY); //animate to happy
                        handler.postDelayed(runnable, 250); // start listening for learn protocol only AFTER animation and tts response are done

                        // Keyword "Clear memory" clears saved files of all added keywords, not including hard-coded ones.
                    } else if (phrasesPresent.get(0).equals("clear memory")) {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                startListening(speechRecogNormal);
                            }
                        };
                        currentResponse = "Okay, clearing memory";
                        clearMemory();
                        animate(Emotion.NEUTRAL);		//animate to neutral
                        handler.postDelayed(runnable, 250); // start listening again normally after finished speaking.

                        // Keyword "Command mode" starts command listener, so that robot can be controlled by voice commands.
                    } else if (phrasesPresent.get(0).equals("command mode")) {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                startListening(speechRecogCommand);
                            }
                        };
                        currentResponse = "Entering command mode.";
                        animate(Emotion.HAPPY); //animate to happy
                        handler.postDelayed(runnable, 250); // start listening for command protocol only AFTER animation and tts response are done

                        // Keyword "Conversation mode" starts conversation listener, so that robot can interact with topic detector.
                    } else if (phrasesPresent.get(0).equals("conversation mode")) {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                startListening(speechRecogConvo);
                            }
                        };
                        currentResponse = "Okay. Loading conversation mode. What subject should we talk about?";
                        // TODO: debug bluetooth starting stuff here
                        //bluetooth.write("new");// When app is client not server, here will need to be a command to start up query handler
                        animate(Emotion.HAPPY); //animate to happy
                        handler.postDelayed(runnable, 250); // start listening for conversation protocol only AFTER animation and tts response are done


                        // Keyword "Camera mode" sets animation to be only happy, no blinking, until camera mode is exited.
                    } else if (phrasesPresent.get(0).equals("camera mode")){
                        if (cameraMode) {
                            final Handler handler = new Handler();
                            final Runnable runnable = new Runnable() {
                                public void run() {
                                    startListening(speechRecogNormal);
                                }
                            };
                            cameraMode = false;
                            currentResponse = "Exiting camera mode";
                            currentEmotion = Emotion.HAPPY;
                            animate(Emotion.NEUTRAL);
                            handler.postDelayed(runnable, 250);	// Start listening again when camera mode exited
                        } else {
                            currentResponse = "Entering camera mode";
                            cameraMode = true;
                            animate(Emotion.HAPPY);
                        }

                        // All other keywords are dealt with here.
                    } else {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                startListening(speechRecogNormal);
                            }
                        };
                        currentResponse = face.responseDictionary.get(phrasesPresent.get(0)); //get the response if ones exists for this phrase, to speak when animation done
                        animate(face.wordDictionary.get(phrasesPresent.get(0))); //animate to the expression indicated by the emotion(value) that matches this string(key)
                        handler.postDelayed(runnable, 250); // start listening again only after animation and tts response are done
                    }

                    // If no keyword recognized, repeat back the entire speech input and start listening again immediately afterward.
                } else {
                    speakOut(topHit);
                    Log.d("THIS WAS HEARD: ", topHit);
                    startListening(speechRecogNormal);
                }

                // If "stop" is recognized, fill the progress bar, generate a low tone, and stop listening.
            } else {
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }
        }

    }




    /**----------------------------------------------------------------------------------------------
     * The learning speech listener opens when startListeningLearn() is called. It acts like the normal speech
     * listener, except that how it reacts to each input depends on its progress through a step-by-step learning
     * protocol.  The steps are advanced by incrementing a private variable after the successful completion of a
     * previous step.
     * @author eliserussell
     *
     */
    class listenerLearn implements RecognitionListener
    {
        // This is the step counter variable.  It is advanced when a step of the protocol is successfully
        // completed, and reset when protocol is completed or broken.
        private int step;
        public void setStep(int set) {		// Used if need to start on specific step of protocol out of order.
            step = set;
        }

        private String toBeLearned;

        // Implement non-needed methods as empty methods.
        public void onReadyForSpeech(Bundle params) {}
        public void onPartialResults(Bundle partialResults) {}
        public void onEvent(int eventType, Bundle params) {}
        public void onBufferReceived(byte[] buffer) {}
        public void onEndOfSpeech() {}
        public void onBeginningOfSpeech() {}

        // Change progress bar to reflect changes in voice decibel level heard - this
        // makes it clear when the app is listening and when it is not.
        public void onRmsChanged(float rmsdB)
        {
            int prog = progress.getProgress();
            int goal = (int)rmsdB * 4 + 10;
            while (prog != goal) {
                if (prog < goal)
                    prog ++;
                else
                    prog --;
                progress.setProgress(prog);
            }
        }

        // When voice timeout, unrecognized speech, or audio recording error are encountered,
        // do not advance protocol step; instead, ask for user to repeat what they just said.
        // For all other errors, announce error number and cancel learn protocol.
        public void onError(int error)
        {
            isListening = false;
            progress.setProgress(100);
            if ((error == 6) || (error == 7) || (error == 3)) {
                speakOut("Sorry, can you repeat that?");
                SystemClock.sleep(250);
                startListening(speechRecogLearn);
            } else {								// For all other errors, announce error number and stop listening.
                currentResponse = "Recognizer error number " + error;
                step = 0;
                toBeLearned = "";
                animate(Emotion.CONFUSED);
            }
        }

        // Depending on which step the protocol is on, the results are dealt with differently.  A switch case is used:
        public void onResults(Bundle results)
        {
            // First empty the progress bar
            isListening = false;
            progress.setProgress(0);
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); // Get list of phrases that are possible hits from voice recognizer
            String topHit = matches.get(0).toLowerCase(Locale.ENGLISH); // Only use the top hit, and control for case sensitivity - RESTRICTED TO ENGLISH LANGUAGE

            // If the user says stop, don't continue the protocol.
            if (!topHit.equalsIgnoreCase("stop")) {

                switch (step) {

                    case 0: // STEP ONE: SAVE NEW KEYWORD
                        toBeLearned = topHit;//saves the new word for putting it in the dictionary later
                        speakOut(topHit + "...  How does that make me feel?");
                        step = 1;
                        break;

                    case 1: // STEP TWO: SAVE ASSOCIATED EMOTION
                        try {
                            Emotion emotion = face.getEmotionKey(topHit);
                            if (currentEmotion != Emotion.HAPPY)		// If current expression was confused, now go back to happy
                                animate(Emotion.HAPPY);
                            if (emotion != null) {				// Save emotion. Or if keyword was "nothing," just advance protocol without saving any response.
                                face.wordDictionary.put(toBeLearned, emotion);    // Match the keyword from step one with the emotion and save them.
                                saveWord();
                            }
                            speakOut("Okay, got it. What should I say to that?");
                            step = 2;
                        }
                        catch(Exception e) {				// Expression was not recognized, need to get a valid expression word.
                            currentResponse = "Whoops, can you say another expression?";
                            animate(Emotion.CONFUSED);	//animate confused face
                        }
                        break;

                    case 2: // STEP THREE: SAVE ASSOCIATED VERBAL RESPONSE
                        if(!topHit.equals("nothing") && !topHit.equalsIgnoreCase("I don't know")){
                            speakOut("Okay, I'll say, " + topHit);		// "I don't know" and "nothing" are assumed to mean "don't save a response, just an expression"
                            face.responseDictionary.put(toBeLearned, topHit);	// Match keyword from step one with response and save them.
                            saveResponse();
                        }
                        step = 3;
                        break;

                    default:	// In the unlikely event that an invalid step number is passed, exit the protocol.
                        speakOut("Whoops! Exiting learn.");
                        step = 3;
                        break;

                }

                // Now either start listening for learn again or reset protocol step and start listening for normal speech.
                if (step < 3) {
                    final Handler handler = new Handler();
                    final Runnable runnable = new Runnable() {
                        public void run() {
                            startListening(speechRecogLearn);
                        }
                    };
                    handler.postDelayed(runnable, 180);	// Start listening only after tts and animations have finished.
                } else {
                    step = 0;		// Resets protocol so that next time it starts over at the beginning.
                    toBeLearned = "";
                    final Handler handler = new Handler();
                    final Runnable runnable = new Runnable() {
                        public void run() {
                            startListening(speechRecogNormal);
                        }
                    };
                    handler.postDelayed(runnable, 180);	// Start listening only after tts and animations have finished.
                }

                // If user says "stop," cancel protocol and stop listening.
            } else {
                step = 0;
                toBeLearned = "";
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }

        }

    }



    /**----------------------------------------------------------------------------------------------
     * The command speech listener opens when startListeningCommand() is called. It acts like the normal speech
     * listener, except that it compares its inputs to an enumeration of possible commands (defined at top).  When
     * it finds a match, it sends the message to the robot to execute that command.
     * @author eliserussell
     *
     */
    class listenerCommand implements RecognitionListener
    {
        // Implement non-needed methods as empty methods.
        public void onReadyForSpeech(Bundle params) {}
        public void onPartialResults(Bundle partialResults) {}
        public void onEvent(int eventType, Bundle params) {}
        public void onBufferReceived(byte[] buffer) {}
        public void onEndOfSpeech() {}
        public void onBeginningOfSpeech() {}

        // Change progress bar to reflect changes in voice decibel level heard - this
        // makes it clear when the app is listening and when it is not.
        public void onRmsChanged(float rmsdB)
        {
            int prog = progress.getProgress();
            int goal = (int)rmsdB * 4 + 10;
            while (prog != goal) {
                if (prog < goal)
                    prog ++;
                else
                    prog --;
                progress.setProgress(prog);
            }
        }

        // When voice timeout, unrecognized speech, or audio recording error are encountered,
        // start listening again - probably just heard robot moving.
        public void onError(int error)
        {
            isListening = false;
            progress.setProgress(100);
            if ((error == 6) || (error == 7) || (error == 3)) {
                SystemClock.sleep(250);
                startListening(speechRecogCommand);
            } else {								// For all other errors, announce error number and stop listening.
                currentResponse = "Recognizer error number " + error;
                animate(Emotion.CONFUSED);
            }
        }

        // When the voice recognition results are in, a switch case is used to find if any of them match a command:
        public void onResults(Bundle results)
        {
            // First empty the progress bar
            isListening = false;
            progress.setProgress(0);
            // Get list of phrases that are possible hits from voice recognizer
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String topHit = matches.get(0).toLowerCase(Locale.ENGLISH); // Only use the top hit, and control for case sensitivity - RESTRICTED TO ENGLISH LANGUAGE

            // Only continue if the top hit is not "stop" or "exit".
            if (!topHit.equals("stop") && !topHit.equals("exit")) {

                // Take care of commonly mismatched word "kik", for "kick"
                String commandMessage = "";
                if (topHit.equals("kik"))
                {
                    commandMessage = "kick";
                }
                else
                {
                    commandMessage = topHit;
                }

                //For any recognized command, send it to the robot for execution.
                if 		(commandMessage.equals("stand")
                        || commandMessage.equals("walk")
                        || commandMessage.equals("kick")
                        || commandMessage.equals("track")
                        || commandMessage.equals("relax")
                        || commandMessage.equals("engage"))
                {
                    speakOut("Okay, I'll " + commandMessage);
                    new Thread(new ClientThread(commandMessage, ROBOT_PORT, ROBOT_IP)).start(); //put the command in the socket
                }

                // If no recognized command, ask user to try again.

                else
                {
                    speakOut(topHit);
                    new Thread(new ClientThread(topHit, ROBOT_PORT, ROBOT_IP)).start(); //put the command in the socket
//					speakOut("Sorry, can you repeat that?");
                }

                // Then, start listening for another command.
                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        startListening(speechRecogCommand);
                    }
                };
                handler.postDelayed(runnable, 250);	// Start listening only after tts and animations have finished.


                // If user says "exit", say "Exiting command mode" and start listening normally instead.
            }
            else if (topHit.equals("exit")) {
                speakOut("Exiting Command Mode.");
                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        startListening(speechRecogNormal);
                    }
                };
                handler.postDelayed(runnable, 250);	// Start listening only after tts and animations have finished.

                // For consistency's sake, if user says "stop", fill the progress bar and stop listening.
            }
            else {
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }

        }

    }

    class listenerConvo implements RecognitionListener
    {
        // Class constants:
        private String[] topicLabels = {"pets", "life partners", "comedy", "time travel", "current events",
                "hobbies", "smoking", "televised criminal trials", "censorship",
                "health and fitness", "family", "outdoor activities", "friends",
                "food", "illness", "personal habits", "reality tv", "holidays"};
        private String[] topicPrompts = {"Do you have a pet? If so, how much time each day do you spend with your pet? How important is your pet to you?",
                "What do you think is the most important thing to look for in a life partner?",
                "How do you draw the line between acceptable humor and humor that is in bad taste?",
                "If you had the opportunity to go back in time and change something that you had done, what would it be and why?",
                "How do you keep up with current events? Do you get most of your news from TV, radio, newspapers, or people you know?",
                "What are your favorite hobbies? How much time do you spend pursuing your hobbies? Do you feel that every person needs at least one hobby?",
                "How do you feel about the movement to ban smoking in all public places? Do you think Smoking Prevention Programs, Counter-smoking ads, Help Quit hotlines and so on, are a good idea?",
                "Do you feel that criminal trials, especially those involving high-profile individuals, should be televised? Have you ever watched any high-profile trials on TV?",
                "Do you think public or private schools have the right to forbid students to read certain books?",
                "Do you exercise regularly to maintain your health or fitness level? If so, what do you do? If not, would you like to start?",
                "What does the word family mean to you?",
                "Do you like cold weather or warm weather activities the best? Do you like outside or inside activities better? Tell me about your favorite activities.",
                "Are you the type of person who has lots of friends and acquaintances or do you just have a few close friends? Tell me about your best friend or friends.",
                "Which do you like better--eating at a restaurant or at home? Describe your perfect meal.",
                "When the seasons change, many people get ill. Do you? What do you do to keep yourself well? There is a saying, \"A cold lasts seven days if you don't go to the doctor and a week if you do.\" Do you agree?",
                "According to you, which is worse: gossiping, smoking, drinking alcohol or caffeine excessively, overeating, or not exercising?",
                "Do you watch reality shows on TV? If so, which one or ones? Why do you think that reality based television programming, shows like \"Survivor\" or \"Who Wants to Marry a Millionaire\" are so popular?",
                "Do you have a favorite holiday? Why? If you could create a holiday, what would it be and how would you have people celebrate it?"};

        private String[] keepGoing = {"Go on", "interesting", "oh wow"};
        private String[] replyTemplates = { "It sounds like you're talking about <topic>.",
                "That sounds interesting. Please tell me more about <topic>!",
                "How do you feel about <topic>?",
                "Is <topic> related to that?",
                "<topic> sounds like a big subject.",
                "<topic> sure does sound interesting!",
                "I don't know much about <topic>. What do you think?",
                "How does <topic> relate to that?"};
        private String finalReplyTemplate = "You sure know a lot about <topic>. It was nice talking with you about all that!";

        // Class variables to track progress through conversation
        private int step = 0;
        public void setStep(int set) {		// Used if need to start on specific step of protocol out of order.
            step = set;
        }
        private String convoTopic = "";
        private String query = "";
        private int replies = 0;
        boolean prompted = false;
        private Random rand = new Random();

        // Implement non-needed methods as empty methods.
        public void onReadyForSpeech(Bundle params) {}
        public void onPartialResults(Bundle partialResults) {}
        public void onEvent(int eventType, Bundle params) {}
        public void onBufferReceived(byte[] buffer) {}
        public void onEndOfSpeech() {}
        public void onBeginningOfSpeech() {}

        // Change progress bar to reflect changes in voice decibel level heard - this
        // makes it clear when the app is listening and when it is not.
        public void onRmsChanged(float rmsdB)
        {
            int prog = progress.getProgress();
            int goal = (int)rmsdB * 4 + 10;
            while (prog != goal) {
                if (prog < goal)
                    prog ++;
                else
                    prog --;
                progress.setProgress(prog);
            }
        }

        // When voice timeout, unrecognized speech, or audio recording error are encountered,
        // start listening again - need to collect enough material for full query
        public void onError(int error)
        {
            isListening = false;
            progress.setProgress(100);
            if ((error == 6) || (error == 7) || (error == 3)) {
                SystemClock.sleep(250);
                startListening(speechRecogConvo);
                // For all other errors, announce error number and stop listening.
            } else {
                currentResponse = "Recognizer error number " + error;
                animate(Emotion.CONFUSED);
            }
        }

        // When the voice recognition results are in, a switch case is used to respond in the proper way for the
        // point in the protocol:
        public void onResults(Bundle results)
        {
            // First empty the progress bar
            isListening = false;
            progress.setProgress(0);
            // Get list of phrases that are possible hits from voice recognizer
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String topHit = matches.get(0).toLowerCase(Locale.ENGLISH); // Only use the top hit, and control for case sensitivity - RESTRICTED TO ENGLISH LANGUAGE

            if (!topHit.equals("stop") && !topHit.equals("exit")) {
                switch (step)
                {
                    case 0:
                        if (Arrays.asList(topicLabels).contains(topHit))
                        {
                            convoTopic = topHit;
                            speakOut(convoTopic);
                            step = 1;
                            //speakOut("Set step to 1");///////////////////////
                        }
                        else
                        {
                            speakOut("Sorry, what subject?");
                        }
                        break;
                    case 1:
                        if (topHit.equals("yes") || topHit.equals("yeah") || topHit.equals("yep"))
                        {
                            speakOut("Okay, it looks like we're talking about " + convoTopic + ". " + topicPrompts[Arrays.asList(topicLabels).indexOf(convoTopic)]);
                            bluetooth.write("new");
                            query = "";
                            replies = 0;
                            prompted = false;
                            step = 2;
                        }
                        else if (topHit.split(" ")[0].equals("no") || topHit.split(" ")[0].equals("nope"))
                        {
                            String[] hits = topHit.split(" ");
                            convoTopic = "";
                            for (int i = 1; i < hits.length; ++i){
                                convoTopic += hits[i] + " ";
                            }
                            convoTopic = convoTopic.trim();
                            speakOut(convoTopic);
                        }
                        else
                        {
                            speakOut("Did you say yes?");
                        }
                        break;
                    case 2:
                        query += topHit + " ";
                        if ((query.split(" ").length < 15) && (prompted == false)) {
                            speakOut(keepGoing[rand.nextInt(keepGoing.length)]);
                            prompted = true;
                        } else if (query.split(" ").length >= 15) {
                            // TODO: debug bluetooth stuff here
                            bluetooth.write(query);/////////////
                            String reply = bluetooth.readNext();
                            query = "";
                            prompted = false;
                            if (replies < 6) {
                                speakOut(replyTemplates[rand.nextInt(replyTemplates.length)].replace("<topic>", reply));
                                replies++;
                            } else {
                                speakOut(finalReplyTemplate.replace("<topic>", reply));
                                speakOut("Should we talk about another subject?");
                                step = 3;
                            }
                        }
                        break;
                    case 3:
                        if (topHit.split(" ")[0].equals("yes") || topHit.split(" ")[0].equals("yeah") || topHit.split(" ")[0].equals("yep"))
                        {
                            String[] hits = topHit.split(" ");
                            for (int i = 1; i < hits.length; ++i){
                                convoTopic += hits[i] + " ";
                            }
                            convoTopic = convoTopic.trim();
                            if (Arrays.asList(topicLabels).contains(topHit))
                            {
                                convoTopic = topHit;
                                speakOut(convoTopic);
                                step = 1;
                            }
                            else
                            {
                                speakOut("Sorry, what subject?");
                                step = 0;
                            }
                        }
                        else if (topHit.equals("no") || topHit.equals("nope"))
                        {
                            speakOut("Okay, it was nice talking to you!");
                            step = 4;
                            // stop listening
                        }
                        break;

                }

                // if step is less than 5, start listening in convo mode again
                // if step is 5, reset all variables and stop listening
                if (step < 4)
                {
                    final Handler handler = new Handler();
                    final Runnable runnable = new Runnable() {
                        public void run() {
                            startListening(speechRecogConvo);
                        }
                    };
                    handler.postDelayed(runnable, 250);	// Start listening only after tts and animations have finished.
                }
                else
                {
                    step = 0;
                    progress.setProgress(100);
                }


            }
            // If user says "exit", say "Exiting conversation mode" and start listening normally instead.
            else if (topHit.equals("exit") || (topHit.equals("stop"))) {
                speakOut("Exiting Conversation Mode.");
                step = 0;
                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        startListening(speechRecogNormal);
                    }
                };
                handler.postDelayed(runnable, 250);	// Start listening only after tts and animations have finished.
            }
            // For consistency's sake, if user says "stop", fill the progress bar and stop listening.
            else {
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }

        }
    }

    // Helper methods that need to be outside of inner classes.
    /**----------------------------------------------------------------------------------------------
     * Saves the current version of WordDictionary to the text file.
     */
    private void saveWord() {
        try {
            face.writeWordDatabase(this);
        } catch (IOException e) {
            speakOut("Error saving to word database.");
        }
    }
    /**----------------------------------------------------------------------------------------------
     * Saves the current version of ResponseDictionary to the text file.
     */
    private void saveResponse() {
        try {
            face.writeResponseDatabase(this);
        } catch (IOException e) {
            speakOut("Error saving to response database.");
        }
    }
    /**----------------------------------------------------------------------------------------------
     * Clears the saved text files for both WordDictionary and ResponseDictionary, then
     * re-populates them with the hard-coded lists.
     */
    private void clearMemory() {
        try {
            face.clearMemory(this);
        } catch (IOException e) {
            speakOut("Error clearing memory.");
        }
    }



    /**----------------------------------------------------------------------------------------------
     *  Search a sentence to see if it contains a phrase.
     *
     *  Assumptions:
     *  The phrase has zero or more words, separated by whitespace.
     *  The sentence has zero or more words, separated by whitespace.
     *  Spelling and separation of words by whitespace are consistent between both the phrase and the sentence.
     *
     *  This method returns true if the sentence contains the words in the phrase, in the same
     *  exact sequential order, regardless of punctuation, capitalization, or spacing.
     */
    public static boolean containsPhrase(String sentence, String keyphrase) {

        boolean found = false;

        // Parse both the keyphrase and the sentence into lists of component words, regardless of punctuation and spacing
        String[] keyphraseSplit = keyphrase.trim().replaceAll("[^\\w ]", "").split("\\s+");
        String[] sentenceSplit = sentence.trim().replaceAll("[^\\w ]", "").split("\\s+");


        // Return false if the keyphrase is longer than the sentence
        if (keyphraseSplit.length > sentenceSplit.length)
            return false;

        // Check each word in the sentence for the first word of the phrase, until a match is found or until the end of the sentence is reached
        int i = 0;
        while (((i + keyphraseSplit.length) <= sentenceSplit.length) && (!found)) {
            int currentIndex = i;

            // When a match for the first word is found, check the following words to see if they also match with the rest of the phrase
            if (sentenceSplit[i].equalsIgnoreCase(keyphraseSplit[0])){
                found = true;
                while ((found) && (currentIndex < (i + keyphraseSplit.length))) {	// If the words match so far and we haven't reached the end of the phrase yet, check this word.

                    if 		((currentIndex == sentenceSplit.length) 												// If we have reached the end of the sentence,
                            || (!keyphraseSplit[currentIndex - i].equalsIgnoreCase(sentenceSplit[currentIndex])) )	// or this word does not match,
                        found = false;																				// then we haven't found a phrase match. Exit loop.
                    currentIndex++;
                }
            }
            i++;
        }

        // Return whether or not a match was found in the phrase.
        return found;
    }




    /**----------------------------------------------------------------------------------------------
     *  Inflate the menu; this adds items to the action bar if it is present.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_start_smile, menu);
        return true;
    }



    /**----------------------------------------------------------------------------------------------
     * Animate an expression transition from the current expression to the one indicated by the integer parameter.
     */
    private void animate(Emotion emotion)
    {
        if (cameraMode)
        {
            emotion = Emotion.HAPPY;
        }

        // This switch case changes the pitch and rate of SMILE's voice
        // depending on the type of expression it is going to animate
        switch (emotion){

            case HAPPY:
                speechPitch= 1.7;		// Happy
                speechRate= .95;
                break;
            case SAD:
                speechPitch= 1.5;		// Sad
                speechRate= .5;
                break;
            case ANGRY:
                speechPitch= 1.8;		// Upset
                speechRate= 1.5;
                break;
            case CONFUSED:
                speechPitch= 1.5;		// Confused
                speechRate= .8;
                break;
            case SURPRISED:
                speechPitch= 1.7;		// Surprised
                speechRate= 1.4;
                break;
            case NEUTRAL:
                speechPitch= 1.5;		// Neutral
                speechRate= .95;
                break;
            default:
                break;
        }

        // Animate transition if currently showing an expression that is different from the target expression
        if (currentEmotion != emotion)  {
            imgvw.setBackgroundResource(EmotionalFace.transitions[currentEmotion.ordinal()][emotion.ordinal()]);
            AnimationDrawable transitionanimation = (AnimationDrawable) imgvw.getBackground();

            currentEmotion = emotion; // Update current expression number

            // Set up next static (blinking) expression to start after transition animation finishes
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                public void run() {
                    animateThisExpression();
                }
            };

            // Animate transition, then call animation for next blinking expression when it's done
            transitionanimation.start();
            handler.postDelayed(runnable, 250);

            // If currently showing an expression that is the same as target expression, just speak whatever response is ready.
        } else {		// Yes, it's kind of weird that it goes here, but it works.
            speakOut(currentResponse);
        }
    }


    /**----------------------------------------------------------------------------------------------
     * Helper method that animates the static (blinking) expression that corresponds to the currentExpression number.
     */
    private void animateThisExpression()
    {
        speakOut(currentResponse);
        int staticAnimation;
        if (cameraMode)
        {
            staticAnimation = R.drawable.happy_camera;
        }
        else
        {
            staticAnimation = EmotionalFace.transitions[currentEmotion.ordinal()][currentEmotion.ordinal()];
        }
        imgvw.setBackgroundResource(staticAnimation);
        AnimationDrawable targetanimation = (AnimationDrawable) imgvw.getBackground();
        targetanimation.start();

    }

    /**----------------------------------------------------------------------------------------------
     * When app closes, shut down tts and speech recognizers.
     */
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecogNormal != null)
        {
            speechRecogNormal.stopListening();
            speechRecogNormal.cancel();
            speechRecogNormal.destroy();
        }
        if (speechRecogLearn != null)
        {
            speechRecogLearn.stopListening();
            speechRecogLearn.cancel();
            speechRecogLearn.destroy();
        }
        if (speechRecogCommand != null)
        {
            speechRecogCommand.stopListening();
            speechRecogCommand.cancel();
            speechRecogCommand.destroy();
        }
        if (speechRecogConvo != null)
        {
            speechRecogConvo.stopListening();
            speechRecogConvo.cancel();
            speechRecogConvo.destroy();
        }
        super.onDestroy();
    }



    /**----------------------------------------------------------------------------------------------
     * Set up TTS when starting app.
     */
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
        }

    }


    /**----------------------------------------------------------------------------------------------
     * Speak out a given string using TTS.
     */
    private void speakOut(String toSpeak) {
        tts.setPitch((float) speechPitch); //speechPitch is a global variable found at the top of the class. higher the number, the squeekier the voice
        tts.setSpeechRate((float) speechRate);//speechRate is a global variable. the high the number, the faster the speech
        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null); //say whatever string is passed
        while (tts.isSpeaking()) {
            // Do nothing while TTS is speaking
        }
        currentResponse = "";
        timeCompletedSpeaking = System.currentTimeMillis();
    }

    /**----------------------------------------------------------------------------------------------
     * TODO: find a better way of toggling this mode
     * Send accelerometer information from phone to robot, and change expression based on head position
     * relative to gravity. CANNOT BE ACTIVE AT THE SAME TIME AS CONVERSATION INTERFACE (TOP) IS ACTIVE.
     */
    public void onSensorChanged(SensorEvent event) {
//		int x_accel = (int)event.values[0];					// Round down all float values to lower integer bound.
//		int y_accel = (int)event.values[1] + 10;			// Add 10 for y and z values to interface correctly with robot computer.
//		int z_accel = (int)event.values[2] + 10;
//
//		// Send y-value to robot - this is side-to-side motion
//		new Thread(new ClientThread((y_accel + "").toString(), ROBOT_PORT, ROBOT_IP)).start();
//
//		if (y_accel < 2 || y_accel > 18)
//		{
//			animate(Emotion.SAD);		// If fallen over sideways, animate a sad face.
//		}
//		else if (y_accel < 7 || y_accel > 13)
//		{
//			animate(Emotion.SURPRISED);		// If falling to the side, animate a surprised face.
//		}
//		else if (x_accel < 7)
//		{
//			if (z_accel < 10)
//			{
//				animate(Emotion.ANGRY);		// If looking down, as at a ball, animate an angry face.
//			}
//			else
//			{
//				animate(Emotion.SAD);		// If fallen over backwards, animate a sad face.
//			}
//		}
//		else
//		{
//			animate(Emotion.HAPPY);			// If more or less upright, animate a happy face.
//		}

    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {

    }


    /**----------------------------------------------------------------------------------------------
     * The client's datagram sender class - sends a specified message to robot's server.
     */
    class ClientThread implements Runnable  {
        private String messageToSend;
        private int onboardPC_port;
        private String BroadcastIP;

        public ClientThread(String message, int port, String ipAddy){
            this.messageToSend = message;	// To be used for better interfacing with this class
            this.onboardPC_port = port;
            this.BroadcastIP = ipAddy;
        }

        @Override
        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(BroadcastIP);
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, onboardPC_port);

                packet.setData(messageToSend.getBytes());
                socket.send(packet);
                socket.close();

            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

}
