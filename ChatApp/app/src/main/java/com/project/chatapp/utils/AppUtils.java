package com.project.chatapp.utils;

import android.content.Context;
import android.widget.Toast;

public class AppUtils {
    private AppUtils()
    {

    }
    public static void showToast(String message, Context context)
    {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
