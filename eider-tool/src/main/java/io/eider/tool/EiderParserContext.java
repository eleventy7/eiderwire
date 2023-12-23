package io.eider.tool;

import io.eider.internals.PreprocessedEiderEnum;
import io.eider.internals.PreprocessedEiderEnumItem;
import io.eider.internals.PreprocessedEiderMessage;
import io.eider.internals.PreprocessedEiderProperty;

import java.util.ArrayList;
import java.util.List;

public class EiderParserContext
{
    private final List<PreprocessedEiderMessage> messages = new ArrayList<>();
    private final List<PreprocessedEiderEnum> enums = new ArrayList<>();
    private String syntax;
    private String packageName;
    private List<PreprocessedEiderProperty> workingMsgFields;
    private List<PreprocessedEiderEnumItem> workingEnumItems;

    public String getSyntax()
    {
        return syntax;
    }

    public void setSyntax(final String syntax)
    {
        this.syntax = syntax;
    }

    public void setPackageName(final String packageName)
    {
        this.packageName = packageName;
    }

    public void startMessage()
    {

        workingMsgFields = new ArrayList<>();
    }

    public void endMessage(final String name)
    {
//        messages.add(new PreprocessedEiderMessage(name, "zzz", (short) 0, (short) 0, packageName,
//                true, false, "", false, false, true,
//                workingMsgFields));
    }

    public void addMessageAnnotation(final String name)
    {

    }

    public void addMessageFieldAnnotation(final String name)
    {

    }

    public void addMessageField(final String type, final String name)
    {
        boolean enumFound = false;
        for (final PreprocessedEiderEnum enumItem : enums)
        {
            if (enumItem.getName().equalsIgnoreCase(type))
            {
                enumFound = true;
                // workingMsgFields.add(new PreprocessedEiderProperty(name,
                //       EiderPropertyType.ENUM, null, type, new HashMap<>()));
                break;
            }
        }
        if (!enumFound)
        {
            //workingMsgFields.add(new PreprocessedEiderProperty(name,
            //      EiderPropertyType.fromAntlr(type), null, null, new HashMap<>()));

        }

    }

    public void startEnum()
    {
        workingEnumItems = new ArrayList<>();
    }

    public void endEnum(final String name)
    {
        // enums.add(new PreprocessedEiderEnum(name, RepresentationType.SHORT, workingEnumItems));
    }

    public void addEnumItem(final String name, final String value)
    {
        workingEnumItems.add(new PreprocessedEiderEnumItem(name, value));
    }
}
