package com.coveo.feign;

import java.lang.reflect.InvocationTargetException;

@FunctionalInterface
public interface ExceptionSupplier<S> {
  S get()
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException;
}
