package com.project.chatapp.activities;



import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import com.project.chatapp.adapters.ChatAdapter;
import com.project.chatapp.databinding.ActivityChatBinding;
import com.project.chatapp.models.ChatMessage;
import com.project.chatapp.models.User;
import com.project.chatapp.network.ApiClient;
import com.project.chatapp.network.ApiService;
import com.project.chatapp.utils.AppUtils;
import com.project.chatapp.utils.Constants;
import com.project.chatapp.utils.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversationId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.getImage()),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.getId());
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        Log.d("DEBUG",message.toString());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if(conversationId != null) {
            updateConversion(binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.getId());
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.getName());
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.getImage());
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }

        if(!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.getToken());

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA,data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            } catch (Exception exception) {
                AppUtils.showToast(exception.getMessage(),getApplicationContext());
            }
        }

        binding.inputMessage.setText(null);
    }
    
    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call,@NonNull  Response<String> response) {
                if(response.isSuccessful()) {
                    try {
                        if(response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray result = responseJson.getJSONArray("results");
                            if(responseJson.getInt("failure") == 1){
                                JSONObject error = (JSONObject) result.get(0);
                                AppUtils.showToast(error.getString("error"),getApplicationContext());
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    AppUtils.showToast("Notification sent successfully",getApplicationContext());
                } else {
                    AppUtils.showToast("Error: " + response.code(),getApplicationContext());
                }
            }

            @Override
            public void onFailure(@NonNull  Call<String> call, @NonNull Throwable t) {
                AppUtils.showToast(t.getMessage(),getApplicationContext());
            }
        });
    }

    private void listenAvailabilityReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(receiverUser.getId())
                .addSnapshotListener(ChatActivity.this, ((value, error) -> {
                    if(error != null){
                        return ;
                    }
                    if(value != null){
                        if(value.getLong(Constants.KEY_AVAILABILITY) != null){
                            int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY))
                                    .intValue();
                            isReceiverAvailable = availability == 1;
                            Log.d("DEBUG","available");
                        }
                        receiverUser.setToken(value.getString(Constants.KEY_FCM_TOKEN));
                        if(receiverUser.getImage() == null) {
                            receiverUser.setImage(value.getString(Constants.KEY_IMAGE));
                            chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.getImage()));
                            chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                        }
                    }
                    if(isReceiverAvailable){
                        binding.textAvailability.setVisibility(View.VISIBLE);
                    } else {
                        binding.textAvailability.setVisibility(View.GONE);
                    }

                }));
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.getId())
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.getId())
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);

    }

    private final EventListener<QuerySnapshot> eventListener = ((value, error) -> {
       if (error != null){
           return;
       }
       if (value != null){
           int count = chatMessages.size();
           for(DocumentChange documentChange : value.getDocumentChanges()) {
               if (documentChange.getType() == DocumentChange.Type.ADDED){
                   ChatMessage chatMessage = new ChatMessage(
                           documentChange.getDocument().getString(Constants.KEY_SENDER_ID),
                           documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID),
                           documentChange.getDocument().getString(Constants.KEY_MESSAGE),
                           getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP)),
                           documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP)
                   );
                   chatMessages.add(chatMessage);
               }
           }
           chatMessages.sort(Comparator.comparing(ChatMessage::getDateObject));
           if(count == 0){
               chatAdapter.notifyDataSetChanged();
           } else {
               chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
               binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size()-1);
           }
           binding.chatRecyclerView.setVisibility(View.VISIBLE);
       }
       binding.progressBar.setVisibility(View.GONE);
       if(conversationId == null) {
           checkForConversion();
       }
    });

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if(encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        } else {
            return null;
        }

    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.getName());
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }
    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversationId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion() {
        if(chatMessages.size() != 0) {
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.getId()
            );
            checkForConversionRemotely(
                    receiverUser.getId(),
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversationOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversationOnCompleteListener = task -> {
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversationId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityReceiver();
    }
}