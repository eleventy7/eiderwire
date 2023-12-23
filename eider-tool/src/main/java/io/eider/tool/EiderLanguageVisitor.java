package io.eider.tool;

import io.eider.language.eiderBaseVisitor;
import io.eider.language.eiderParser;

public class EiderLanguageVisitor extends eiderBaseVisitor<EiderParserContext>
{
    private final EiderParserContext context;

    public EiderLanguageVisitor()
    {
        super();
        context = new EiderParserContext();
    }

    @Override
    public EiderParserContext visitSyntax(final eiderParser.SyntaxContext ctx)
    {
        context.setSyntax(ctx.getChild(2).getText());
        return context;
    }

    @Override
    public EiderParserContext visitMessageDef(final eiderParser.MessageDefContext ctx)
    {
        context.startMessage();
        System.out.println("start message: " + ctx.messageName().getText());
        final var f = visitChildren(ctx);
        context.endMessage(ctx.messageName().getText());
        System.out.println("end message: " + ctx.messageName().getText());
        return f;
    }

    @Override
    public EiderParserContext visitMessageElement(final eiderParser.MessageElementContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitField(final eiderParser.FieldContext ctx)
    {
        if (ctx.getChildCount() == 5)
        { //no annotation
            context.addMessageField(ctx.getChild(0).getText(), ctx.getChild(1).getText());
        }
        else
        { //annotation; todo capture annotation
            context.addMessageField(ctx.getChild(1).getText(), ctx.getChild(2).getText());
        }
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitAnnotationStatement(final eiderParser.AnnotationStatementContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitAnnotationName(final eiderParser.AnnotationNameContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitAnnotationOptions(final eiderParser.AnnotationOptionsContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitAnnotationOption(final eiderParser.AnnotationOptionContext ctx)
    {
        if (ctx.getChildCount() == 1)
        {
            System.out.println("annotation option: " + ctx.getText());
        }
        else if (ctx.getChildCount() == 3)
        {
            System.out.println("annotation option key: " + ctx.getChild(0).getText());
            System.out.println("annotation option val: " + ctx.getChild(2).getText());
        }
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitMessageBody(final eiderParser.MessageBodyContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitEnumDef(final eiderParser.EnumDefContext ctx)
    {
        System.out.println("start enum");
        System.out.println("enum name: " + ctx.getChild(1).getText());
        context.startEnum();
        final var f = visitChildren(ctx);
        System.out.println("end enum");
        context.endEnum(ctx.getChild(1).getText());
        return f;
    }

    @Override
    public EiderParserContext visitEnumBody(final eiderParser.EnumBodyContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitEnumField(final eiderParser.EnumFieldContext ctx)
    {
        System.out.println("enum field f=" + ctx.getChild(0).getText() + " val=" + ctx.getChild(2).getText());
        context.addEnumItem(ctx.getChild(0).getText(), ctx.getChild(2).getText());
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitEnumElement(final eiderParser.EnumElementContext ctx)
    {
        return visitChildren(ctx);
    }

    @Override
    public EiderParserContext visitPackageStatement(final eiderParser.PackageStatementContext ctx)
    {
        System.out.println("package " + ctx.getChild(1).getText());
        context.setPackageName(ctx.getChild(1).getText());
        return context;
    }

    public EiderParserContext getContext()
    {
        return context;
    }

}
