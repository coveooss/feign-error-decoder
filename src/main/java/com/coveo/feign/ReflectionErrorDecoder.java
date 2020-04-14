package com.coveo.feign;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;

import com.coveo.feign.hierarchy.CachedSpringClassHierarchySupplier;
import com.coveo.feign.hierarchy.ClassHierarchySupplier;
import com.coveo.feign.hierarchy.EmptyClassHierarchySupplier;
import com.coveo.feign.util.ClassUtils;
import com.coveo.feign.util.Pair;

import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;

@SuppressWarnings("unchecked")
public abstract class ReflectionErrorDecoder<T, S extends Exception> implements ErrorDecoder {
  private static final Logger logger = LoggerFactory.getLogger(ReflectionErrorDecoder.class);
  private static final List<Object> SUPPORTED_CONSTRUCTOR_ARGUMENTS =
      Arrays.asList(
          new String(),
          new Exception(
              "Not the real cause, this throwable was only used for instantiation by ReflectionErrorDecoder"),
          new Error(
              "Not the real cause, this throwable was only used for instantiation by ReflectionErrorDecoder"));

  private static Field detailMessageField;
  private static boolean isSpringWebAvailable = ClassUtils.isSpringWebAvailable();

  protected Class<?> apiClass;
  protected Class<T> apiResponseClass;
  protected ClassHierarchySupplier classHierarchySupplier;
  protected Class<S> baseExceptionClass;
  protected String basePackage;

  private Map<String, ThrownExceptionDetails<S>> exceptionsThrown = new HashMap<>();
  private Map<String, ThrownExceptionDetails<RuntimeException>> runtimeExceptionsThrown =
      new HashMap<>();

  private Decoder decoder = new JacksonDecoder();
  private ErrorDecoder fallbackErrorDecoder = new ErrorDecoder.Default();

  private boolean isMultipleFieldsEnabled = false;

  public ReflectionErrorDecoder(
      Class<?> apiClass, Class<T> apiResponseClass, Class<S> baseExceptionClass) {
    this(apiClass, apiResponseClass, baseExceptionClass, "");
  }

  public ReflectionErrorDecoder(
      Class<?> apiClass,
      Class<T> apiResponseClass,
      Class<S> baseExceptionClass,
      String basePackage) {
    this(
        apiClass,
        apiResponseClass,
        baseExceptionClass,
        basePackage,
        ClassUtils.isSpringFrameworkAvailable()
            ? new CachedSpringClassHierarchySupplier(baseExceptionClass, basePackage)
            : new EmptyClassHierarchySupplier());
  }

  public ReflectionErrorDecoder(
      Class<?> apiClass,
      Class<T> apiResponseClass,
      Class<S> baseExceptionClass,
      String basePackage,
      ClassHierarchySupplier classHierarchySupplier) {
    this.apiClass = apiClass;
    this.apiResponseClass = apiResponseClass;
    this.basePackage = basePackage;
    this.classHierarchySupplier = classHierarchySupplier;
    this.baseExceptionClass = baseExceptionClass;

    initialize();
  }

  public void setMultipleFieldsEnabled(boolean isMultipleFieldsEnabled) {
    this.isMultipleFieldsEnabled = isMultipleFieldsEnabled;
  }

