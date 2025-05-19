package org.idp.server.core.oidc.exception;

import org.idp.server.basic.exception.NotFoundException;

public class OAuthRequestNotFoundException extends NotFoundException {

  String error;
  String errorDescription;

  public OAuthRequestNotFoundException(String error, String errorDescription) {
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
