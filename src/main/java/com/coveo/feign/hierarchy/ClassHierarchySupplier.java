package com.coveo.feign.hierarchy;

import java.util.Set;

public interface ClassHierarchySupplier {
  Set<Class<?>> getSubClasses(Class<?> clazz, String basePackage);
}
