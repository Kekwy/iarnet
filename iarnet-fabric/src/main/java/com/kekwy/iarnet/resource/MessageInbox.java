package com.kekwy.iarnet.resource;

public interface MessageInbox<T> {

    T get();
    void put(T message);

}
