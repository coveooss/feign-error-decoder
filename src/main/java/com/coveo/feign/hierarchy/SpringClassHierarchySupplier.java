package com.coveo.feign.hierarchy;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class SpringClassHierarchySupplier implements ClassHierarchySupplier {
  @Override
  public Set<Class<?>> getSubClasses(Class<?> clazz, String basePackage) {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(clazz));

    Set<BeanDefinition> components = provider.findCandidateComponents(basePackage);

    return components
        .stream()
        .map(
            component -> {
              try {
                return Class.forName(component.getBeanClassName());
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                    String.format("Could not load child class '%s'.", component.getBeanClassName()),
                    e);
              }
            })
        .collect(Collectors.toSet());
  }
}
