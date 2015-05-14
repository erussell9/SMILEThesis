package com.teamroboface.smilethesis;

/**
 * Created by heirlab4 on 5/14/15.
 */
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;

import android.content.Context;
import android.util.Log;

import com.teamroboface.smilethesis.R;

public class EmotionalFace {

    /**----------------------------------------------------------------------------------------------
     * An enumeration of possible emotional states for the robot to display.
     */
    protected static enum Emotion {
        HAPPY,
        SAD,
        ANGRY,
        CONFUSED,
        SURPRISED,
        NEUTRAL
    }

    /**----------------------------------------------------------------------------------------------
     * The array that holds the standard recognized words. Each is placed in a row corresponding to its respective expression.
     */
    private static String[][] wordList = {
            {"happy", "good", "smile", "nice", "favorite", "learn",
                    "command mode", "camera mode", "conversation mode"},			                        // Happy words
            {"sad", "cry", "unhappy", "depressed", "packers"},												// Sad words
            {"angry", "mad", "upset", "stupid", "loser"},													// Angry words
            {"question", "confused", "questioning", "strange", "uncertain"},								// Confused words
            {"surprise", "wow", "surprised", "boo", "shocked"},												// Surprised words
            {"neutral", "hello", "hi", "indifferent", "blank", "clear memory"}								// Neutral words
    };



    /**----------------------------------------------------------------------------------------------
     * The array that holds the standard responses.  Each is paired with a keyword.
     */
    private static String[][] responseList = {
            {"hello", "hello"},
            {"hi", "hello"},
            {"boo", "wow"},
    };


    /**----------------------------------------------------------------------------------------------
     * ******************** Expression Animations Listed Here ****************************
     *
     *  Organization: This is a 6x6 array of transition animations, organized by expression number (from order in Emotion enum).
     *  Rows represent the initial expression and columns represent the final expression of each transition.
     *  In indices where row = column, the animation is a continuing loop of that expression blinking.
     *
     *  For example: transitions[1][3] - The row is 1, and 1=sad, so the animation starts the face in sad.
     *  The column is 3, and 3=confused, so the animation ends the face in confused.  In other words, this is an
     *  animation showing the face going from sad to confused.
     *
     *  Another example: transitions[2][2] - The row and column are both 2, and 2=angry, so the animation starts
     *  and ends the face in angry.  In other words, this is an animation of the face blinking while being angry.
     *  It will play on a loop until some other animation is triggered.
     */
    protected static int[][] transitions = {
            {R.drawable.happy_blinking, R.drawable.happy_sad, R.drawable.happy_angry, R.drawable.happy_questioning, R.drawable.happy_surprised, R.drawable.happy_neutral},
            {R.drawable.sad_happy, R.drawable.sad_blinking, R.drawable.sad_angry, R.drawable.sad_questioning, R.drawable.sad_surprised, R.drawable.sad_neutral},
            {R.drawable.angry_happy, R.drawable.angry_sad, R.drawable.angry_blinking, R.drawable.angry_questioning, R.drawable.angry_surprised, R.drawable.angry_neutral},
            {R.drawable.questioning_happy, R.drawable.questioning_sad, R.drawable.questioning_angry, R.drawable.questioning_blinking, R.drawable.questioning_surprised, R.drawable.questioning_neutral},
            {R.drawable.surprised_happy, R.drawable.surprised_sad, R.drawable.surprised_angry, R.drawable.surprised_questioning, R.drawable.surprised_blinking, R.drawable.surprised_neutral},
            {R.drawable.nuetral_happy, R.drawable.nuetral_sad, R.drawable.nuetral_angry, R.drawable.nuetral_questioning, R.drawable.nuetral_surprised, R.drawable.nuetral_blinking}
    };

    /**----------------------------------------------------------------------------------------------
     * The dictionaries for keywords linked with emotions and keywords linked with responses.
     */
    protected LinkedHashMap<String, Emotion> wordDictionary;
    protected LinkedHashMap<String, String> responseDictionary;


    /**----------------------------------------------------------------------------------------------
     * Constructor method for EmotionalFace.
     */
    public EmotionalFace () {

        // Create hash table of keywords paired with their corresponding expressions(represented by an integer 0-5)
        wordDictionary = new LinkedHashMap<String, Emotion>();

        // Create hash table of keywords paired with their corresponding responses
        responseDictionary = new LinkedHashMap<String, String>();

        fillDictionaries();		// Fill the hash tables with standard keywords and emotions/responses from wordList and responseList, defined above.

        // Best thing to do now is load databases.  In order to handle IO exceptions, do this from main activity.
    }

    /**----------------------------------------------------------------------------------------------
     * Populates wordDictionary and responseDictionary with key-value pairs from the static arrays wordList and responseList.
     */
    private void fillDictionaries() {
        for (int i = 0; i < wordList.length; i++) {
            for (int j = 0; j < wordList[i].length; j++) {
                wordDictionary.put(wordList[i][j], Emotion.values()[i]); // Each keyword in wordList is associated with an Emotion depending on its row.
            }
        }
        for (int i = 0; i < responseList.length; i++) {
            responseDictionary.put(responseList[i][0], responseList[i][1]);	// Each keyword-response pair in responseList is saved.
        }
    }

