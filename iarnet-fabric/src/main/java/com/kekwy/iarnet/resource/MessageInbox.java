package com.kekwy.iarnet.resource;

public interface MessageInbox<T> {

    T receive();
    void send(T message);

}
