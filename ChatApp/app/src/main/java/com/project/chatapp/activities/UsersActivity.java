package com.project.chatapp.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.project.chatapp.R;
import com.project.chatapp.adapters.UserAdapter;
import com.project.chatapp.databinding.ActivitySignUpBinding;
import com.project.chatapp.databinding.ActivityUsersBinding;
import com.project.chatapp.listeners.UserListener;
import com.project.chatapp.models.User;
import com.project.chatapp.utils.Constants;
import com.project.chatapp.utils.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends BaseActivity implements UserListener {

    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUsers();

    }
    private void setListeners(){
        binding.imageBack.setOnClickListener(v -> onBackPressed());
    }
    private void getUsers(){
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    loading(false);
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if(task.isSuccessful() && task.getResult() != null){
                        List<User> users = new ArrayList<>();
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()){
                            if(currentUserId.equals(queryDocumentSnapshot.getId())){
                                continue;
                            }
                            User user = new User(
                                    queryDocumentSnapshot.getString(Constants.KEY_NAME),
                                    queryDocumentSnapshot.getString(Constants.KEY_IMAGE),
                                    queryDocumentSnapshot.getString(Constants.KEY_EMAIL),
                                    queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN),
                                    queryDocumentSnapshot.getId()
                            );
                            users.add(user);
                            if(users.size() > 0) {
                                UserAdapter userAdapter = new UserAdapter(users, this);
                                binding.usersRecyclerView.setAdapter(userAdapter);
                                binding.usersRecyclerView.setVisibility(View.VISIBLE);
                            } else {
                                showErrorMessage();
                            }
                        }
                    } else {
                        showErrorMessage();
                    }
                });
    }
    private void showErrorMessage(){
        binding.textErrorMessage.setText(String.format("%s","No User available"));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading){
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}