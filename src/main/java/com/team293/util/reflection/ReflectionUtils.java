package com.team293.util.reflection;

import org.reflections.Reflections;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ReflectionUtils {

    /**
     * Finds all classes in the given package that implement/extend the specified interface/class.
     *
     * @param interfaceClass the interface or superclass
     * @param packageName    the root package to scan
     * @param <T>            the type of the interface
     * @return list of classes implementing that interface
     */
    public static <T> List<Class<? extends T>> getAllClassesImplementingInterface(Class<T> interfaceClass, String packageName) {
        Reflections reflections = new Reflections(packageName);

        // getSubTypesOf finds all classes that are assignable to interfaceClass
        Set<Class<? extends T>> subTypes = reflections.getSubTypesOf(interfaceClass);

        // filter out abstract classes or interfaces if you only want concretes
        return subTypes.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> (c.getModifiers() & java.lang.reflect.Modifier.ABSTRACT) == 0)
                .collect(Collectors.toList());
    }
}