  //The copied response will be closed in SynchronousMethodHandler and the actual is closed in Util.toByteArray
  @SuppressWarnings("resource")
  @Override
  public Exception decode(String methodKey, Response response) {
    Response responseCopy = response;
    if (response.body() != null) {
      try {
        byte[] bodyData = Util.toByteArray(response.body().asInputStream());
        responseCopy = responseCopy.toBuilder().body(bodyData).build();
        T apiResponse = (T) decoder.decode(responseCopy, apiResponseClass);
        if (apiResponse != null) {
          String key = getKeyFromResponse(apiResponse);
          if (exceptionsThrown.containsKey(key)) {
            return isMultipleFieldsEnabled ?
                    getExceptionByObjectMapperAndReflection(key, apiResponse, responseCopy) :
                    getExceptionByReflection(key, apiResponse);
          } else if (runtimeExceptionsThrown.containsKey(key)) {
            return getRuntimeExceptionByReflection(key, apiResponse);
          }
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
    }
    return fallbackErrorDecoder.decode(methodKey, responseCopy);
  }

  private void initialize() {
    try {
      detailMessageField = Throwable.class.getDeclaredField("detailMessage");
      detailMessageField.setAccessible(true);
      for (Method method : apiClass.getMethods()) {
        if (method.getAnnotation(RequestLine.class) != null
            || (isSpringWebAvailable && isMethodAnnotedWithAMappingAnnotation(method))) {
          processDeclaredThrownExceptions(method.getExceptionTypes());
        }
      }
    } catch (
        InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchFieldException | SecurityException
            e) {
      throw new IllegalStateException("ReflectionErrorDecoder instantiation failed!", e);
    }

    addAdditionalRuntimeExceptions(runtimeExceptionsThrown);
  }

  private void processDeclaredThrownExceptions(Class<?>[] thrownExceptionsClasses)
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException {
    for (Class<?> clazz : thrownExceptionsClasses) {
      if (baseExceptionClass.isAssignableFrom(clazz)) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
          extractExceptionInfoFromSubClasses(classHierarchySupplier, clazz);
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

  private RuntimeException getRuntimeExceptionByReflection(String exceptionKey, T apiResponse)
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException {
    RuntimeException runtimeExceptionToBeThrown =
        runtimeExceptionsThrown.get(exceptionKey).instantiate();
    detailMessageField.set(runtimeExceptionToBeThrown, getMessageFromResponse(apiResponse));
    return runtimeExceptionToBeThrown;
  }

  private S getExceptionByReflection(String exceptionKey, T apiResponse)
      throws IllegalArgumentException, IllegalAccessException, InstantiationException,
          InvocationTargetException {
    S exceptionToBeThrown = exceptionsThrown.get(exceptionKey).instantiate();
    detailMessageField.set(exceptionToBeThrown, getMessageFromResponse(apiResponse));
    return exceptionToBeThrown;
  }

  private S getExceptionByObjectMapperAndReflection(String exceptionKey, T apiResponse, Response response)
          throws IllegalArgumentException, IllegalAccessException, IOException {
    S exceptionToBeThrown = (S) decoder.decode(response, exceptionsThrown.get(exceptionKey).getClazz());
    detailMessageField.set(exceptionToBeThrown, getMessageFromResponse(apiResponse));
    return exceptionToBeThrown;
  }

  private void extractExceptionInfoFromSubClasses(
      ClassHierarchySupplier classHierarchySupplier, Class<?> clazz)
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException {
    Set<Class<?>> subClasses = classHierarchySupplier.getSubClasses(clazz, basePackage);
    for (Class<?> subClass : subClasses) {
      if (!Modifier.isAbstract(subClass.getModifiers())) {
        extractExceptionInfo((Class<? extends S>) subClass);
      }
    }
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
              new ThrownExceptionDetails<S>().withClazz(clazz).withExceptionSupplier(supplier));

      if (existingExceptionDetails != null && !clazz.equals(existingExceptionDetails.getClazz())) {
        throw new IllegalStateException(
            String.format(
                "Duplicate error code '%s' for exception '%s' and '%s'.",
                errorCode,
                clazz.getName(),
                existingExceptionDetails.getClazz().getName()));
      }
    }
  }

  private boolean isMethodAnnotedWithAMappingAnnotation(Method method) {
    return Stream.of(method.getAnnotations())
        .anyMatch(
            annotation -> {
              Class<? extends Annotation> clazz = annotation.annotationType();
              return clazz.getAnnotation(RequestMapping.class) != null
                  || clazz.equals(RequestMapping.class);
            });
  }

  protected ExceptionSupplier<S> getExceptionSupplierFromExceptionClass(Class<? extends S> clazz) {
    List<Pair<Constructor<?>, List<Object>>> potentialConstructors = new ArrayList<>();
    List<Object> supportedArguments = getSupportedConstructorArgumentInstances();
    for (Constructor<?> constructor : clazz.getConstructors()) {
      Class<?>[] parameters = constructor.getParameterTypes();
      List<Object> arguments = new ArrayList<>();
      for (Class<?> parameter : parameters) {
        supportedArguments
            .stream()
            .filter(argumentInstance -> parameter.isAssignableFrom(argumentInstance.getClass()))
            .findFirst()
            .ifPresent(argumentInstance -> arguments.add(argumentInstance));
      }
      if (arguments.size() == parameters.length) {
        potentialConstructors.add(Pair.of(constructor, arguments));
      }
    }

    if (potentialConstructors.isEmpty()) {
      logger.warn(
          "Couldn't instantiate the exception '{}' for the interface '{}'. It needs an empty or "
              + "a combination of any number of String or Throwable arguments *public* constructor.",
          clazz.getName(),
          apiClass.getName());
      return null;
    }

    //Try and get a constructor without a Throwable argument
    Pair<Constructor<?>, List<Object>> selectedConstructor =
        potentialConstructors
            .stream()
            .filter(
                pair
                    -> pair.getRight()
                        .stream()
                        .noneMatch(
                            argument -> Throwable.class.isAssignableFrom(argument.getClass())))
            .findFirst()
            .orElseGet(() -> potentialConstructors.get(0));
    return ()
        -> (S)
            selectedConstructor
                .getLeft()
                .newInstance(selectedConstructor.getRight().toArray(new Object[0]));
  }

  protected List<Object> getSupportedConstructorArgumentInstances() {
    return SUPPORTED_CONSTRUCTOR_ARGUMENTS;
  }

  protected void addAdditionalRuntimeExceptions(
      @SuppressWarnings("unused")
      Map<String, ThrownExceptionDetails<RuntimeException>> runtimeExceptionsThrown) {}

  protected abstract String getKeyFromException(S exception);

  protected abstract String getKeyFromResponse(T apiResponse);

  protected abstract String getMessageFromResponse(T apiResponse);

  protected void setDecoder(Decoder decoder) {
    this.decoder = decoder;
  }

  protected void setFallbackErrorDecoder(ErrorDecoder errorDecoder) {
    this.fallbackErrorDecoder = errorDecoder;
  }
}
