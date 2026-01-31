package com.MagisKube.magisk.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
