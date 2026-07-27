package com.company.platform.core.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CollectionUtils {

  public static <T> Set<T> findDuplicateElements(final T[] array) {
    if (array == null) {
      return new HashSet<>();
    }
    return findDuplicateElements(Arrays.asList(array));
  }

  @SuppressWarnings("unchecked")
  public static <T> Set<T> findDuplicateElements(final Collection<T> collection) {
    if (collection == null) {
      return new HashSet<>();
    }
    var uniqueValues = new HashSet<T>();
    return collection.stream()
        .filter(Objects::nonNull)
        .map(s -> {
          if (s instanceof String) {
            return (T) s.toString().strip();
          }
          return s;
        }).filter(e -> {
          if (e instanceof String s) {
            return !uniqueValues.add((T) s.toLowerCase());
          }
          return !uniqueValues.add(e);
        }).collect(Collectors.toSet());
  }

  public static boolean isEmpty(final Object[] objects) {
    return objects == null
        || objects.length == 0
        || Stream.of(objects).noneMatch(Objects::nonNull);
  }

  public static <T> boolean isEmpty(final Collection<T> collection) {
    return collection == null || collection.isEmpty();
  }

  public static <T> boolean isNotEmpty(final Collection<T> collection) {
    return !isEmpty(collection);
  }

  public static <T> Set<T> findDifferentElements(final Collection<T> collection,
      final Collection<T> inputCollection) {
    if (isEmpty(collection)) {
      return Collections.emptySet();
    }

    if (isEmpty(inputCollection)) {
      return new HashSet<>(collection);
    }

    return collection.stream()
        .filter(element -> !inputCollection.contains(element))
        .collect(Collectors.toSet());
  }

  public static <T> String toString(final Collection<T> collection) {
    if (isEmpty(collection)) {
      return "[]";
    }
    return collection.stream()
        .map(Object::toString)
        .collect(Collectors.joining(",", "[", "]"));
  }

  public static <K, V> List<V> getValuesByKeys(
      Collection<K> keys,
      @NonNull Map<K, V> map
  ) {
    if (org.apache.commons.collections4.CollectionUtils.isEmpty(keys)) {
      return Collections.emptyList();
    }

    return keys.stream()
        .map(map::get)
        .filter(Objects::nonNull)
        .toList();
  }
}
