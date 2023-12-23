/*
 * Copyright ©2019-2023 Shaun Laurens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 */

package io.eider.javawriter.agrona;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.eider.internals.PreprocessedEiderMessage;
import io.eider.internals.PreprocessedEiderRepeatableRecord;
import io.eider.javawriter.EiderCodeWriter;
import org.agrona.DirectBuffer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class AgronaWriter implements EiderCodeWriter
{
    private static final String IO_EIDER_UTIL = "io.eider.util";

    private final AgronaSpecGenerator specGenerator = new AgronaSpecGenerator();

    @Override
    public void generate(final ProcessingEnvironment pe,
        final List<PreprocessedEiderRepeatableRecord> records,
        final List<PreprocessedEiderMessage> objects)
    {
        String packageName = null;

        final AgronaWriterGlobalState globalState = new AgronaWriterGlobalState();
        final List<PreprocessedEiderRepeatableRecord> alreadyGeneratedRecs = new ArrayList<>();

        for (final PreprocessedEiderMessage object : objects)
        {
            if (specGenerator.hasAtLeastOneRecord(object))
            {
                final List<PreprocessedEiderRepeatableRecord> requiredRecs = specGenerator.listRecords(object, records);
                if (requiredRecs.size() > 1)
                {
                    throw new RuntimeException("cannot have more than one repeated record at this time.");
                }
                for (final PreprocessedEiderRepeatableRecord rec : requiredRecs)
                {
                    if (!alreadyGeneratedRecs.contains(rec))
                    {
                        //want the writing to be within the main object; this is just the basic outline
                        specGenerator.generateSpecRecord(pe, rec, globalState);
                        alreadyGeneratedRecs.add(rec);
                    }
                }
            }

            packageName = object.getPackageNameGen();
            final AgronaWriterState state = new AgronaWriterState();
            specGenerator.generateSpecObject(pe, object, records, state, globalState);

        }

        if (packageName != null)
        {
            generateEiderHelper(pe);
        }
    }

    private void generateEiderHelper(final ProcessingEnvironment pe)
    {
        final String packageName = IO_EIDER_UTIL;

        final TypeSpec.Builder builder = TypeSpec.classBuilder("EiderHelper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethods(buildHeaderHelperMethods());
        final TypeSpec generated = builder.build();

        final JavaFile javaFile = JavaFile.builder(packageName, generated)
            .build();

        try
        { // write the file
            final JavaFileObject source = pe.getFiler()
                .createSourceFile(packageName + ".EiderHelper");
            final Writer writer = source.openWriter();
            javaFile.writeTo(writer);
            writer.flush();
            writer.close();
        }
        catch (final IOException e)
        {
            //normal
        }
    }

    private Iterable<MethodSpec> buildHeaderHelperMethods()
    {
        final List<MethodSpec> results = new ArrayList<>();

        results.add(
            MethodSpec.constructorBuilder()
                .addJavadoc("private constructor.")
                .addModifiers(Modifier.PRIVATE)
                .addStatement("//unused")
                .build()
        );

        results.add(
            MethodSpec.methodBuilder("getEiderId")
                .addJavadoc("Reads the Eider Id from the buffer at the offset provided.")
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.STATIC)
                .returns(short.class)
                .addParameter(DirectBuffer.class, Constants.BUFFER)
                .addParameter(int.class, Constants.OFFSET)
                .addStatement("return buffer.getShort(offset" + Constants.JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1)
                .build()
        );

        results.add(
            MethodSpec.methodBuilder("getEiderGroupId")
                .addJavadoc("Reads the Eider Group Id from the buffer at the offset provided.")
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.STATIC)
                .returns(short.class)
                .addParameter(DirectBuffer.class, Constants.BUFFER)
                .addParameter(int.class, Constants.OFFSET)
                .addStatement("return buffer.getShort(offset + 2"
                    +
                    Constants.JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1)
                .build()
        );

        return results;
    }

}
