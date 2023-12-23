package io.eider.tool;

import io.eider.language.eiderLexer;
import io.eider.language.eiderParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EiderTool
{

    private static final String SAMPLE = "syntax = \"eiderwire\";\n" +
        "package io.eider;\n" +
        "\n" +
        "enum Corpus {\n" +
        "    UNIVERSAL = 0;\n" +
        "    WEB = 1;\n" +
        "    IMAGES = 2;\n" +
        "    LOCAL = 3;\n" +
        "    NEWS = 4;\n" +
        "    PRODUCTS = 5;\n" +
        "    VIDEO = 6;\n" +
        "}\n" +
        "\n" +
        "message SearchRequest {\n" +
        "  string query = 1;\n" +
        "  @repeated\n" +
        "  int32 repeatedField = 2;\n" +
        "  int32 thingMaBob = 3;\n" +
        "  Corpus corpus = 4;\n" +
        "}\n" +
        "message Foobar {\n" +
        "  int64 query = 1;\n" +
        "  int32 page_number = 2;\n" +
        "  int32 result_per_page = 3;\n" +
        "  int16 corpus = 4;\n" +
        "}";

    public static void main(final String[] args) throws IOException
    {
        final InputStream stream = new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8));
        final CharStream fromStream = CharStreams.fromStream(stream);
        final eiderLexer lexer = new eiderLexer(fromStream);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final eiderParser parser = new eiderParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ParserErrorListener.INSTANCE);
        final EiderLanguageVisitor visitor = new EiderLanguageVisitor();
        visitor.visit(parser.eider());
        System.out.println(visitor.getContext().getSyntax());
        //parser.eider();

    }
}
