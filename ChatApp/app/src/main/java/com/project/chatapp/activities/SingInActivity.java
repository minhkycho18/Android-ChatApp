package com.project.chatapp.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.project.chatapp.R;
import com.project.chatapp.databinding.ActivitySingInBinding;
import com.project.chatapp.databinding.ActivitySingUpBinding;

public class SingInActivity extends AppCompatActivity {

    private ActivitySingInBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySingInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
    }

    private void setListeners() {
        binding.textCreateNewAccount.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), SingUpActivity.class)));
    }
}