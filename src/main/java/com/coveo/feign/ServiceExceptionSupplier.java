package com.coveo.feign;

import java.lang.reflect.InvocationTargetException;

@FunctionalInterface
public interface ServiceExceptionSupplier {
	ServiceException get()
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException;

}