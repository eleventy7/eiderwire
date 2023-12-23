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

package io.eider.processor;

import io.eider.annotation.EiderAttribute;
import io.eider.annotation.EiderRepeatableRecord;
import io.eider.annotation.EiderSpec;
import io.eider.internals.EiderPropertyType;
import io.eider.internals.PreprocessedEiderMessage;
import io.eider.internals.PreprocessedEiderProperty;
import io.eider.internals.PreprocessedEiderRepeatableRecord;
import io.eider.javawriter.EiderCodeWriter;
import io.eider.javawriter.agrona.AgronaWriter;
import io.eider.javawriter.agrona.AttributeConstants;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes( {
    "io.eider.annotation.EiderSpec"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class EiderAnnotationProcessor extends AbstractProcessor
{
    public static final String STRING = "java.lang.String";
    private short sequence = 0;
    private EiderCodeWriter writer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        writer = new AgronaWriter();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (roundEnv.processingOver() || annotations.isEmpty())
        {
            return false;
        }

        List<PreprocessedEiderRepeatableRecord> records = new ArrayList<>();
        List<PreprocessedEiderMessage> objects = new ArrayList<>();

        for (Element el : roundEnv.getElementsAnnotatedWith(EiderRepeatableRecord.class))
        {
            boolean continueProcessing = false;
            if (el instanceof TypeElement)
            {
                continueProcessing = true;
            }

            if (!continueProcessing)
            {
                break;
            }

            TypeElement element = (TypeElement) el;
            preprocessRepeatableRecord(element, records);
        }

        for (Element el : roundEnv.getElementsAnnotatedWith(EiderSpec.class))
        {
            boolean continueProcessing = false;
            if (el instanceof TypeElement)
            {
                continueProcessing = true;
            }

            if (!continueProcessing)
            {
                break;
            }

            TypeElement element = (TypeElement) el;
            preprocessObject(element, objects, records);
        }

        writer.generate(processingEnv, records, objects);

        return true;
    }


    private PreprocessedEiderMessage getEiderObject(String attrName,
                                                    List<PreprocessedEiderMessage> objects)
    {

        final String[] f = attrName.split("\\.");
        final String typeNameNoPkg = f[f.length - 1];

        for (final PreprocessedEiderMessage object : objects)
        {
            if (object.getClassNameInput().equalsIgnoreCase(typeNameNoPkg))
            {
                return object;
            }
        }
        return null;
    }

    @SuppressWarnings("all")
    private void preprocessObject(TypeElement typeElement,
                                  final List<PreprocessedEiderMessage> objects,
                                  final List<PreprocessedEiderRepeatableRecord> records)
    {
        final String classNameInput = typeElement.getSimpleName().toString();
        final String classNameGen = classNameInput + "Eider";
        final String packageName = typeElement.getQualifiedName().toString();
        final String packageNameGen = packageName.replace(classNameInput, "gen");
        sequence += (short) 1;
        int keyFieldCount = 0;

        EiderSpec annotation = typeElement.getAnnotation(EiderSpec.class);

        final List<PreprocessedEiderProperty> preprocessedEiderProperties = new ArrayList<>();
        final List<? extends Element> enclosedElements = typeElement.getEnclosedElements();

        for (Element element : enclosedElements)
        {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.FIELD)
            {
                Map<String, String> annotations = new HashMap<>();
                boolean isFixed = false;
                EiderAttribute attribute = element.getAnnotation(EiderAttribute.class);
                if (attribute != null)
                {
                    annotations.put(AttributeConstants.MAXLENGTH, Integer.toString(attribute.maxLength()));

                    if (attribute.maxLength() != -1)
                    {
                        isFixed = true;
                    }
                }
                else
                {
                    applyDefaultAnnotations(annotations);
                }

                final String attrName = element.getSimpleName().toString();
                checkUnfixedStringInFixedObject(annotation, element, isFixed);

                final EiderPropertyType type = defineType(element.asType().toString(), isFixed, records);

                final PreprocessedEiderProperty prop = new PreprocessedEiderProperty(attrName, type,
                    element.asType().toString(), annotations);
                preprocessedEiderProperties.add(prop);
            }
        }

        final short objectEiderId;
        if (annotation.wireProtocolId() == -1)
        {
            objectEiderId = sequence;
        }
        else
        {
            objectEiderId = annotation.wireProtocolId();
        }

        final String name;
        if (!annotation.name().isEmpty())
        {
            name = annotation.name();
        }
        else
        {
            name = classNameGen;
        }


        final PreprocessedEiderMessage obj = new PreprocessedEiderMessage(name,
            classNameInput,
            objectEiderId,
            annotation.version(),
            packageNameGen,
            annotation.fixedLength(),
            annotation.header(),
            preprocessedEiderProperties);

        objects.add(obj);
    }

    @SuppressWarnings("all")
    private void preprocessRepeatableRecord(TypeElement typeElement,
                                            final List<PreprocessedEiderRepeatableRecord> records)
    {
        final String classNameInput = typeElement.getSimpleName().toString();
        final String classNameGen = classNameInput;
        final String packageName = typeElement.getQualifiedName().toString();
        final String packageNameGen = packageName.replace(classNameInput, "gen");
        sequence += (short) 1;
        int keyFieldCount = 0;

        EiderSpec annotation = typeElement.getAnnotation(EiderSpec.class);

        final List<PreprocessedEiderProperty> preprocessedEiderProperties = new ArrayList<>();
        final List<? extends Element> enclosedElements = typeElement.getEnclosedElements();

        for (Element element : enclosedElements)
        {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.FIELD)
            {
                Map<String, String> annotations = new HashMap<>();
                boolean isFixed = false;
                EiderAttribute attribute = element.getAnnotation(EiderAttribute.class);
                if (attribute != null)
                {
                    annotations.put(AttributeConstants.MAXLENGTH, Integer.toString(attribute.maxLength()));
                    annotations.put(AttributeConstants.REPEATED_RECORD, Boolean.toString(attribute.repeatedRecord()));

                    if (attribute.maxLength() != -1)
                    {
                        isFixed = true;
                    }
                }
                else
                {
                    applyDefaultAnnotations(annotations);
                }

                final String attrName = element.getSimpleName().toString();
                checkUnfixedStringInFixedObject(annotation, element, isFixed);

                //not allowing a repeatable record (by passing empty list) here as can't support records within records.
                final EiderPropertyType type = defineType(element.asType().toString(), isFixed,
                    Collections.emptyList());

                final PreprocessedEiderProperty prop = new PreprocessedEiderProperty(attrName, type, "", annotations);
                preprocessedEiderProperties.add(prop);
            }
        }

        final String name;

        final PreprocessedEiderRepeatableRecord obj = new PreprocessedEiderRepeatableRecord(classNameGen,
            classNameInput,
            packageNameGen,
            preprocessedEiderProperties);

        records.add(obj);
    }

    private void applyDefaultAnnotations(Map<String, String> annotations)
    {
        annotations.put(AttributeConstants.MAXLENGTH, Integer.toString(Integer.MIN_VALUE));
    }

    private EiderPropertyType defineType(String typeStr,
                                         boolean isFixed,
                                         List<PreprocessedEiderRepeatableRecord> records)
    {
        if (typeStr.equalsIgnoreCase(STRING) && isFixed)
        {
            return EiderPropertyType.FIXED_STRING;
        }
        else if (typeStr.equalsIgnoreCase(STRING) && !isFixed)
        {
            return EiderPropertyType.INVALID;
        }

        EiderPropertyType initialGuessType = EiderPropertyType.from(typeStr);
        if (initialGuessType == EiderPropertyType.INVALID)
        {
            for (final PreprocessedEiderRepeatableRecord record : records)
            {
                if (typeStr.contains(record.getClassNameInput()))
                {
                    return EiderPropertyType.REPEATABLE_RECORD;
                }
            }
        }

        return initialGuessType;
    }

    private void checkUnfixedStringInFixedObject(final EiderSpec annotation, final Element element,
                                                 final boolean isFixed)
    {
        if (element.asType().toString().equalsIgnoreCase(STRING)
            &&
            !isFixed
            &&
            annotation.fixedLength())
        {
            throw new EiderProcessorException("Cannot have non fixed length strings on fixed length object");
        }
    }

}
