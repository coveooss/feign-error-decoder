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
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;

@SuppressWarnings("unchecked")
public class FeignServiceExceptionErrorDecoder extends ErrorDecoder.Default {
	private static final Logger logger = LoggerFactory.getLogger(FeignServiceExceptionErrorDecoder.class);
	private static Field detailMessageField;

	private static boolean isSpringFrameworkAvailable = isSpringFrameworkAvailable();

	private Class<?> apiClass;
	private Map<String, ThrownServiceExceptionDetails> exceptionsThrown = new HashMap<>();
	private JacksonDecoder jacksonDecoder = new JacksonDecoder();

	public FeignServiceExceptionErrorDecoder(Class<?> apiClass) {
		try {
			this.apiClass = apiClass;
			detailMessageField = Throwable.class.getDeclaredField("detailMessage");
			detailMessageField.setAccessible(true);
			for (Method method : apiClass.getMethods()) {
				if (method.getAnnotation(RequestLine.class) != null) {
					for (Class<?> clazz : method.getExceptionTypes()) {
						if (ServiceException.class.isAssignableFrom(clazz)) {
							if (Modifier.isAbstract(clazz.getModifiers()) && isSpringFrameworkAvailable) {
								extractServiceExceptionInfoFromSubClasses(clazz);
							} else {
								extractServiceExceptionInfo((Class<? extends ServiceException>) clazz);
							}
						} else {
							logger.info(
									"Exception '{}' declared thrown on interface '{}' doesn't inherit from ServiceException,"
											+ " it will be skipped.",
									clazz.getName(), apiClass.getName());
						}
					}
				}
			}
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchFieldException | SecurityException e) {
			throw new IllegalStateException("FeignServiceException instantiation failed!", e);
		}
	}

	@Override
	public Exception decode(String methodKey, Response response) {
		ErrorCodeAndMessage restException = null;
		try {
			restException = (ErrorCodeAndMessage) jacksonDecoder.decode(response, ErrorCodeAndMessage.class);
			if (restException != null && exceptionsThrown.containsKey(restException.getErrorCode())) {
				return getExceptionByReflection(restException);
			}
		} catch (IOException e) {
			// Fail silently as a new exception will be thrown in super
		} catch (IllegalAccessException | IllegalArgumentException | InstantiationException
				| InvocationTargetException e) {
			logger.error("Error instantiating the exception declared thrown for the interface '{}'", apiClass.getName(),
					e);
		}
		return super.decode(methodKey, response);
	}

	private ServiceException getExceptionByReflection(ErrorCodeAndMessage restException)
			throws IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException {
		ServiceException exceptionToBeThrown = exceptionsThrown.get(restException.getErrorCode()).instantiate();
		detailMessageField.set(exceptionToBeThrown, restException.getMessage());
		return exceptionToBeThrown;
	}

	private void extractServiceExceptionInfoFromSubClasses(Class<?> clazz) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Set<Class<?>> subClasses = getAllSubClasses(clazz);
		for (Class<?> subClass : subClasses) {
			extractServiceExceptionInfo((Class<? extends ServiceException>) subClass);
		}
	}

	private Set<Class<?>> getAllSubClasses(Class<?> clazz) throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AssignableTypeFilter(clazz));

		Set<BeanDefinition> components = provider.findCandidateComponents("");

		Set<Class<?>> subClasses = new HashSet<>();
		for (BeanDefinition component : components) {
			subClasses.add(Class.forName(component.getBeanClassName()));
		}

		return subClasses;
	}

	private void extractServiceExceptionInfo(Class<? extends ServiceException> clazz)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		ServiceExceptionSupplier supplier = null;

		for (Constructor<?> constructor : clazz.getConstructors()) {
			Class<?>[] parameters = constructor.getParameterTypes();
			if (parameters.length == 0) {
				supplier = () -> (ServiceException) constructor.newInstance();
			} else if (parameters.length == 1 && parameters[0].isAssignableFrom(String.class)) {
				supplier = () -> (ServiceException) constructor.newInstance(new String());
			} else if (parameters.length == 2 && parameters[0].isAssignableFrom(String.class)
					&& parameters[1].isAssignableFrom(String.class)) {
				supplier = () -> (ServiceException) constructor.newInstance(new String(), new String());
			} else if (parameters.length == 2 && parameters[0].isAssignableFrom(String.class)
					&& parameters[1].isAssignableFrom(Throwable.class)) {
				supplier = () -> (ServiceException) constructor.newInstance(new String(), new Throwable());
			} else if (parameters.length == 1 && parameters[0].isAssignableFrom(Throwable.class)) {
				supplier = () -> (ServiceException) constructor.newInstance(new Throwable());
			}

			if (supplier != null) {
				break;
			}
		}

		if (supplier != null) {
			String errorCode = supplier.get().getErrorCode();
			if (errorCode == null || errorCode.isEmpty()) {
				throw new IllegalStateException(String
						.format("The exception '%s' needs to declare an error code to be rethrown. If it's a base exception, "
								+ "make it abstract.", clazz.getName()));
			}

			ThrownServiceExceptionDetails existingExceptionDetails = exceptionsThrown.put(errorCode,
					new ThrownServiceExceptionDetails().withClazz(clazz).withServiceExceptionSupplier(supplier));

			if (existingExceptionDetails != null && !clazz.equals(existingExceptionDetails.getClazz())) {
				throw new IllegalStateException(String.format("Duplicate error code '%s' for exception '%s' and '%s'.",
						errorCode, clazz.getName(), existingExceptionDetails.getClazz().getName()));
			}
		} else {
			logger.warn(
					"Couldn't instantiate the exception '{}' for the interface '{}', it needs an empty or String only "
							+ "or 2 Strings or 1 String and 1 Throwable or 1 Throwable *public* constructor.",
					clazz.getName(), apiClass.getName());
		}
	}

	private static boolean isSpringFrameworkAvailable() {
		boolean isSpringFrameworkAvailable = false;
		try {
			Class.forName("org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider");
			isSpringFrameworkAvailable = true;
		} catch (ClassNotFoundException e) {
		}
		return isSpringFrameworkAvailable;
	}
}
