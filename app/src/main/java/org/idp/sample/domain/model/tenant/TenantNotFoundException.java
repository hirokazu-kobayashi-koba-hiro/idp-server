package org.idp.sample.domain.model.tenant;

public class TenantNotFoundException extends RuntimeException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
