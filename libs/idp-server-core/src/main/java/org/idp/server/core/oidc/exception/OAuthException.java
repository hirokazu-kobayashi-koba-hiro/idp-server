package org.idp.server.core.oidc.exception;

public class OAuthException extends RuntimeException {

  String error;
  String errorDescription;

  public OAuthException(String error, String errorDescription) {
    super(errorDescription);
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
