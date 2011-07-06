package com.smartclient.annotation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

/**
 * <p>
 * <h3>With help from:</h3>
 * <ul>
 * <li>http://javasign.blogspot.com/2009/08/annotation-checking-at-compile-time.html</li>
 * <li>http://code.google.com/p/acris/wiki/AnnotationProcessing_DebuggingEclipse</li>
 * <li>http://download.oracle.com/javase/6/docs/api/javax/annotation/processing/Processor.html#process(java.util.Set, javax.annotation.processing.RoundEnvironment)</li>
 * </ul>
 * </p>
 * 
 * @author alex
 * @author $Author$
 * @version $Revision$
 */
@SupportedAnnotationTypes("com.smartclient.annotation.OverridePoint")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class OverridePointProcessor extends AbstractProcessor {

    private static final String ERROR_KIND_OPTION_KEY = "smartgwt.overridepointprocessor.error.kind";
    private Kind errorKind;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if (processingEnv.getOptions().containsKey(ERROR_KIND_OPTION_KEY)) {
            errorKind = Kind.valueOf(processingEnv.getOptions().get(ERROR_KIND_OPTION_KEY));
        }
        else {
            errorKind = Kind.WARNING;
        }
    }

    @Override
    public boolean process(Set< ? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Map<TypeElement, Set<ExecutableElement>> annotated = new HashMap<TypeElement, Set<ExecutableElement>>();

        indexAnnotatedMethodsByClass(annotations, roundEnv, annotated);

        findMethodOverrides(roundEnv, annotated);

        return true;
    }

    private void indexAnnotatedMethodsByClass(Set< ? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<TypeElement, Set<ExecutableElement>> annotated) {
        for (TypeElement typeElement : annotations) {
            Set< ? extends Element> elements = roundEnv.getElementsAnnotatedWith(typeElement);
            for (Element element : elements) {

                TypeElement e = (TypeElement) element.getEnclosingElement();

                ExecutableElement method = (ExecutableElement) element;

                if (!annotated.containsKey(e)) {
                    annotated.put(e, new HashSet<ExecutableElement>());
                }
                annotated.get(e).add(method);
            }
        }
    }

    private void findMethodOverrides(RoundEnvironment roundEnv, Map<TypeElement, Set<ExecutableElement>> annotated) {
        for (Element element : roundEnv.getRootElements()) {
            if (element.getKind().equals(ElementKind.CLASS)) {
                TypeElement klass = (TypeElement) element;
                TypeElement superKlass = getMatchingAnnotatedSuperclass(annotated, klass.getSuperclass());
                if (superKlass != null) {
                    for (Element childClassMethod : ElementFilter.methodsIn(klass.getEnclosedElements())) {
                        Element annotatedMethod = getMatchingMethod(childClassMethod, annotated.get(superKlass));

                        if (annotatedMethod == null) {
                            processingEnv.getMessager().printMessage(errorKind,
                                    "class overrides non-override-point method '" + childClassMethod.getSimpleName() + "' from " + superKlass.getQualifiedName(), klass);
                        }
                    }
                }
            }
        }
    }

    private ExecutableElement getMatchingMethod(Element implementorMethod, Set<ExecutableElement> annotatedChildMethods) {
        for (ExecutableElement annotatedMethod : annotatedChildMethods) {
            if (processingEnv.getTypeUtils().isSubsignature((ExecutableType) implementorMethod.asType(), (ExecutableType) annotatedMethod.asType())) {
                return annotatedMethod;
            }
        }
        return null;
    }

    private TypeElement getMatchingAnnotatedSuperclass(Map<TypeElement, Set<ExecutableElement>> annotated, TypeMirror klass) {
        for (TypeElement e : annotated.keySet()) {
            if (processingEnv.getTypeUtils().isSameType(e.asType(), klass)) {
                return e;
            }
        }
        return null;
    }
}
