package com.kekwy.iarnet.fabric.messaging;

public interface MessageInbox<T> {

    T get();
    void put(T message);

}
