package com.kekwy.iarnet.sdk;


public interface BranchedFlow<T> {

    Flow<T> getFlow(int port);

}
