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
    private long timeCompletedSpeaking;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecog;
    private BluetoothService bluetooth;
    private ProgressBar progress;
    private ToneGenerator tone;
    private boolean isListening;
    private listenerNormal normalListener;
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
        int initialExpression = EmotionalFace.transitions[currentEmotion.ordinal()][currentEmotion.ordinal()];
        imgvw.setBackgroundResource(initialExpression);
        AnimationDrawable initialanimation = (AnimationDrawable) imgvw.getBackground();
        initialanimation.start();

		/*
		 * Create speech recognizer and listeners for each mode
		 * Set first listener as normal
		 */
        isListening = false;
        normalListener = new listenerNormal();
        learnListener = new listenerLearn();
        commandListener = new listenerCommand();
        convoListener = new listenerConvo();
        speechRecog = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecog.setRecognitionListener(normalListener);

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
        View speakButton = findViewById(R.id.animationImage);
        speakButton.setEnabled(true);
        speakButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
				/* **************** TO DISABLE CONVERSATION INTERFACE, COMMENT OUT THIS BLOCK ************/
                if (isListening) {
                    speechRecog.stopListening();
                    speechRecog.cancel();
                    learnListener.setStep(0);
                    convoListener.setStep(0);
                    isListening = false;
                    progress.setProgress(100);
                } else if (System.currentTimeMillis() - timeCompletedSpeaking > 750) {	// don't respond to any touches noticed while app is busy speaking
                    speechRecog.setRecognitionListener(normalListener);
                    startListening();				// this starts the activity for normal listening (i.e. not learning new words)
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
    private void startListening () {
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
        private int numError7s = 0;

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

        // When voice timeout, unrecognizeable speech, audio recording error, or "recognizer busy" error is encountered,
        // play tone and start listening again.  For all other errors, announce error number and stop
        // recognition cycle.
        public void onError(int error)
        {
            Log.d("ERRR", "this is the error number: " + error);
            speechRecog.cancel();
            isListening = false;
            progress.setProgress(100);
            switch (error) {
                case 8: // ERROR_RECOGNIZER_BUSY comes at beginning of request - still goes on to listen. I think. Do nothing.
                    //break;
                case 7: // ERROR_NO_MATCH comes two at a time for some reason. Only respond to second.
                    if (numError7s == 0) {
                        numError7s++;
                        break;
                    } else {
                        numError7s = 0;
                    }
                case 3: // ERROR_AUDIO
                case 6: // ERROR_SPEECH_TIMEOUT
                    tone.startTone(ToneGenerator.TONE_PROP_NACK);
                    SystemClock.sleep(500);
                    startListening();
                    break;
                default:
                    SystemClock.sleep(500);
                    animateAndSpeak(Emotion.CONFUSED, "Network connectivity error number " + error);
                    break;
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
            if (topHit.equals("stop")) {
                // If "stop" is recognized, fill the progress bar, generate a low tone, and stop listening.
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            } else {
                // Default: If no keyword recognized, repeat back the entire speech input and start listening again immediately afterward.
                Emotion animateTo = currentEmotion;
                String say = topHit;
                RecognitionListener modeToListen = normalListener;

                // GOTCHA: if defining any hard-coded commands here (as in "learn" or "clear memory"),
                // these keys must also be hard-coded in the keyword lists or saved files.
                if (phrasesPresent.size() > 0) {

                    // Keyword "Learn" starts learning speech listener
                    if (phrasesPresent.get(0).equals("learn")) {
                        modeToListen = learnListener;
                        animateTo = Emotion.HAPPY;
                        say = "Okay, teach me something.";

                        // Keyword "Clear memory" clears saved files of all added keywords, not including hard-coded ones.
                    } else if (phrasesPresent.get(0).equals("clear memory")) {
                        clearMemory();
                        animateTo = Emotion.NEUTRAL;
                        say = "Okay, clearing memory";		//animate to neutral

                        // Keyword "Command mode" starts command listener, so that robot can be controlled by voice commands.
                    } else if (phrasesPresent.get(0).equals("command mode")) {
                        modeToListen = commandListener;
                        animateTo = Emotion.HAPPY;
                        say = "Entering command mode.";

                        // Keyword "Conversation mode" starts conversation listener, so that robot can interact with topic detector.
                    } else if (phrasesPresent.get(0).equals("conversation mode")) {
                        modeToListen = convoListener;
                        animateTo = Emotion.NEUTRAL;
                        say = "Okay, loading conversation mode. What condition number?";

                        // Keyword "Camera mode" sets animation to be only happy, no blinking, until camera mode is exited.
                    } else if (phrasesPresent.get(0).equals("camera mode")){
                        if (cameraMode) {
                            cameraMode = false;
                            animateTo = Emotion.NEUTRAL;
                            say = "Exiting camera mode";
                        } else {
                            cameraMode = true;
                            animateTo = Emotion.HAPPY;
                            say = "Entering camera mode";
                        }

                        // All other keywords are dealt with here.
                    } else {
                        //animate to the expression indicated by the emotion(value) that matches this string(key)
                        animateTo = face.wordDictionary.get(phrasesPresent.get(0));
                        //get the response if ones exists for this phrase, to speak when animation done
                        say = face.responseDictionary.get(phrasesPresent.get(0));
                    }
                }

                final Handler handler = new Handler();
                final RecognitionListener mode = modeToListen;
                final Runnable runnable = new Runnable() {
                    public void run() {
                        speechRecog.setRecognitionListener(mode);
                        startListening();
                    }
                };
                animateAndSpeak(animateTo, say); //animate to happy
                handler.postDelayed(runnable, 500); // start listening for learn protocol only AFTER animation and tts response are done
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
        private int numError7s = 0;
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

        // When voice timeout, unrecognizeable speech, audio recording error, or "recognizer busy" error is encountered,
        // do not advance protocol step; instead, ask for user to repeat what they just said.
        // For all other errors, announce error number and cancel learn protocol.
        public void onError(int error)
        {
            Log.d("ERRR", "this is the error number: " + error);
            speechRecog.cancel();
            isListening = false;
            progress.setProgress(100);
            switch (error) {
                case 8: // ERROR_RECOGNIZER_BUSY comes at beginning of request - still goes on to listen. I think. Do nothing.
                    //break;
                case 7: // ERROR_NO_MATCH comes two at a time for some reason. Only respond to second.
                    if (numError7s == 0) {
                        numError7s++;
                        break;
                    } else {
                        numError7s = 0;
                    }
                case 3: // ERROR_AUDIO
                case 6: // ERROR_SPEECH_TIMEOUT
                    tone.startTone(ToneGenerator.TONE_PROP_NACK);
                    animateAndSpeak(currentEmotion, "Sorry, can you repeat that?");
                    SystemClock.sleep(500);
                    startListening();
                    break;
                default:
                    SystemClock.sleep(500);
                    animateAndSpeak(Emotion.CONFUSED, "Network connectivity error number " + error);
                    break;
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

            if (topHit.equalsIgnoreCase("stop")) {
                // If user says "stop," cancel protocol and stop listening.
                step = 0;
                toBeLearned = "";
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);

            } else {

                Emotion animateTo = Emotion.HAPPY;
                String say = "";
                RecognitionListener modeToListen = learnListener;

                switch (step) {

                    case 0: // STEP ONE: SAVE NEW KEYWORD
                        toBeLearned = topHit;//saves the new word for putting it in the dictionary later
                        say = topHit + "...  How does that make me feel?";
                        step = 1;
                        break;

                    case 1: // STEP TWO: SAVE ASSOCIATED EMOTION
                        try {
                            Emotion emotion = face.getEmotionKey(topHit);
                            if (emotion != null) {				// Save emotion. Or if keyword was "nothing," just advance protocol without saving any response.
                                face.wordDictionary.put(toBeLearned, emotion);    // Match the keyword from step one with the emotion and save them.
                                saveWord();
                            }
                            say = "Okay, got it. What should I say to that?";
                            step = 2;
                        }
                        catch(Exception e) {				// Expression was not recognized, need to get a valid expression word.
                            say = "Whoops, can you say another expression?";
                            animateTo = Emotion.CONFUSED;	//animate confused face
                        }
                        break;

                    case 2: // STEP THREE: SAVE ASSOCIATED VERBAL RESPONSE
                        if(topHit.equals("nothing") || topHit.equalsIgnoreCase("I don't know")) {
                            say = "Okay, I won't say anything.";
                        } else {
                            say = "Okay, I'll say, " + topHit;		// "I don't know" and "nothing" are assumed to mean "don't save a response, just an expression"
                            face.responseDictionary.put(toBeLearned, topHit);	// Match keyword from step one with response and save them.
                            saveResponse();
                        }
                        modeToListen = normalListener;
                        step = 0;
                        break;

                    default:	// In the unlikely event that an invalid step number is passed, exit the protocol.
                        speakOut("Whoops! Exiting learn.");
                        modeToListen = normalListener;
                        step = 0;
                        break;

                }

                // Now either start listening for learn again or reset protocol step and start listening for normal speech.
                final Handler handler = new Handler();
                final RecognitionListener mode = modeToListen;
                final Runnable runnable = new Runnable() {
                    public void run() {
                        speechRecog.setRecognitionListener(mode);
                        startListening();
                    }
                };
                animateAndSpeak(animateTo, say); //animate to happy
                handler.postDelayed(runnable, 500); // start listening for learn protocol only AFTER animation and tts response are done
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
        private int numError7s = 0;

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

        // When voice timeout, unrecognizeable speech, audio recording error, or "recognizer busy" error is encountered,
        // start listening again - probably just heard robot moving.
        public void onError(int error)
        {
            Log.d("ERRR", "this is the error number: " + error);
            speechRecog.cancel();
            isListening = false;
            progress.setProgress(100);
            switch (error) {
                case 8: // ERROR_RECOGNIZER_BUSY comes at beginning of request - still goes on to listen. I think. Do nothing.
                    //break;
                case 7: // ERROR_NO_MATCH comes two at a time for some reason. Only respond to second.
                    if (numError7s == 0) {
                        numError7s++;
                        break;
                    } else {
                        numError7s = 0;
                    }
                case 3: // ERROR_AUDIO
                case 6: // ERROR_SPEECH_TIMEOUT
                    tone.startTone(ToneGenerator.TONE_PROP_NACK);
                    SystemClock.sleep(500);
                    startListening();
                    break;
                default:
                    SystemClock.sleep(500);
                    animateAndSpeak(Emotion.CONFUSED, "Network connectivity error number " + error);
                    break;
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
            if (topHit.equals("stop"))
            {
                // For consistency's sake, if user says "stop", fill the progress bar and stop listening.
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }
            else {
                // Default: say back whatever is said, if it's not a command.
                Emotion animateTo = Emotion.HAPPY;
                String say = topHit;
                RecognitionListener modeToListen = commandListener;

                if (topHit.equals("exit"))
                {
                    // If user says "exit", say "Exiting command mode" and start listening normally instead.
                    say = "Exiting Command Mode.";
                    modeToListen = normalListener;
                }
                else {
                    String commandMessage = "";

                    // Take care of commonly mismatched word "kik", for "kick"
                    if (topHit.equals("kik")) {
                        commandMessage = "kick";
                    } else {
                        commandMessage = topHit;
                    }

                    //For any recognized command, send it to the robot for execution.
                    if (commandMessage.equals("stand")
                            || commandMessage.equals("walk")
                            || commandMessage.equals("kick")
                            || commandMessage.equals("track")
                            || commandMessage.equals("relax")
                            || commandMessage.equals("engage")) {
                        say = "Okay, I'll " + commandMessage;
                        new Thread(new ClientThread(commandMessage, ROBOT_PORT, ROBOT_IP)).start(); //put the command in the socket
                    }
                }

                // If no recognized command, ask user to try again.
                //else
                //{
                    //new Thread(new ClientThread(topHit, ROBOT_PORT, ROBOT_IP)).start(); //put the command in the socket
					//speakOut("Sorry, can you repeat that?");
                //}


                // Now either start listening for command again or exit and start listening for normal speech.
                final Handler handler = new Handler();
                final RecognitionListener mode = modeToListen;
                final Runnable runnable = new Runnable() {
                    public void run() {
                        speechRecog.setRecognitionListener(mode);
                        startListening();
                    }
                };
                animateAndSpeak(animateTo, say); //animate
                handler.postDelayed(runnable, 500); // start listening only AFTER animation and tts response are done

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
        private Emotion[] expressions = {Emotion.SAD, Emotion.NEUTRAL, Emotion.HAPPY};

        // Class variables to track progress through conversation
        private int numError7s = 0;
        private int step = 0;
        public void setStep(int set) {		// Used if need to start on specific step of protocol out of order.
            step = set;
        }
        private String convoTopic = "";
        private String condition = "";
        private String query = "";
        private int replies = 0;
        private int timeouts = 0;
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

        // When voice timeout, unrecognizeable speech, audio recording error, or "recognizer busy" error is encountered,
        // start listening again - need to collect enough material for full query
        public void onError(int error)
        {
            Log.d("ERRR", "this is the error number: " + error);
            speechRecog.cancel();
            isListening = false;
            progress.setProgress(100);
            switch (error) {
                case 8: // ERROR_RECOGNIZER_BUSY comes at beginning of request - still goes on to listen. I think. Do nothing.
                    //break;
                case 7: // ERROR_NO_MATCH comes two at a time for some reason. Only respond to second.
                    if (numError7s == 0) {
                        numError7s++;
                        break;
                    } else {
                        numError7s = 0;
                    }
                case 3: // ERROR_AUDIO
                case 6: // ERROR_SPEECH_TIMEOUT
                    timeouts++;
                    tone.startTone(ToneGenerator.TONE_PROP_NACK);
                    SystemClock.sleep(500);
                    startListening();
                    break;
                default:
                    SystemClock.sleep(500);
                    animateAndSpeak(Emotion.CONFUSED, "Network connectivity error number " + error);
                    break;
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

            timeouts = 0;

            if (topHit.equals("stop"))
            {
                // For consistency's sake, if user says "stop", fill the progress bar, reset the protocol, and stop listening.
                query = "";
                replies = 0;
                step = 0;
                progress.setProgress(100);
                tone.startTone(ToneGenerator.TONE_PROP_NACK);
            }
            else {
                Emotion animateTo = Emotion.NEUTRAL;
                String say = "";
                RecognitionListener modeToListen = convoListener;

                if (topHit.equals("exit")) {
                    // If user says "exit", say "Exiting conversation mode" and start listening normally instead.
                    say = "Exiting Conversation Mode.";
                    step = 0;
                    modeToListen = normalListener;
                } else {
                    String[] hitSplit = topHit.split(" ");
                    switch (step) {

                        case 0: // conditions are 1=control, 2=topics, 3=emotions, 4=integrated
                            if (topHit.equals("1")
                                    || topHit.equals("2")
                                    || topHit.equals("to")
                                    || topHit.equals("too")
                                    || topHit.equals("3")
                                    || topHit.equals("4")) {
                                condition = topHit;
                                say = condition + "?";
                                step = 1;
                            } else {
                                say = "Sorry, what condition number?";
                            }
                            break;
                        case 1:
                            switch (hitSplit[0]) {
                                case "yes":
                                case "yeah":
                                case "yep":
                                    say = "Okay, what subject should we talk about?";
                                    step = 2;
                                    break;
                                case "no":
                                case "nope":
                                    if (hitSplit[1].equals("1")
                                            || hitSplit[1].equals("2")
                                            || hitSplit[1].equals("to")
                                            || hitSplit[1].equals("too")
                                            || hitSplit[1].equals("3")
                                            || hitSplit[1].equals("4"))
                                    {
                                        condition = hitSplit[1];
                                        say = condition + "?";
                                    }
                                    else
                                    {
                                        say = "Sorry, what condition number?";
                                    }
                                    break;
                                case "1":
                                case "2":
                                case "to":
                                case "too":
                                case "3":
                                case "4":
                                    condition = hitSplit[0];
                                    say = condition + "?";
                                    break;
                                default:
                                    say = "Sorry, what condition number?";
                                    break;

                            }
                            break;
                        case 2:
                            if (condition.equals("to") || condition.equals("too"))
                                condition = "2";
                            if (Arrays.asList(topicLabels).contains(topHit)) {
                                convoTopic = topHit;
                                say = convoTopic;
                                step = 3;
                            } else {
                                say = "Sorry, what subject?";
                            }
                            break;
                        case 3:
                            switch (hitSplit[0]) {
                                case "yes":
                                case "yeah":
                                case "yep":
                                    say = "Okay, it looks like we're talking about " + convoTopic + ". " + topicPrompts[Arrays.asList(topicLabels).indexOf(convoTopic)];
                                    bluetooth.write("new");
                                    query = "";
                                    replies = 0;
                                    step = 4;
                                    break;
                                case "no":
                                case "nope":
                                    String[] hits = topHit.split(" ");
                                    convoTopic = "";
                                    for (int i = 1; i < hits.length; ++i) {
                                        convoTopic += hits[i] + " ";
                                    }
                                    convoTopic = convoTopic.trim();
                                    say = convoTopic + "?";
                                    break;
                                default:
                                    say = "Did you say yes?";
                                    break;
                            }
                            break;
                        case 4:
                            timeouts = 0;
                            query += topHit + " ";
                            if (query.split(" ").length >= 15) {
                                Log.d("CONVERSATION", "making reply. query: " + query);
                                if (condition.equals("1") || condition.equals("3"))//******** control or emotions condition - need to request random reply
                                    query = "#random#" + query;

                                // TODO: debug bluetooth stuff here
                                bluetooth.write(query);
                                query = "";
                                String[] queryResults = bluetooth.readNext().split("#"); // string from query handler has to be formatted "<int sentiment>#<String reply>"

                                int sentimentIndex;
                                if (condition.equals("1") || condition.equals("2")) //******** control or topics condition - need to get random sentiment
                                {
                                    sentimentIndex = rand.nextInt(3);
                                }
                                else
                                {
                                    sentimentIndex = Integer.parseInt(queryResults[0]) + 1; // sentiment must be -1=negative, 0=neutral, 1=positive
                                }
                                animateTo = expressions[sentimentIndex];

                                String reply = queryResults[1];
                                if (replies < 6) { // only 7 queries per conversation
                                    say = reply;
                                    replies++;
                                } else {
                                    say = reply + " Well thanks for the conversation. It was nice talking with you!";
                                    modeToListen = normalListener;
                                    step = 0;
                                }
                            }
                            break;

                    }

                }

                // Now either start listening for query again or exit and start listening for normal speech.
                final Handler handler = new Handler();
                final RecognitionListener mode = modeToListen;
                final Runnable runnable = new Runnable() {
                    public void run() {
                        speechRecog.setRecognitionListener(mode);
                        startListening();
                    }
                };
                animateAndSpeak(animateTo, say); //animate
                handler.postDelayed(runnable, 500); // start listening only AFTER animation and tts response are done

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
    private void animateAndSpeak(Emotion emotion, String speech)
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

            final String toSpeak = speech;
            imgvw.setBackgroundResource(EmotionalFace.transitions[currentEmotion.ordinal()][emotion.ordinal()]);
            AnimationDrawable transitionanimation = (AnimationDrawable) imgvw.getBackground();
            currentEmotion = emotion;

            // Set up any speech, and the blinking animation in the new expression, to start when the transition ends
            final Handler animationHandler = new Handler();
            final Runnable onAnimationEnd = new Runnable() {
                public void run() {
                    speakOut(toSpeak);
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
            };
            animationHandler.postDelayed(onAnimationEnd, 200); // start listening only AFTER animation and tts response are done

            // Animate transition
            transitionanimation.start();

            // If currently showing an expression that is the same as target expression, just speak whatever response is ready.
        } else {		// Yes, it's kind of weird that it goes here, but it works.
            //##### FIGURE OUT HOW TO MAKE SURE IT DOESN'T INTERRUPT A BLINK??
            speakOut(speech);
        }
    }

    /**----------------------------------------------------------------------------------------------
     * When app closes, shut down tts and speech recognizers.
     */
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecog != null)
        {
            speechRecog.stopListening();
            speechRecog.cancel();
            speechRecog.destroy();
        }
        bluetooth.write("exit");
        bluetooth.cancel();
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
        //while (tts.isSpeaking()) {
            // Do nothing while TTS is speaking
        //}
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
