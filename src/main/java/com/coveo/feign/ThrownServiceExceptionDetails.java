package com.coveo.feign;

import java.lang.reflect.InvocationTargetException;

public class ThrownServiceExceptionDetails {
	private Class<? extends ServiceException> clazz;

	private ServiceExceptionSupplier serviceExceptionSupplier;

	public Class<? extends ServiceException> getClazz() {
		return clazz;
	}

	public void setClazz(Class<? extends ServiceException> clazz) {
		this.clazz = clazz;
	}

	public ServiceExceptionSupplier getServiceExceptionSupplier() {
		return serviceExceptionSupplier;
	}

	public void setServiceExceptionSupplier(ServiceExceptionSupplier serviceExceptionSupplier) {
		this.serviceExceptionSupplier = serviceExceptionSupplier;
	}

	public ThrownServiceExceptionDetails withClazz(Class<? extends ServiceException> clazz) {
		setClazz(clazz);
		return this;
	}

	public ThrownServiceExceptionDetails withServiceExceptionSupplier(ServiceExceptionSupplier supplier) {
		setServiceExceptionSupplier(supplier);
		return this;
	}

	public ServiceException instantiate()
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return serviceExceptionSupplier.get();
	}
}
