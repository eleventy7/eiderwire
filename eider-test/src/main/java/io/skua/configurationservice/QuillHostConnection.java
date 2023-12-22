package io.skua.configurationservice;

import io.eider.annotation.EiderAttribute;
import io.eider.annotation.EiderRepeatableRecord;

@EiderRepeatableRecord
public class QuillHostConnection
{
    private short port;
    @EiderAttribute(maxLength = 50)
    private String hostName;
}
