package io.skua.configurationservice;

import io.eider.annotation.EiderAttribute;
import io.eider.annotation.EiderSpec;

@EiderSpec(wireProtocolId = 100, name = "RegisterQuillServiceCommand")
public class RegisterQuillService
{
    private long correlationId;
    @EiderAttribute(repeatedRecord = true)
    private QuillHostConnection quillGateway;
}

