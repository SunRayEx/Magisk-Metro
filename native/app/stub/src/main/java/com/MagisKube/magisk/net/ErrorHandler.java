package com.MagisKube.magisk.net;

import java.net.HttpURLConnection;

public interface ErrorHandler {
    void onError(HttpURLConnection conn, Exception e);
}
