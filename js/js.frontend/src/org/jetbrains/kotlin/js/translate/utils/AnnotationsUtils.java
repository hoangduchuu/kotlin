/*
 * Copyright 2010-2015 JetBrains s.r.o.
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.js.translate.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.kotlin.descriptors.annotations.AnnotationWithTarget;
import org.jetbrains.kotlin.descriptors.annotations.Annotations;
import org.jetbrains.kotlin.js.PredefinedAnnotation;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.resolve.DescriptorUtils;
import org.jetbrains.kotlin.resolve.constants.ConstantValue;

public final class AnnotationsUtils {
    private static final String JS_NAME = "kotlin.js.JsName";

    private AnnotationsUtils() {
    }

    public static boolean hasAnnotation(
            @NotNull DeclarationDescriptor descriptor,
            @NotNull PredefinedAnnotation annotation
    ) {
        return getAnnotationByName(descriptor, annotation) != null;
    }

    @Nullable
    private static String getAnnotationStringParameter(@NotNull DeclarationDescriptor declarationDescriptor,
            @NotNull PredefinedAnnotation annotation) {
        AnnotationDescriptor annotationDescriptor = getAnnotationByName(declarationDescriptor, annotation);
        assert annotationDescriptor != null;
        //TODO: this is a quick fix for unsupported default args problem
        if (annotationDescriptor.getAllValueArguments().isEmpty()) {
            return null;
        }
        ConstantValue<?> constant = annotationDescriptor.getAllValueArguments().values().iterator().next();
        //TODO: this is a quick fix for unsupported default args problem
        if (constant == null) {
            return null;
        }
        Object value = constant.getValue();
        assert value instanceof String : "Native function annotation should have one String parameter";
        return (String) value;
    }

    @Nullable
    public static String getNameForAnnotatedObject(@NotNull DeclarationDescriptor declarationDescriptor,
            @NotNull PredefinedAnnotation annotation) {
        if (!hasAnnotation(declarationDescriptor, annotation)) {
            return null;
        }
        return getAnnotationStringParameter(declarationDescriptor, annotation);
    }

    @Nullable
    public static String getNameForAnnotatedObject(@NotNull DeclarationDescriptor descriptor) {
        String defaultJsName = getJsName(descriptor);

        for (PredefinedAnnotation annotation : PredefinedAnnotation.Companion.getWITH_CUSTOM_NAME()) {
            if (!hasAnnotationOrInsideAnnotatedClass(descriptor, annotation)) {
                continue;
            }
            String name = getNameForAnnotatedObject(descriptor, annotation);
            if (name == null) {
                name = defaultJsName;
            }
            return name != null ? name : descriptor.getName().asString();
        }

        if (defaultJsName == null && isEffectivelyExternal(descriptor)) {
            return descriptor.getName().asString();
        }

        return defaultJsName;
    }

    @Nullable
    private static AnnotationDescriptor getAnnotationByName(
            @NotNull DeclarationDescriptor descriptor,
            @NotNull PredefinedAnnotation annotation
    ) {
        return getAnnotationByName(descriptor, annotation.getFqName());
    }

    @Nullable
    private static AnnotationDescriptor getAnnotationByName(@NotNull DeclarationDescriptor descriptor, @NotNull FqName fqName) {
        AnnotationWithTarget annotationWithTarget = Annotations.Companion.findAnyAnnotation(descriptor.getAnnotations(), (fqName));
        return annotationWithTarget != null ? annotationWithTarget.getAnnotation() : null;
    }

    public static boolean isNativeObject(@NotNull DeclarationDescriptor descriptor) {
        if (hasAnnotationOrInsideAnnotatedClass(descriptor, PredefinedAnnotation.NATIVE) || isEffectivelyExternal(descriptor)) return true;

        if (descriptor instanceof PropertyAccessorDescriptor) {
            PropertyAccessorDescriptor accessor = (PropertyAccessorDescriptor) descriptor;
            return hasAnnotationOrInsideAnnotatedClass(accessor.getCorrespondingProperty(), PredefinedAnnotation.NATIVE) ||
                   isEffectivelyExternal(descriptor);
        }

        return false;
    }

    public static boolean isNativeInterface(@NotNull DeclarationDescriptor descriptor) {
        return isNativeObject(descriptor) && DescriptorUtils.isInterface(descriptor);
    }

    private static boolean isEffectivelyExternal(@NotNull DeclarationDescriptor descriptor) {
        return descriptor instanceof MemberDescriptor && isEffectivelyExternal((MemberDescriptor) descriptor);
    }

    private static boolean isEffectivelyExternal(@NotNull MemberDescriptor descriptor) {
        if (descriptor.isExternal()) return true;

        if (descriptor instanceof FunctionDescriptor && ((FunctionDescriptor) descriptor).isInline()) return false;

        if (descriptor instanceof PropertyAccessorDescriptor) {
            PropertyDescriptor variableDescriptor = ((PropertyAccessorDescriptor) descriptor).getCorrespondingProperty();
            if (isEffectivelyExternal(variableDescriptor)) return true;
        }

        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        return containingClass != null && isEffectivelyExternal(containingClass);
    }

    public static boolean isLibraryObject(@NotNull DeclarationDescriptor descriptor) {
        return hasAnnotationOrInsideAnnotatedClass(descriptor, PredefinedAnnotation.LIBRARY);
    }

    @Nullable
    public static String getJsName(@NotNull DeclarationDescriptor descriptor) {
        AnnotationDescriptor annotation = getJsNameAnnotation(descriptor);
        if (annotation == null || annotation.getAllValueArguments().isEmpty()) return null;

        ConstantValue<?> value = annotation.getAllValueArguments().values().iterator().next();
        if (value == null) return null;

        Object result = value.getValue();
        if (!(result instanceof String)) return null;

        return (String) result;
    }

    @Nullable
    public static AnnotationDescriptor getJsNameAnnotation(@NotNull DeclarationDescriptor descriptor) {
        return getAnnotationByName(descriptor, new FqName(JS_NAME));
    }

    public static boolean isPredefinedObject(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof MemberDescriptor && ((MemberDescriptor) descriptor).isPlatform()) return true;
        if (isEffectivelyExternal(descriptor)) return true;

        for (PredefinedAnnotation annotation : PredefinedAnnotation.values()) {
            if (hasAnnotationOrInsideAnnotatedClass(descriptor, annotation)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnnotationOrInsideAnnotatedClass(
            @NotNull DeclarationDescriptor descriptor,
            @NotNull PredefinedAnnotation annotation
    ) {
        return hasAnnotationOrInsideAnnotatedClass(descriptor, annotation.getFqName());
    }

    private static boolean hasAnnotationOrInsideAnnotatedClass(@NotNull DeclarationDescriptor descriptor, @NotNull FqName fqName) {
        if (getAnnotationByName(descriptor, fqName) != null) {
            return true;
        }
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        return containingClass != null && hasAnnotationOrInsideAnnotatedClass(containingClass, fqName);
    }

    public static boolean hasJsNameInAccessors(@NotNull PropertyDescriptor property) {
        for (PropertyAccessorDescriptor accessor : property.getAccessors()) {
            if (getJsName(accessor) != null) return true;
        }
        return false;
    }
}