    /**----------------------------------------------------------------------------------------------
     * Checks if a given word corresponds to any STANDARD keyword for a particular emotion (defined in wordList).
     * If so, return the emotion.  If the word is "nothing", returns null. Throws exception if no word matched.
     * @param word
     * @return emotion
     */
    public Emotion getEmotionKey(String word) throws Exception {
        Emotion result = null;
        if (word.equalsIgnoreCase("nothing"))
        {
            result = null;
        }
        else
        {
            for (int i = 0; i < wordList.length; i++) {		// Find hardcoded keyword in wordList and return associated emotion.
                for (String key : wordList[i]) {
                    if (word.equalsIgnoreCase(key))
                    {
                        result = Emotion.values()[i];
                        break;
                    }
                }
                if (result != null)
                    break;
            }
        }
        if (result == null && !word.equalsIgnoreCase("nothing"))
        {
            throw new Exception("No such emotion");
        }
        return result;
    }

    /**
     * Fills the hash tables with any saved keywords and emotions/responses from databases.
     * @param context
     * @throws IOException
     */
    public void loadDatabases(Context context) throws IOException {

        loadResponseDatabase(context);
        loadWordDatabase(context);
    }

    /**----------------------------------------------------------------------------------------------
     * Reads saved keywords and expressions from Words.txt and put into wordDictionary
     */
    public void loadWordDatabase(Context context) throws IOException {

        BufferedReader wordReader = new BufferedReader(new InputStreamReader(context.openFileInput("Words")));

        String inputString;
        while ((inputString = wordReader.readLine()) != null) {
            String subS1 = inputString.substring(1,inputString.length()-1); //reads one big string that looks like "happy=0, sad=1,confused=3,..."
            String[] tempArray = subS1.split(", "); // Split string at commas, put each term in its own index in String[] tempArray

            for(int i=0; i<tempArray.length; i++){ // For each cell of tempArray
                String [] holder = tempArray[i].split("="); //separate key from the value at the "=" and put into a 1x2 String[] holder
                String key = holder[0].trim(); //we know that the first cell holds the key
                Emotion value = Emotion.valueOf(holder[1]); //we know that the second cell holds the value, which should be an int for wordDictionary, so convert it to an int

                wordDictionary.put(key, value); //put them in the dictionary

            }
        }
        wordReader.close(); //close the file to save it
    }

    /**----------------------------------------------------------------------------------------------
     * Reads saved keywords and responses from Responses.txt and put into responseDictionary
     */
    public void loadResponseDatabase(Context context) throws IOException {

        BufferedReader responseReader = new BufferedReader(new InputStreamReader(context.openFileInput("Responses")));

        String inputString;
        while ((inputString = responseReader.readLine()) != null) {
            String subS1 = inputString.substring(1,inputString.length()-1); //reads one big string that looks like "happy=Im a happy robot, sad=don't say that,confused=what do you mean,..."
            String[] tempArray = subS1.split(", "); // Split string at commas, put each term in its own index in String[] tempArray


            for(int i=0; i<tempArray.length; i++){
                String [] holder1 = tempArray[i].split("="); //separate key from the value at the "=" and put into a 1x2 String[] holder
                String key = holder1[0].trim(); //we know that the first cell holds the key
                String value = holder1[1]; //we know that the second cell holds the value, which should be an String for responseDictionary

                responseDictionary.put(key, value); //put them in the dictionary
            }
        }
        responseReader.close();  //close the file to save it
    }


    /**----------------------------------------------------------------------------------------------
     * Converts wordDictinoary into strings and writes to database on the device's internal storage.
     */
    public void writeWordDatabase(Context context) throws IOException {
        FileOutputStream fos = context.openFileOutput("Words", Context.MODE_PRIVATE);
        String toFile= wordDictionary.toString();
        fos.write(toFile.getBytes());
        fos.close();
    }

    /**----------------------------------------------------------------------------------------------
     * Converts responseDictinoary into strings and writes to database on the device's internal storage.
     */
    public void writeResponseDatabase(Context context) throws IOException {
        FileOutputStream fos = context.openFileOutput("Responses", Context.MODE_PRIVATE); // Finds Responses.txt and opens it
        String toFile= responseDictionary.toString(); //convert our hash table to a big string
        fos.write(toFile.getBytes()); //turn the string into bytes so it can be written to the file and then write it
        fos.close(); //save the file
    }

    /**----------------------------------------------------------------------------------------------
     * Clears memory of learned keywords and responses; refills dictionaries with only hard-coded
     * keywords and responses.
     */
    public void clearMemory(Context context) throws IOException {
        FileOutputStream fos1 = context.openFileOutput("Words", Context.MODE_PRIVATE);
        fos1.write("".getBytes());		// Fill text file with empty string
        fos1.close();

        FileOutputStream fos2 = context.openFileOutput("Responses", Context.MODE_PRIVATE); // Finds Responses.txt and opens it
        fos2.write("".getBytes()); 	// Fill text file with empty string
        fos2.close(); //save the file

        wordDictionary.clear();
        responseDictionary.clear();
        fillDictionaries();
    }

}



