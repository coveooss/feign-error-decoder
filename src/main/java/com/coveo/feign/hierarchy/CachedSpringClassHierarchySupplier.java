package com.coveo.feign.hierarchy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class CachedSpringClassHierarchySupplier implements ClassHierarchySupplier {
  private static final Logger logger =
      LoggerFactory.getLogger(CachedSpringClassHierarchySupplier.class);

  private static Map<Class<?>, Set<Class<?>>> baseClassSubClassesCache = new HashMap<>();

  private Set<Class<?>> subClasses;

  public CachedSpringClassHierarchySupplier(Class<?> baseClass, String basePackage) {
    if (!baseClassSubClassesCache.containsKey(baseClass)) {
      logger.debug(
          "Cache miss for the SpringClassHierarchySupplier using key '{}' and base package '{}'.",
          baseClass,
          basePackage);
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
      logger.debug("Found '{}' subClasses.", subClasses.size());
    } else {
      logger.debug("Cache hit for the SpringClassHierarchySupplier using key '{}'.", baseClass);
    }
    subClasses = baseClassSubClassesCache.get(baseClass);
  }

  @Override
  public Set<Class<?>> getSubClasses(Class<?> clazz, String basePackage) {
    return subClasses.stream().filter(clazz::isAssignableFrom).collect(Collectors.toSet());
  }
}
