package com.cja.service;


import com.cja.rpc.RPCClient;

@RPCClient(name = "test")
public interface TestService {
    void test();
}
