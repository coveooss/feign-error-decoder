package com.coveo.feign.hierarchy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class CachedSpringClassHierarchySupplier implements ClassHierarchySupplier {
  private static Map<Class<?>, Set<Class<?>>> baseClassSubClassesCache = new HashMap<>();

  private Set<Class<?>> subClasses;

  public CachedSpringClassHierarchySupplier(Class<?> baseClass, String basePackage) {
    if (!baseClassSubClassesCache.containsKey(baseClass)) {
      ClassPathScanningCandidateComponentProvider provider =
          new ClassPathScanningCandidateComponentProvider(false);
      provider.addIncludeFilter(new AssignableTypeFilter(baseClass));

      Set<Class<?>> subClasses = new HashSet<>();
      for (BeanDefinition beanDefinition : provider.findCandidateComponents(basePackage)) {
        try {
          subClasses.add(Class.forName(beanDefinition.getBeanClassName()));
        } catch (ClassNotFoundException e) {
          throw new IllegalStateException(
              String.format("Could not load child class '%s'.", beanDefinition.getBeanClassName()),
              e);
        }
      }
      baseClassSubClassesCache.put(baseClass, subClasses);
    }
    subClasses = baseClassSubClassesCache.get(baseClass);
  }

  @Override
  public Set<Class<?>> getSubClasses(Class<?> clazz, String basePackage) {
    return subClasses
        .stream()
        .filter(subClass -> clazz.isAssignableFrom(subClass))
        .collect(Collectors.toSet());
  }
}
