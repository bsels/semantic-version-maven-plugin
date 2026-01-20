package io.github.bsels.semantic.version.test.utils;

import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;
import org.junit.jupiter.params.converter.DefaultArgumentConverter;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArrayArgumentConverter implements ArgumentConverter {
    private static final Pattern REGEX = Pattern.compile(";");

    private static Stream<Object> getObjectStream(String string, Class<?> componentType) {
        return REGEX.splitAsStream(string)
                .map(data -> DefaultArgumentConverter.INSTANCE.convert(
                        data,
                        componentType,
                        Thread.currentThread().getContextClassLoader()
                ));
    }

    @Override
    public Object convert(Object source, ParameterContext context)
            throws ArgumentConversionException {
        if (source == null) {
            return null;
        }
        if (!(source instanceof String string)) {
            throw new ArgumentConversionException("Cannot convert a non string '%s' to array/collection".formatted(source));
        }
        Type type = context.getParameter()
                .getParameterizedType();
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();
                List<Object> list = getObjectStream(string, componentType)
                        .toList();
                Object array = Array.newInstance(componentType, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(array, i, list.get(i));
                }
                return array;
            } else {
                return DefaultArgumentConverter.INSTANCE.convert(string, context);
            }
        } else if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawType)) {
                Collector<Object, ?, ? extends Collection<Object>> collector;
                if (List.class.isAssignableFrom(rawType) || Collection.class.equals(rawType)) {
                    collector = Collectors.toList();
                } else if (Set.class.isAssignableFrom(rawType)) {
                    collector = Collectors.toSet();
                } else {
                    throw new IllegalStateException("Unsupported collection type '%s'".formatted(rawType));
                }
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length != 1) {
                    throw new IllegalStateException("Unsupported collection type '%s'".formatted(rawType));
                }
                if (typeArguments[0] instanceof Class<?> componentType) {
                    return getObjectStream(string, componentType)
                            .collect(collector);
                } else {
                    throw new IllegalStateException("Unsupported collection element type '%s'".formatted(typeArguments[0]));
                }
            }
        }
        throw new ArgumentConversionException("Cannot convert '%s' to array".formatted(source));
    }
}
