package com.coveo.feign.util;

public class ClassUtils {
  private ClassUtils() {}

  public static boolean isSpringFrameworkAvailable() {
    return isClassAvailable(
        "org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider");
  }

  public static boolean isSpringWebAvailable() {
    return isClassAvailable("org.springframework.web.bind.annotation.RequestMapping");
  }

  public static boolean isClassAvailable(String fullyQualifiedName) {
    try {
      Class.forName(fullyQualifiedName);
      return true;
    } catch (Throwable e) {
    }
    return false;
  }
}
