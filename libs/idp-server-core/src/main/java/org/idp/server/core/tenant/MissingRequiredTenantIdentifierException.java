package org.idp.server.core.tenant;

public class MissingRequiredTenantIdentifierException extends RuntimeException {

  public MissingRequiredTenantIdentifierException(String message) {
    super(message);
  }
}
