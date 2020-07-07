package com.example.chitchat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.squareup.okhttp.Response;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int SIGN_IN_REQUEST_CODE = 100;
    private ImageButton btn_post;
    private FirebaseListAdapter<ChatMessage> adapter;
    String URL="https://hackapi.reverieinc.com/nmt";
    String URLTTS="https://hackapi.reverieinc.com/tts"; //URL for TTS in Reverie
    TextToSpeech tts;
    int result;
    String stringEntered,stringToSpeakInHindi,sentimentString=null;
    int check=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Text to speech
        tts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS)
                {
                    result = tts.setLanguage(Locale.forLanguageTag("hin"));
                }
                else
                {
                    Toast.makeText(getApplicationContext(),"Feature not supported on your device",Toast.LENGTH_LONG).show();
                }
            }
        });

        //End of text to speech


        FirebaseApp.initializeApp(this);

        btn_post = findViewById(R.id.btn_post);

        btn_post.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText input = findViewById(R.id.input);
                stringEntered = input.getText().toString();

                SentimentAnalysis(stringEntered);
                //API call
                new GetJSONTask().execute(URL);

                if(check == 1) {
                    FirebaseDatabase.getInstance()
                            .getReference()
                            .push()
                            .setValue(new ChatMessage("***",
                                    FirebaseAuth.getInstance()
                                            .getCurrentUser()
                                            .getDisplayName())
                            );
                } else {
                    // Read the input field and push a new instance
                    // of ChatMessage to the Firebase database
                    FirebaseDatabase.getInstance()
                            .getReference()
                            .push()
                            .setValue(new ChatMessage(stringToSpeakInHindi,
                                    FirebaseAuth.getInstance()
                                            .getCurrentUser()
                                            .getDisplayName())
                            );
                }




                // Clear the input
                input.setText("");
            }
        });





        //Firebase stuff for sign in
        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Start sign in/sign up activity
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .build(),
                    SIGN_IN_REQUEST_CODE
            );
        } else {
            // User is already signed in. Therefore, display
            // a welcome Toast
            Toast.makeText(this,
                    "Welcome " + FirebaseAuth.getInstance()
                            .getCurrentUser()
                            .getDisplayName(),
                    Toast.LENGTH_LONG)
                    .show();

            // Load chat room contents
            displayChatMessages();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(tts != null)
        {
            tts.stop();
            tts.shutdown();
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == SIGN_IN_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                Toast.makeText(this,
                        "Successfully signed in. Welcome!",
                        Toast.LENGTH_LONG)
                        .show();
                displayChatMessages();
            } else {
                Toast.makeText(this,
                        "We couldn't sign you in. Please try again later.",
                        Toast.LENGTH_LONG)
                        .show();

                // Close the app
                finish();
            }
        }

    }

    private void displayChatMessages() {
        ListView listOfMessages = (ListView)findViewById(R.id.list_of_messages);

        adapter = new FirebaseListAdapter<ChatMessage>(this, ChatMessage.class,
                R.layout.message, FirebaseDatabase.getInstance().getReference()) {
            @Override
            protected void populateView(View v, ChatMessage model, int position) {
                // Get references to the views of message.xml
                TextView messageText = (TextView)v.findViewById(R.id.message_text);
                TextView messageUser = (TextView)v.findViewById(R.id.message_user);
                TextView messageTime = (TextView)v.findViewById(R.id.message_time);

                // Set their text
                messageText.setText(model.getMessageText());
                messageUser.setText(model.getMessageUser());

                // Format the date before showing it
                messageTime.setText(DateFormat.format("dd-MM-yyyy (HH:mm:ss)",
                        model.getMessageTime()));
            }
        };

        listOfMessages.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_sign_out) {
            AuthUI.getInstance().signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(MainActivity.this,
                                    "You have been signed out.",
                                    Toast.LENGTH_LONG)
                                    .show();

                            // Close activity
                            finish();
                        }
                    });
        }
        return true;
    }














    private class GetJSONTask extends AsyncTask<String, Void, String> {

        // onPreExecute called before the doInBackgroud start for display
        // progress dialog.
        Response response = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... urls) {

            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, "{\n\t\"data\": [\""+stringEntered+"\"],\n\t\"tgt\": \"hi\",\n\t\"src\": \"en\"\n}\n");
            Request request = new Request.Builder()
                    .url("https://hackapi.reverieinc.com/nmt")
                    .post(body)
                    .addHeader("token", "4d016fedda3614b7b7df61dba3b7ff330dc0fdcd")
                    .addHeader("content-type", "application/json")
                    .addHeader("cache-control", "no-cache")
                    .addHeader("postman-token", "ae9edcd7-4821-73b0-8dc7-c82321302419")
                    .build();

            try {
                response = client.newCall(request).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        // onPostExecute displays the results of the doInBackgroud and also we
        // can hide progress dialog.
        @Override
        protected void onPostExecute(String result) {

            JSONObject object = null;
            JSONArray Jarray = null;
            try {
                String x = response.body().string();

                JSONObject Jobject = new JSONObject(x);
                JSONObject result1 = Jobject.getJSONObject("data");
                Object level = result1.get("result");
                stringToSpeakInHindi = level.toString().substring(level.toString().indexOf("[") + 3, level.toString().indexOf("]") - 1);

                if (check==0) {
                    speakOut(stringToSpeakInHindi);
                } else {
                    speakOut("Are you ok");
                }


            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void speakOut(String text) {
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA){
            Toast.makeText(getApplicationContext(),"Language not supported on your device",Toast.LENGTH_LONG).show();
        }
        else {
            tts.speak(text,TextToSpeech.QUEUE_FLUSH,null);
        }


        //API call
//        new PutJSONTask().execute(URLTTS);

    }

    private void SentimentAnalysis(final String str) {
        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        String url = "https://sentiment-analysis-api.herokuapp.com/sentiment";

        StringRequest sr = new StringRequest(com.android.volley.Request.Method.POST, url , new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
//                text.setText(String.format("%s : ", input.getText().toString().toUpperCase()));
                if(response.toUpperCase().equals("NEGATIVE")) {
                    Toast.makeText(getApplicationContext(),"Are you ok?",Toast.LENGTH_LONG).show();
                    sentimentString = "***";
                    check = 1;
                }

            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
//                output.setText(error.getMessage());
            }
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                HashMap<String, String> params2 = new HashMap<String, String>();
                params2.put("text", str);
                return new JSONObject(params2).toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json";
            }
        };

        queue.add(sr);
    }

    private class PutJSONTask extends AsyncTask<String, Void, String> {

        // onPreExecute called before the doInBackgroud start for display
        // progress dialog.
        Response response = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... urls) {

//            OkHttpClient client = new OkHttpClient();
//
//            MediaType mediaType = MediaType.parse("application/json");
//            RequestBody body = RequestBody.create(mediaType, "{\n\t\"text\": \""+stringToSpeakInHindi+"\",\n\t\"lang\": \"hi\"}");
//            Log.d("Erererererer",body.toString());
//            Request request = new Request.Builder()
//                    .url("https://hackapi.reverieinc.com/tts")
//                    .post(body)
//                    .addHeader("token", "4d016fedda3614b7b7df61dba3b7ff330dc0fdcd")
//                    .addHeader("content-type", "application/json")
//                    .addHeader("cache-control", "no-cache")
////                    .addHeader("postman-token", "ae9edcd7-4821-73b0-8dc7-c82321302419")
//                    .build();
//
//            try {
//                response = client.newCall(request).execute();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, "{\n\t\"text\": \"Hello\",\n\t\"lang\": \"hi\"\n}");
            Request request = new Request.Builder()
                    .url("https://hackapi.reverieinc.com/tts")
                    .post(body)
                    .addHeader("token", "4d016fedda3614b7b7df61dba3b7ff330dc0fdcd")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("cache-control", "no-cache")
                    .addHeader("Postman-Token", "7d0f839b-f84d-4820-87e4-e0be5a9b5193")
                    .build();

            try {
                response = client.newCall(request).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }

        // onPostExecute displays the results of the doInBackgroud and also we
        // can hide progress dialog.
        @Override
        protected void onPostExecute(String result) {



//            try {
//                String x = response.body().string();
//
//                JSONObject Jobject = new JSONObject(x);
//                JSONObject result1 = Jobject.getJSONObject("data");
//                Object level = result1.get("result");
//                String stringToSpeakInHindi = level.toString().substring(level.toString().indexOf("[") + 3, level.toString().indexOf("]") - 1);
//
//                speakOut(stringToSpeakInHindi);
//
//            } catch (JSONException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

        }
    }
}
