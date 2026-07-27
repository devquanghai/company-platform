package com.company.platform.core.utils;

import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Java Stream API ultility functions
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StreamUtils {

  /**
   * @param input Input collection
   * @return Stream of input collection
   */
  public static <T> Stream<T> ofNullable(@Nullable Collection<T> input) {
    return Optional.ofNullable(input).orElse(List.of()).stream();
  }

  public static <K, V> Stream<V> valuesOfNullable(@Nullable Map<K, V> input) {
    return Optional.ofNullable(input).orElse(Map.of()).values().stream();
  }

  public static <K, V> Stream<K> keysOfNullable(@Nullable Map<K, V> input) {
    return Optional.ofNullable(input).orElse(Map.of()).keySet().stream();
  }

  /**
   * Still open new loop operation, maybe more 'overhead' while it need to init Supplier. But it
   * make code look a bit more readable.
   *
   * @param input Input collection
   * @return Supplier container of a stream
   * @see Supplier
   */
  public static <T> Supplier<Stream<T>> toSupplier(Collection<T> input) {
    return () -> ofNullable(input);
  }

  /**
   * <a href="https://stackoverflow.com/questions/23699371/java-8-distinct-by-property">REF</a>
   * */
  public static <T> Predicate<T> distinctBy(Function<? super T, ?> f) {
    Set<Object> objects = ConcurrentHashMap.newKeySet();
    return t -> objects.add(f.apply(t));
  }
}
