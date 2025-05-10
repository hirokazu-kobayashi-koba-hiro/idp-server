package org.idp.server.core.oidc.exception;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class OAuthAuthorizeBadRequestException extends RuntimeException {

  String error;
  String errorDescription;

  public OAuthAuthorizeBadRequestException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthAuthorizeBadRequestException(String error, String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }

}
