package com.coveo.feign.hierarchy;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptyClassHierarchySupplier implements ClassHierarchySupplier {
  private static final Logger logger = LoggerFactory.getLogger(EmptyClassHierarchySupplier.class);

  @Override
  public Set<Class<?>> getSubClasses(Class<?> clazz, String basePackage) {
    logger.warn(
        "Can't extract the class hierarchy from the abstract class '{}'. You need to provide a ClassHierarchySupplier.",
        clazz.getName());
    return new HashSet<>();
  }
}
