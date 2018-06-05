package com.coveo.feign;

import java.lang.reflect.InvocationTargetException;

public class ThrownExceptionDetails<T> {
  private Class<? extends T> clazz;
  private ExceptionSupplier<T> exceptionSupplier;

  public Class<? extends T> getClazz() {
    return clazz;
  }

  public void setClazz(Class<? extends T> clazz) {
    this.clazz = clazz;
  }

  public ExceptionSupplier<T> getServiceExceptionSupplier() {
    return exceptionSupplier;
  }

  public void setExceptionSupplier(ExceptionSupplier<T> serviceExceptionSupplier) {
    this.exceptionSupplier = serviceExceptionSupplier;
  }

  public ThrownExceptionDetails<T> withClazz(Class<? extends T> clazz) {
    setClazz(clazz);
    return this;
  }

  public ThrownExceptionDetails<T> withExceptionSupplier(ExceptionSupplier<T> supplier) {
    setExceptionSupplier(supplier);
    return this;
  }

  public T instantiate()
      throws InstantiationException, IllegalAccessException, IllegalArgumentException,
          InvocationTargetException {
    return exceptionSupplier.get();
  }
}
