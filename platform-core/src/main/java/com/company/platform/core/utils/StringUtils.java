package com.company.platform.core.utils;

import java.lang.Character.UnicodeBlock;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

  public static boolean isBlank(final CharSequence cs) {
    return org.apache.commons.lang3.StringUtils.isBlank(cs);
  }

  public static boolean isNotBlank(final CharSequence cs) {
    return org.apache.commons.lang3.StringUtils.isNotBlank(cs);
  }

  public static boolean containsAnyNonUnicode(final String s) {
    if (isBlank(s)) {
      return false;
    }
    var characters = s.toCharArray();
    for (var c : characters) {
      if (UnicodeBlock.of(c) != UnicodeBlock.BASIC_LATIN) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsIgnoreCase(final String s1, final String s2) {
    if (s1 == null && s2 == null) {
      return true;
    }

    if (s1 == null || s2 == null) {
      return false;
    }

    return s1.strip().toLowerCase(Locale.ROOT)
        .contains(s2.strip().toLowerCase(Locale.ROOT));
  }
}
