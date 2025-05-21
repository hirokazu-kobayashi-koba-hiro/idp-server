package org.idp.server.platform.multi_tenancy.tenant;

public class MissingRequiredTenantIdentifierException extends RuntimeException {

  public MissingRequiredTenantIdentifierException(String message) {
    super(message);
  }
}
