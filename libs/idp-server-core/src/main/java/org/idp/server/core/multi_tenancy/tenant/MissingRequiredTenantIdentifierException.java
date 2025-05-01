package org.idp.server.core.multi_tenancy.tenant;

public class MissingRequiredTenantIdentifierException extends RuntimeException {

  public MissingRequiredTenantIdentifierException(String message) {
    super(message);
  }
}
