package com.company.platform.core.utils;

import java.lang.reflect.InvocationTargetException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GenericUtils {

  public static <T> T newInstance(Class<T> c) {
    return newInstance(c, null, null);
  }

  public static <T> T newInstance(Class<T> c, Class<?>[] parameterTypes, Object[] parameterValues) {
    if (c == null) {
      throw new IllegalArgumentException("class cannot be null");
    }
    try {
      if (parameterTypes == null || parameterTypes.length == 0) {
        return emptyConstructor(c);
      }
      return c.getDeclaredConstructor(parameterTypes).newInstance(parameterValues);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Could not find a matching constructor for " + c.getName(), e);
    } catch (ReflectiveOperationException | RuntimeException e) {
      throw new IllegalArgumentException("Could not instantiate class " + c.getName(), e);
    }
  }

  private static <T> T emptyConstructor(Class<T> c)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    return c.getDeclaredConstructor().newInstance();
  }
}
