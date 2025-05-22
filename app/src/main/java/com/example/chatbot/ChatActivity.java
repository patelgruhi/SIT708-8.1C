package com.example.chatbot;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.util.ArrayList;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private MessageAdapter messageAdapter;
    private ArrayList<Message> messageList;
    private OkHttpClient client = new OkHttpClient();
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = "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";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerView = findViewById(R.id.recyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userMessage = editTextMessage.getText().toString().trim();
                if (!TextUtils.isEmpty(userMessage)) {
                    addMessage(userMessage, true);
                    editTextMessage.setText("");
                    sendMessageToBot(userMessage);
                }
            }
        });
    }

    private void addMessage(String text, boolean isUser) {
        runOnUiThread(() -> {
            messageList.add(new Message(text, isUser));
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            recyclerView.scrollToPosition(messageList.size() - 1);
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    private void sendMessageToBot(String userMessage) {
        if (!isNetworkAvailable()) {
            addMessage("No internet connection. Please check your network settings.", false);
            return;
        }

        try {
            JSONObject messageObject = new JSONObject();
            messageObject.put("role", "user");
            messageObject.put("content", userMessage);

            JSONArray messagesArray = new JSONArray();
            messagesArray.put(messageObject);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "meta-llama/llama-3.3-8b-instruct:free");
            requestBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMsg = "Network error: " + e.getMessage();
                    addMessage(errorMsg, false);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.has("choices") && jsonResponse.getJSONArray("choices").length() > 0) {
                                JSONObject choice = jsonResponse.getJSONArray("choices").getJSONObject(0);
                                JSONObject message = choice.getJSONObject("message");
                                String botReply = message.getString("content");
                                addMessage(botReply, false);
                            } else {
                                addMessage("Unexpected response format: " + responseBody, false);
                            }
                        } catch (Exception e) {
                            addMessage("Error parsing response: " + e.getMessage() + "\nResponse: " + responseBody, false);
                        }
                    } else {
                        String errorMsg = "Error " + response.code() + ": ";
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("error")) {
                                errorMsg += errorJson.getString("error");
                            } else {
                                errorMsg += responseBody;
                            }
                        } catch (JSONException e) {
                            errorMsg += responseBody;
                        }
                        addMessage(errorMsg, false);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            addMessage("Error creating request: " + e.getMessage(), false);
        }
    }
} 