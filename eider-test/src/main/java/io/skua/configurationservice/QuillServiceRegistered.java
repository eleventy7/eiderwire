package io.skua.configurationservice;

import io.eider.annotation.EiderSpec;

@EiderSpec(wireProtocolId = 101, name = "QuillServiceRegisteredEvent")
public class QuillServiceRegistered
{
    private long correlationId;
    private boolean success;
    private short statusCode;
}
