package org.idp.server.core.oidc.rar;

public class AuthorizationDetailsInvalidException extends RuntimeException {

  String error;
  String errorDescription;

  public AuthorizationDetailsInvalidException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public AuthorizationDetailsInvalidException(String error, String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }
}
