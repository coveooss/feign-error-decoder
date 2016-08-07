package com.coveo.feign;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import feign.RequestLine;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;

@SuppressWarnings("unchecked")
public abstract class ReflectionErrorDecoder<T, S extends Exception> implements ErrorDecoder {
  private static final Logger logger = LoggerFactory.getLogger(ReflectionErrorDecoder.class);
  private static Field detailMessageField;

  private static boolean isSpringFrameworkAvailable = isSpringFrameworkAvailable();

  private Class<?> apiClass;
  private Class<T> apiResponseClass;
  private Map<String, ThrownExceptionDetails<S>> exceptionsThrown = new HashMap<>();
  private String basePackage;
  private Decoder decoder = new JacksonDecoder();

  public ReflectionErrorDecoder(
      Class<?> apiClass, Class<T> apiResponseClass, Class<S> baseExceptionClass) {
    this(apiClass, apiResponseClass, baseExceptionClass, "");
  }

  public ReflectionErrorDecoder(
      Class<?> apiClass,
      Class<T> apiResponseClass,
      Class<S> baseExceptionClass,
      String basePackage) {
    this.apiClass = apiClass;
    this.apiResponseClass = apiResponseClass;
    this.basePackage = basePackage;

    try {
      detailMessageField = Throwable.class.getDeclaredField("detailMessage");
      detailMessageField.setAccessible(true);

      for (Method method : apiClass.getMethods()) {
        if (method.getAnnotation(RequestLine.class) != null) {
          processDeclaredThrownExceptions(method.getExceptionTypes(), baseExceptionClass);
        }
      }
    } catch (
        ClassNotFoundException | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException | NoSuchFieldException
                | SecurityException
            e) {
      throw new IllegalStateException("FeignApiExceptionErrorDecoder instantiation failed!", e);
    }
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    T apiResponse = null;
    try {
      apiResponse = (T) decoder.decode(response, apiResponseClass);
      if (apiResponse != null && exceptionsThrown.containsKey(getKeyFromResponse(apiResponse))) {
        return getExceptionByReflection(apiResponse);
      }
    } catch (IOException e) {
      // Fail silently as a new exception will be thrown in super
    } catch (
        IllegalAccessException | IllegalArgumentException | InstantiationException
                | InvocationTargetException
            e) {
      logger.error(
          "Error instantiating the exception declared thrown for the interface '{}'",
          apiClass.getName(),
          e);
    }
    return getFallbackException(methodKey, response);
  }

  private void processDeclaredThrownExceptions(
      Class<?>[] exceptionsClasses, Class<S> baseExceptionClass)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    for (Class<?> clazz : exceptionsClasses) {
      if (baseExceptionClass.isAssignableFrom(clazz)) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
          if (isSpringFrameworkAvailable) {
            extractServiceExceptionInfoFromSubClasses(clazz);
          } else {
            logger.warn(
                "Can't extract the class hierarchy from the abstract class '{}'"
                    + " without Spring Framework.",
                clazz.getName());
          }
        } else {
          extractExceptionInfo((Class<? extends S>) clazz);
        }
      } else {
        logger.info(
            "Exception '{}' declared thrown on interface '{}' doesn't inherit from '{}',"
                + " it will be skipped.",
            clazz.getName(),
            apiClass.getName(),
            baseExceptionClass.getName());
      }
    }
  }

  private S getExceptionByReflection(T apiResponse)
      throws IllegalArgumentException, IllegalAccessException, InstantiationException,
          InvocationTargetException {
    S exceptionToBeThrown = exceptionsThrown.get(getKeyFromResponse(apiResponse)).instantiate();
    detailMessageField.set(exceptionToBeThrown, getMessageFromResponse(apiResponse));
    return exceptionToBeThrown;
  }

  private void extractServiceExceptionInfoFromSubClasses(Class<?> clazz)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Set<Class<?>> subClasses = getAllSubClasses(clazz);
    for (Class<?> subClass : subClasses) {
      extractExceptionInfo((Class<? extends S>) subClass);
    }
  }

  private Set<Class<?>> getAllSubClasses(Class<?> clazz) throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(clazz));

    Set<BeanDefinition> components = provider.findCandidateComponents(basePackage);

    Set<Class<?>> subClasses = new HashSet<>();
    for (BeanDefinition component : components) {
      subClasses.add(Class.forName(component.getBeanClassName()));
    }

    return subClasses;
  }

  private void extractExceptionInfo(Class<? extends S> clazz)
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException {
    ExceptionSupplier<S> supplier = getExceptionSupplierFromExceptionClass(clazz);

    if (supplier != null) {
      String errorCode = getKeyFromException(supplier.get());
      if (errorCode == null || errorCode.isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "The exception '%s' needs to declare an error code to be rethrown. If it's a base exception, "
                    + "make it abstract.",
                clazz.getName()));
      }

      ThrownExceptionDetails<S> existingExceptionDetails =
          exceptionsThrown.put(
              errorCode,
              new ThrownExceptionDetails<S>()
                  .withClazz(clazz)
                  .withServiceExceptionSupplier(supplier));

      if (existingExceptionDetails != null && !clazz.equals(existingExceptionDetails.getClazz())) {
        throw new IllegalStateException(
            String.format(
                "Duplicate error code '%s' for exception '%s' and '%s'.",
                errorCode,
                clazz.getName(),
                existingExceptionDetails.getClazz().getName()));
      }
    } else {
      logger.warn(
          "Couldn't instantiate the exception '{}' for the interface '{}', it needs an empty or String only "
              + "or 2 Strings or 1 String and 1 Throwable or 1 Throwable *public* constructor.",
          clazz.getName(),
          apiClass.getName());
    }
  }

  protected ExceptionSupplier<S> getExceptionSupplierFromExceptionClass(Class<? extends S> clazz) {
    for (Constructor<?> constructor : clazz.getConstructors()) {
      Class<?>[] parameters = constructor.getParameterTypes();
      if (parameters.length == 0) {
        return () -> (S) constructor.newInstance();
      } else if (parameters.length == 1 && parameters[0].isAssignableFrom(String.class)) {
        return () -> (S) constructor.newInstance(new String());
      } else if (parameters.length == 2
          && parameters[0].isAssignableFrom(String.class)
          && parameters[1].isAssignableFrom(String.class)) {
        return () -> (S) constructor.newInstance(new String(), new String());
      } else if (parameters.length == 2
          && parameters[0].isAssignableFrom(String.class)
          && parameters[1].isAssignableFrom(Throwable.class)) {
        return () -> (S) constructor.newInstance(new String(), new Throwable());
      } else if (parameters.length == 1 && parameters[0].isAssignableFrom(Throwable.class)) {
        return () -> (S) constructor.newInstance(new Throwable());
      }
    }
    return null;
  }

  protected abstract Exception getFallbackException(String methodKey, Response response);

  protected abstract String getKeyFromException(S exception);

  protected abstract String getKeyFromResponse(T apiResponse);

  protected abstract String getMessageFromResponse(T apiResponse);

  protected void setDecoder(Decoder decoder) {
    this.decoder = decoder;
  }

  private static boolean isSpringFrameworkAvailable() {
    try {
      Class.forName(
          "org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider");
      return true;
    } catch (ClassNotFoundException e) {
    }
    return false;
  }
}
