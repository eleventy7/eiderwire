/*
 * Copyright ©2019-2022 Shaun Laurens
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

import static io.eider.javawriter.agrona.Constants.BUFFER;
import static io.eider.javawriter.agrona.Constants.FALSE;
import static io.eider.javawriter.agrona.Constants.JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN;
import static io.eider.javawriter.agrona.Constants.JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1;
import static io.eider.javawriter.agrona.Constants.MUTABLE_BUFFER;
import static io.eider.javawriter.agrona.Constants.OFFSET;
import static io.eider.javawriter.agrona.Constants.RETURN_TRUE;
import static io.eider.javawriter.agrona.Constants.UNSAFE_BUFFER;
import static io.eider.javawriter.agrona.Constants.WRITE;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.eider.internals.EiderPropertyType;
import io.eider.internals.PreprocessedEiderMessage;
import io.eider.internals.PreprocessedEiderProperty;
import io.eider.internals.PreprocessedEiderRepeatableRecord;


public class AgronaSpecGenerator
{

    private static final String BUFFER_LENGTH = "BUFFER_LENGTH";
    private static final String UNIQUE_INDEX_FOR = "uniqueIndexFor";
    private static final String VALUE = "value";
    private static final String CLEAR = ".clear()";
    private static final String PUT_ALL = ".putAll(";
    private static final String UNIQUE_INDEX_COPY_FOR = "uniqueIndexCopyFor";
    private static final String FLYWEIGHT_LOCK_KEY_ID = "flyweight.lockKeyId()";
    private static final String COMMITTED_SIZE = "CommittedSize";
    private static final String RETURN = "return ";
    private static final String FINAL = "final ";
    private static final String VALUE_JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN = ", value, java.nio.ByteOrder.LITTLE_ENDIAN)";


    public boolean hasAtLeastOneRecord(final PreprocessedEiderMessage object)
    {
        for (final PreprocessedEiderProperty property : object.getPropertyList())
        {
            if (property.getType().equals(EiderPropertyType.REPEATABLE_RECORD))
            {
                return true;
            }
        }
        return false;
    }

    public List<PreprocessedEiderRepeatableRecord> listRecords(final PreprocessedEiderMessage object,
                                                               final List<PreprocessedEiderRepeatableRecord> records)
    {
        final List<PreprocessedEiderRepeatableRecord> results = new ArrayList<>();
        for (final PreprocessedEiderProperty property : object.getPropertyList())
        {
            if (property.getType().equals(EiderPropertyType.REPEATABLE_RECORD))
            {
                for (final PreprocessedEiderRepeatableRecord rec : records)
                {
                    if (property.getRecordType().contains(rec.getClassNameInput()))
                    {
                        results.add(rec);
                    }
                }
            }
        }
        return results;
    }

    public void generateSpecObject(final ProcessingEnvironment processingEnv,
                                   final PreprocessedEiderMessage object,
                                   final List<PreprocessedEiderRepeatableRecord> records,
                                   final AgronaWriterState state,
                                   final AgronaWriterGlobalState globalState)
    {
        TypeSpec.Builder builder = TypeSpec.classBuilder(object.getName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unused\"").build())
            .addField(buildEiderIdField(object.getEiderId(), object.mustBuildHeader()));

        builder.addFields(offsetsForFields(object, records, state, globalState))
            .addFields(internalFields(object, records))
            .addMethod(buildSetUnderlyingBuffer(object))
            .addMethod(buildEiderId())
            .addMethods(forInternalFields(object));

        if (object.mustBuildHeader())
        {
            builder.addField(buildEiderGroupIdField(object.getEiderGroupId()))
                .addMethod(buildSetUnderlyingBufferAndWriteHeader());
        }

        if (hasAtLeastOneRecord(object))
        {
            builder.addMethods(buildRecordHelpers(object, records));
        }

        TypeSpec generated = builder.build();

        JavaFile javaFile = JavaFile.builder(object.getPackageNameGen(), generated)
            .build();

        try
        { // write the file
            JavaFileObject source = processingEnv.getFiler()
                .createSourceFile(object.getPackageNameGen() + "." + object.getName());
            Writer writer = source.openWriter();
            javaFile.writeTo(writer);
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            // Note: calling e.printStackTrace() will print IO errors
            // that occur from the file already existing after its first run, this is normal
        }
    }

    private Iterable<MethodSpec> buildRecordHelpers(PreprocessedEiderMessage object,
                                                    List<PreprocessedEiderRepeatableRecord> records)
    {
        final List<PreprocessedEiderRepeatableRecord> toGen = listRecords(object, records);
        final List<MethodSpec> methods = new ArrayList<>();

        //3 methods:
        // - precomputeBufferLength(with per rec type item count), allows for tryClaims
        // - resize(new item count) - method per rec type
        // - .record(int offset) - gets the generated spec for the record at given offset

        MethodSpec.Builder precomputeBufferLength = MethodSpec.methodBuilder("precomputeBufferLength")
            .addJavadoc("Precomputes the required buffer length with the given record sizes")
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class);

        MethodSpec.Builder committedBufferLength = MethodSpec.methodBuilder("committedBufferLength")
            .addJavadoc("The required buffer size given current max record counts")
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class);

        String preCompute = "return";
        String committed = "return";
        for (final PreprocessedEiderRepeatableRecord rec : toGen)
        {
            precomputeBufferLength.addParameter(int.class, rec.getName() + "Count", Modifier.FINAL);
            preCompute += " BUFFER_LENGTH + (" + rec.getName() + "Count * " + rec.getClassNameInput()
                + ".BUFFER_LENGTH) +";
            committed += " BUFFER_LENGTH + (" + rec.getName().toUpperCase() + "_COMMITTED_SIZE * "
                + rec.getClassNameInput() + ".BUFFER_LENGTH) +";
        }
        preCompute += ";";
        committed += ";";
        precomputeBufferLength.addStatement(preCompute.replace(" +;", ""));
        committedBufferLength.addStatement(committed.replace(" +;", ""));
        methods.add(precomputeBufferLength.build());
        methods.add(committedBufferLength.build());

        for (final PreprocessedEiderRepeatableRecord rec : toGen)
        {
            MethodSpec.Builder resetSize = MethodSpec.methodBuilder("reset" + rec.getName() + "Size")
                .addJavadoc("Sets the amount of " + rec.getName() + " items that can be written to the buffer")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, rec.getName() + COMMITTED_SIZE, Modifier.FINAL)
                .addStatement(rec.getName().toUpperCase() + "_COMMITTED_SIZE = " + rec.getName() + COMMITTED_SIZE)
                .addStatement("buffer.checkLimit(committedBufferLength())")
                .addStatement("mutableBuffer.putInt(" + rec.getName().toUpperCase() + "_COUNT_OFFSET + initialOffset, "
                    + rec.getName() + COMMITTED_SIZE + ", java.nio.ByteOrder.LITTLE_ENDIAN)")
                .returns(void.class);
            methods.add(resetSize.build());

            MethodSpec.Builder readSize = MethodSpec.methodBuilder("read" + rec.getName() + "Size")
                .addJavadoc("Returns & internally sets the amount of " + rec.getName()
                    + " items that the buffer potentially contains")
                .addModifiers(Modifier.PUBLIC)
                .addStatement(rec.getName().toUpperCase() + "_COMMITTED_SIZE = mutableBuffer.getInt("
                    + rec.getName().toUpperCase() + "_COUNT_OFFSET)")
                .addStatement(RETURN + rec.getName().toUpperCase() + "_COMMITTED_SIZE")
                .returns(int.class);
            methods.add(readSize.build());

            final ClassName recordName = ClassName.get(rec.getPackageNameGen(), rec.getName());

            MethodSpec.Builder getRecordAtOffset = MethodSpec.methodBuilder("get" + rec.getName())
                .addJavadoc("Gets the " + rec.getName() + " flyweight at the given index")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "offset", Modifier.FINAL)
                .addStatement("if (" + rec.getName().toUpperCase() + "_COMMITTED_SIZE < offset) throw new "
                    + "RuntimeException(\"cannot access record beyond committed size\")")
                .addStatement(rec.getName().toUpperCase() + "_FLYWEIGHT.setUnderlyingBuffer(this.buffer, "
                    + rec.getName().toUpperCase() + "_RECORD_START_OFFSET + initialOffset + (offset * "
                    + rec.getName() + ".BUFFER_LENGTH))")
                .addStatement(RETURN + rec.getName().toUpperCase() + "_FLYWEIGHT")
                .returns(recordName);
            methods.add(getRecordAtOffset.build());
        }
        return methods;
    }

    private Iterable<FieldSpec> internalFields(PreprocessedEiderMessage object,
                                               List<PreprocessedEiderRepeatableRecord> recs)
    {
        List<FieldSpec> results = new ArrayList<>();

        results.add(FieldSpec
            .builder(DirectBuffer.class, BUFFER)
            .addJavadoc("The internal DirectBuffer.")
            .addModifiers(Modifier.PRIVATE)
            .initializer("null")
            .build());

        results.add(FieldSpec
            .builder(MutableDirectBuffer.class, MUTABLE_BUFFER)
            .addJavadoc("The internal DirectBuffer used for mutatation opertions. "
                +
                "Valid only if a mutable buffer was provided.")
            .addModifiers(Modifier.PRIVATE)
            .initializer("null")
            .build());

        results.add(FieldSpec
            .builder(UnsafeBuffer.class, UNSAFE_BUFFER)
            .addJavadoc("The internal UnsafeBuffer. Valid only if an unsafe buffer was provided.")
            .addModifiers(Modifier.PRIVATE)
            .initializer("null")
            .build());

        results.add(FieldSpec
            .builder(int.class, "initialOffset")
            .addJavadoc("The starting offset for reading and writing.")
            .addModifiers(Modifier.PRIVATE)
            .build());

        results.add(FieldSpec
            .builder(boolean.class, "isMutable")
            .addJavadoc("Flag indicating if the buffer is mutable.")
            .addModifiers(Modifier.PRIVATE)
            .initializer(FALSE)
            .build());

        results.add(FieldSpec
            .builder(boolean.class, "isUnsafe")
            .addJavadoc("Flag indicating if the buffer is an UnsafeBuffer.")
            .addModifiers(Modifier.PRIVATE)
            .initializer(FALSE)
            .build());

        results.add(FieldSpec
            .builder(boolean.class, "FIXED_LENGTH")
            .addJavadoc("Indicates if this flyweight holds a fixed length object.")
            .addModifiers(Modifier.STATIC)
            .addModifiers(Modifier.PUBLIC)
            .addModifiers(Modifier.FINAL)
            .initializer(Boolean.toString(!hasAtLeastOneRecord(object)))
            .build());


        if (hasAtLeastOneRecord(object))
        {
            final List<PreprocessedEiderRepeatableRecord> records = listRecords(object, recs);
            for (final PreprocessedEiderRepeatableRecord rec : records)
            {
                results.add(FieldSpec
                    .builder(int.class, rec.getName().toUpperCase() + "_COMMITTED_SIZE")
                    .addJavadoc("The max number of items allocated for this record. Use resize() to alter.")
                    .initializer("0")
                    .addModifiers(Modifier.PRIVATE)
                    .build());

                final ClassName recordName = ClassName.get(rec.getPackageNameGen(), rec.getName());
                results.add(FieldSpec
                    .builder(recordName, rec.getName().toUpperCase() + "_FLYWEIGHT")
                    .addJavadoc("The flyweight for the " + rec.getName() + " record.")
                    .initializer("new " + rec.getName() + "()")
                    .addModifiers(Modifier.PRIVATE)
                    .build());

            }
        }


        return results;
    }


    private Iterable<FieldSpec> offsetsForFields(PreprocessedEiderMessage object,
                                                 List<PreprocessedEiderRepeatableRecord> records,
                                                 AgronaWriterState state,
                                                 AgronaWriterGlobalState globalState)
    {
        List<FieldSpec> results = new ArrayList<>();

        if (object.mustBuildHeader())
        {
            results.add(FieldSpec
                .builder(int.class, "HEADER_OFFSET")
                .addJavadoc("The offset for the WIRE_PROTOCOL_ID within the buffer.")
                .addModifiers(Modifier.STATIC)
                .addModifiers(Modifier.PRIVATE)
                .addModifiers(Modifier.FINAL)
                .initializer(Integer.toString(state.getCurrentOffset()))
                .build());

            state.extendCurrentOffset(Short.BYTES);

            results.add(FieldSpec
                .builder(int.class, "HEADER_VERSION_OFFSET")
                .addJavadoc("The offset for the WIRE_PROTOCOL_VERSION within the buffer.")
                .addModifiers(Modifier.STATIC)
                .addModifiers(Modifier.PRIVATE)
                .addModifiers(Modifier.FINAL)
                .initializer(Integer.toString(state.getCurrentOffset()))
                .build());

            state.extendCurrentOffset(Short.BYTES);

            results.add(FieldSpec
                .builder(int.class, "LENGTH_OFFSET")
                .addJavadoc("The length offset. Required for segmented buffers.")
                .addModifiers(Modifier.STATIC)
                .addModifiers(Modifier.PRIVATE)
                .addModifiers(Modifier.FINAL)
                .initializer(Integer.toString(state.getCurrentOffset()))
                .build());

            state.extendCurrentOffset(Integer.BYTES);
        }

        for (final PreprocessedEiderProperty property : object.getPropertyList())
        {
            if (property.getType() != EiderPropertyType.REPEATABLE_RECORD)
            {
                results.add(genOffset(property, state));
            }
        }

        if (hasAtLeastOneRecord(object))
        {
            final List<PreprocessedEiderRepeatableRecord> recs = listRecords(object, records);
            for (final PreprocessedEiderRepeatableRecord rec : recs)
            {
                PreprocessedEiderProperty fake = new PreprocessedEiderProperty(rec.getName().toUpperCase() + "_COUNT",
                    EiderPropertyType.INT, "", Collections.emptyMap());
                results.add(genOffset(fake, state));

                results.add(FieldSpec.builder(int.class, rec.getName().toUpperCase() + "_RECORD_START_OFFSET")
                    .addJavadoc("The byte offset in the byte array to start writing " + rec.getName() + ".")
                    .addModifiers(Modifier.STATIC)
                    .addModifiers(Modifier.PRIVATE)
                    .addModifiers(Modifier.FINAL)
                    .initializer(Integer.toString(state.getCurrentOffset()))
                    .build());
            }
        }


        if (!hasAtLeastOneRecord(object))
        {
            results.add(FieldSpec
                .builder(int.class, BUFFER_LENGTH)
                .addJavadoc("The total bytes required to store this fixed length object.")
                .addModifiers(Modifier.STATIC)
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.FINAL)
                .initializer(Integer.toString(state.getCurrentOffset()))
                .build());
        }
        else
        {
            results.add(FieldSpec
                .builder(int.class, BUFFER_LENGTH)
                .addJavadoc("The total bytes required to store the core data, excluding any repeating record data. "
                    + "Use precomputeBufferLength to compute buffer length this object.")
                .addModifiers(Modifier.STATIC)
                .addModifiers(Modifier.PRIVATE)
                .addModifiers(Modifier.FINAL)
                .initializer(Integer.toString(state.getCurrentOffset()))
                .build());
        }

        globalState.getBufferLengths().put(object.getName(), state.getCurrentOffset());

        return results;
    }

    private FieldSpec genOffset(PreprocessedEiderProperty property,
                                AgronaWriterState runningOffset)
    {
        int bytes = Util.byteLength(property.getType(), property.getAnnotations());
        int startAt = runningOffset.getCurrentOffset();
        runningOffset.extendCurrentOffset(bytes);

        return FieldSpec
            .builder(int.class, getOffsetName(property.getName()))
            .addJavadoc("The byte offset in the byte array for this " + property.getType().name()
                + ". Byte length is " + bytes + ".")
            .addModifiers(Modifier.STATIC)
            .addModifiers(Modifier.PRIVATE)
            .addModifiers(Modifier.FINAL)
            .initializer(Integer.toString(startAt))
            .build();
    }

    private String getOffsetName(String name)
    {
        return name.toUpperCase() + "_OFFSET";
    }

    @SuppressWarnings("all")
    private Iterable<MethodSpec> forInternalFields(PreprocessedEiderMessage object)
    {
        List<PreprocessedEiderProperty> propertyList = object.getPropertyList();
        List<MethodSpec> results = new ArrayList<>();

        if (object.mustBuildHeader())
        {
            results.add(
                MethodSpec.methodBuilder("writeHeader")
                    .addJavadoc("Writes the header data to the buffer.")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("if (!isMutable) throw new RuntimeException(\"cannot write to immutable buffer\")")
                    .addStatement("mutableBuffer.putShort(initialOffset + HEADER_OFFSET"
                        +
                        ", WIRE_PROTOCOL_ID, "
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN)
                    .addStatement("mutableBuffer.putShort(initialOffset + HEADER_VERSION_OFFSET"
                        +
                        ", WIRE_PROTOCOL_VERSION, "
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN)
                    .addStatement("mutableBuffer.putInt(initialOffset + LENGTH_OFFSET"
                        +
                        ", BUFFER_LENGTH, "
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN)
                    .build()
            );

            results.add(
                MethodSpec.methodBuilder("validateHeader")
                    .addModifiers(Modifier.PUBLIC)
                    .addJavadoc("Validates the length and wireProtocolId in the header "
                        + "against the expected values. False if invalid.")
                    .returns(boolean.class)
                    .addStatement("final short wireProtocolId = buffer.getShort(initialOffset + HEADER_OFFSET"
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1)
                    .addStatement("final short wireProtocolVersion = buffer.getShort(initialOffset + " +
                        "HEADER_VERSION_OFFSET"
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1)
                    .addStatement("final int bufferLength = buffer.getInt(initialOffset + LENGTH_OFFSET"
                        + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1)
                    .addStatement("if (wireProtocolId != WIRE_PROTOCOL_ID) return false")
                    .addStatement("if (wireProtocolVersion != WIRE_PROTOCOL_VERSION) return false")
                    .addStatement("return bufferLength == BUFFER_LENGTH")
                    .build()
            );
        }

        for (final PreprocessedEiderProperty property : propertyList)
        {
            if (property.getType() == EiderPropertyType.REPEATABLE_RECORD)
            {
                break;
            }

            results.add(genReadProperty(property));
            results.add(genWriteProperty(property));
            if (property.getType() == EiderPropertyType.FIXED_STRING)
            {
                results.add(genWritePropertyWithPadding(property));
            }

        }

        return results;
    }


    private MethodSpec genWritePropertyWithPadding(PreprocessedEiderProperty property)
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(WRITE + Util.upperFirst(property.getName()
                + "WithPadding"))
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addJavadoc("Writes " + property.getName() + " to the buffer with padding. ")
            .addParameter(getInputType(property));

        final String underlying = WRITE + Util.upperFirst(property.getName());
        int maxLength = Integer.parseInt(property.getAnnotations().get(AttributeConstants.MAXLENGTH));
        builder.addStatement("final String padded = String.format(\"%" + maxLength + "s\", value)");
        builder.addStatement(RETURN + underlying + "(padded)");
        return builder.build();
    }


    private MethodSpec getKeyLock(PreprocessedEiderProperty property)
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("lockKey" + Util.upperFirst(property.getName()))
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Prevents any further updates to the key field.")
            .addStatement("keyLocked = true");

        return builder.build();
    }

    private MethodSpec genWriteProperty(PreprocessedEiderProperty property)
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(WRITE + Util.upperFirst(property.getName()))
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addJavadoc("Writes " + property.getName() + " to the buffer. Returns true if success, false if not.")
            .addParameter(getInputType(property));

        builder.addStatement("if (!isMutable) throw new RuntimeException(\"Cannot write to immutable buffer\")");

        if (property.getType() == EiderPropertyType.FIXED_STRING)
        {
            int maxLength = Integer.parseInt(property.getAnnotations().get(AttributeConstants.MAXLENGTH));
            builder.addStatement(fixedLengthStringCheck(property, maxLength));
        }

        if (property.getType() == EiderPropertyType.FIXED_STRING)
        {
            builder.addJavadoc("Warning! Does not pad the string.");
            builder.addStatement("mutableBuffer.putStringWithoutLengthAscii(initialOffset + "
                + getOffsetName(property.getName()) + ", value)");
        }
        else
        {
            builder.addStatement(bufferWrite(property));
        }
        builder.addStatement(RETURN_TRUE);
        return builder.build();
    }

    private ParameterSpec getInputType(PreprocessedEiderProperty property)
    {
        return ParameterSpec.builder(Util.fromType(property.getType()), VALUE, Modifier.FINAL)
            .addJavadoc("Value for the " + property.getName() + " to write to buffer.")
            .build();
    }

    private String fixedLengthStringCheck(PreprocessedEiderProperty property, int maxLength)
    {
        return "if (value.length() > " + maxLength + ") throw new RuntimeException(\"Field "
            + property.getName() + " is longer than maxLength=" + maxLength + "\")";
    }

    private String bufferWrite(PreprocessedEiderProperty property)
    {
        if (property.getType() == EiderPropertyType.INT)
        {
            return "mutableBuffer.putInt(initialOffset + " + getOffsetName(property.getName())
                +
                ", value, "
                + JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN;
        }
        else if (property.getType() == EiderPropertyType.LONG)
        {
            return "mutableBuffer.putLong(initialOffset + " + getOffsetName(property.getName())
                +
                VALUE_JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN;
        }
        else if (property.getType() == EiderPropertyType.SHORT)
        {
            return "mutableBuffer.putShort(initialOffset + " + getOffsetName(property.getName())
                +
                VALUE_JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN;
        }
        else if (property.getType() == EiderPropertyType.DOUBLE)
        {
            return "mutableBuffer.putDouble(initialOffset + " + getOffsetName(property.getName())
                +
                VALUE_JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN;
        }
        else if (property.getType() == EiderPropertyType.FIXED_STRING)
        {
            return "mutableBuffer.putStringWithoutLengthAscii(initialOffset + " + getOffsetName(property.getName())
                +
                ", value)";
        }
        else if (property.getType() == EiderPropertyType.BOOLEAN)
        {
            return "mutableBuffer.putByte(initialOffset + " + getOffsetName(property.getName())
                +
                ", value ? (byte)1 : (byte)0)";
        }
        return "// unsupported type " + property.getType().name();
    }

    private MethodSpec genReadProperty(PreprocessedEiderProperty property)
    {
        return MethodSpec.methodBuilder("read" + Util.upperFirst(property.getName()))
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Reads " + property.getName() + " as stored in the buffer.")
            .returns(Util.fromType(property.getType()))
            .addStatement(bufferRead(property))
            .build();
    }

    private String bufferRead(PreprocessedEiderProperty property)
    {
        if (property.getType() == EiderPropertyType.INT)
        {
            return "return buffer.getInt(initialOffset + " + getOffsetName(property.getName())
                +
                JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1;
        }
        else if (property.getType() == EiderPropertyType.LONG)
        {
            return "return buffer.getLong(initialOffset + " + getOffsetName(property.getName())
                +
                JAVA_NIO_BYTE_ORDER_LITTLE_ENDIAN1;
        }
        else if (property.getType() == EiderPropertyType.FIXED_STRING)
        {
            int length = Integer.parseInt(property.getAnnotations().get(AttributeConstants.MAXLENGTH));
            return "return buffer.getStringWithoutLengthAscii(initialOffset + " + getOffsetName(property.getName())
                +
                ", " + length + ").trim()";
        }
        else if (property.getType() == EiderPropertyType.BOOLEAN)
        {
            return "return buffer.getByte(initialOffset + " + getOffsetName(property.getName())
                +
                ") == (byte)1";
        }
        else if (property.getType() == EiderPropertyType.SHORT)
        {
            return "return buffer.getShort(initialOffset + " + getOffsetName(property.getName())
                +
                ")";
        }
        else if (property.getType() == EiderPropertyType.DOUBLE)
        {
            return "return buffer.getDouble(initialOffset + " + getOffsetName(property.getName())
                +
                ")";
        }
        return "// unsupported type " + property.getType().name();
    }


    private FieldSpec buildEiderIdField(short eiderId, boolean hasHeader)
    {
        final String comment;
        if (hasHeader)
        {
            comment = "The wire protocol id for this type. Useful in switch statements to detect type in first 16bits.";
        }
        else
        {
            comment = "The wire protocol spec id for this type. Not written to the output buffer as there is no "
                + "header.";
        }

        return FieldSpec
            .builder(short.class, "WIRE_PROTOCOL_ID")
            .addJavadoc(comment)
            .addModifiers(Modifier.STATIC)
            .addModifiers(Modifier.PUBLIC)
            .addModifiers(Modifier.FINAL)
            .initializer(Short.toString(eiderId))
            .build();
    }

    private FieldSpec buildEiderGroupIdField(short groupId)
    {
        return FieldSpec
            .builder(short.class, "WIRE_PROTOCOL_VERSION")
            .addJavadoc("The wire protocol version for this type.")
            .addModifiers(Modifier.STATIC)
            .addModifiers(Modifier.PUBLIC)
            .addModifiers(Modifier.FINAL)
            .initializer(Short.toString(groupId))
            .build();
    }

    private MethodSpec buildEiderId()
    {
        return MethodSpec.methodBuilder("wireProtocolId")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Returns the wire protocol id.\n"
                +
                "@return WIRE_PROTOCOL_ID.\n")
            .returns(short.class)
            .addStatement("return WIRE_PROTOCOL_ID")
            .build();

    }

    private MethodSpec buildSetUnderlyingBuffer(PreprocessedEiderMessage object)
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("setUnderlyingBuffer")
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addJavadoc("Uses the provided {@link org.agrona.DirectBuffer} from the given offset.\n"
                +
                "@param buffer - buffer to read from and write to.\n"
                +
                "@param offset - offset to begin reading from/writing to in the buffer.\n")
            .addParameter(DirectBuffer.class, BUFFER, Modifier.FINAL)
            .addParameter(int.class, OFFSET, Modifier.FINAL)
            .addStatement("this.initialOffset = offset")
            .addStatement("this.buffer = buffer")
            .beginControlFlow("if (buffer instanceof UnsafeBuffer)")
            .addStatement(UNSAFE_BUFFER + " = (UnsafeBuffer) buffer")
            .addStatement(MUTABLE_BUFFER + " = (MutableDirectBuffer) buffer")
            .addStatement("isUnsafe = true")
            .addStatement("isMutable = true")
            .endControlFlow()
            .beginControlFlow("else if (buffer instanceof MutableDirectBuffer)")
            .addStatement(MUTABLE_BUFFER + " = (MutableDirectBuffer) buffer")
            .addStatement("isUnsafe = false")
            .addStatement("isMutable = true")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("isUnsafe = false")
            .addStatement("isMutable = false")
            .endControlFlow();

        builder.addStatement("buffer.checkLimit(initialOffset + BUFFER_LENGTH)");
        return builder.build();
    }


    private MethodSpec buildSetUnderlyingBufferAndWriteHeader()
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("setBufferWriteHeader")
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addJavadoc("Uses the provided {@link org.agrona.DirectBuffer} from the given offset.\n"
                +
                "@param buffer - buffer to read from and write to.\n"
                +
                "@param offset - offset to begin reading from/writing to in the buffer.\n")
            .addParameter(DirectBuffer.class, BUFFER, Modifier.FINAL)
            .addParameter(int.class, OFFSET, Modifier.FINAL)
            .addStatement("setUnderlyingBuffer(buffer, offset)")
            .addStatement("writeHeader()");

        return builder.build();
    }

    public void generateSpecRecord(ProcessingEnvironment pe,
                                   PreprocessedEiderRepeatableRecord rec,
                                   AgronaWriterGlobalState globalState)
    {
        TypeSpec.Builder builder = TypeSpec.classBuilder(rec.getName())
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unused\"").build())
            .addModifiers(Modifier.PUBLIC);

        AgronaWriterState state = new AgronaWriterState();

        builder.addFields(offsetsForRecFields(rec, state, globalState))
            .addFields(internalRecFields())
            .addMethod(buildSetUnderlyingRecBuffer())
            .addMethods(forInternalRecFields(rec));

        TypeSpec generated = builder.build();

        JavaFile javaFile = JavaFile.builder(rec.getPackageNameGen(), generated)
            .build();

        try
        { // write the file
            JavaFileObject source = pe.getFiler()
                .createSourceFile(rec.getPackageNameGen() + "." + rec.getName());
            Writer writer = source.openWriter();
            javaFile.writeTo(writer);
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            // Note: calling e.printStackTrace() will print IO errors
            // that occur from the file already existing after its first run, this is normal
        }
    }

    private Iterable<MethodSpec> forInternalRecFields(PreprocessedEiderRepeatableRecord rec)
    {
        List<PreprocessedEiderProperty> propertyList = rec.getPropertyList();
        List<MethodSpec> results = new ArrayList<>();

        for (final PreprocessedEiderProperty property : propertyList)
        {
            if (property.getType() == EiderPropertyType.REPEATABLE_RECORD)
            {
                break;
            }

            results.add(genReadProperty(property));
            results.add(genWriteProperty(property));
            if (property.getType() == EiderPropertyType.FIXED_STRING)
            {
                results.add(genWritePropertyWithPadding(property));
            }
        }

        return results;
    }

    private Iterable<FieldSpec> offsetsForRecFields(PreprocessedEiderRepeatableRecord rec,
                                                    AgronaWriterState state,
                                                    AgronaWriterGlobalState globalState)
    {
        List<FieldSpec> results = new ArrayList<>();

        for (final PreprocessedEiderProperty property : rec.getPropertyList())
        {
            if (property.getType() != EiderPropertyType.REPEATABLE_RECORD)
            {
                results.add(genOffset(property, state));
            }
        }

        results.add(FieldSpec
            .builder(int.class, BUFFER_LENGTH)
            .addJavadoc("The total bytes required to store a single record.")
            .addModifiers(Modifier.STATIC)
            .addModifiers(Modifier.PUBLIC)
            .addModifiers(Modifier.FINAL)
            .initializer(Integer.toString(state.getCurrentOffset()))
            .build());

        globalState.getBufferLengths().put(rec.getName(), state.getCurrentOffset());

        return results;
    }


    private MethodSpec buildSetUnderlyingRecBuffer()
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("setUnderlyingBuffer")
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addJavadoc("Uses the provided {@link org.agrona.DirectBuffer} from the given offset.\n"
                +
                "@param buffer - buffer to read from and write to.\n"
                +
                "@param offset - offset to begin reading from/writing to in the buffer.\n")
            .addParameter(DirectBuffer.class, BUFFER, Modifier.FINAL)
            .addParameter(int.class, OFFSET, Modifier.FINAL)
            .addStatement("this.initialOffset = offset")
            .addStatement("this.buffer = buffer")
            .beginControlFlow("if (buffer instanceof MutableDirectBuffer)")
            .addStatement(MUTABLE_BUFFER + " = (MutableDirectBuffer) buffer")
            .addStatement("isMutable = true")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("isMutable = false")
            .endControlFlow();

        builder.addStatement("buffer.checkLimit(initialOffset + BUFFER_LENGTH)");
        return builder.build();
    }

    private Iterable<FieldSpec> internalRecFields()
    {
        List<FieldSpec> results = new ArrayList<>();

        results.add(FieldSpec
            .builder(DirectBuffer.class, BUFFER)
            .addJavadoc("The internal DirectBuffer.")
            .addModifiers(Modifier.PRIVATE)
            .initializer("null")
            .build());

        results.add(FieldSpec
            .builder(MutableDirectBuffer.class, MUTABLE_BUFFER)
            .addJavadoc("The internal DirectBuffer used for mutatation opertions. "
                +
                "Valid only if a mutable buffer was provided.")
            .addModifiers(Modifier.PRIVATE)
            .initializer("null")
            .build());

        results.add(FieldSpec
            .builder(int.class, "initialOffset")
            .addJavadoc("The starting offset for reading and writing.")
            .addModifiers(Modifier.PRIVATE)
            .build());

        results.add(FieldSpec
            .builder(boolean.class, "isMutable")
            .addJavadoc("Flag indicating if the buffer is mutable.")
            .addModifiers(Modifier.PRIVATE)
            .initializer(FALSE)
            .build());

        return results;
    }
}
