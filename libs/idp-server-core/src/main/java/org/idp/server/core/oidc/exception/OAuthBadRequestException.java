package org.idp.server.core.oidc.exception;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

/** OAuthBadRequestException */
public class OAuthBadRequestException extends RuntimeException {

  Tenant tenant;
  String error;
  String errorDescription;

  public OAuthBadRequestException(String error, String errorDescription, Tenant tenant) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
    this.tenant = tenant;
  }

  public OAuthBadRequestException(String error, String errorDescription, Throwable throwable, Tenant tenant) {
    super(errorDescription, throwable);
    this.error = error;
    this.errorDescription = errorDescription;
    this.tenant = tenant;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }

  public Tenant tenant() {
    return tenant;
  }
}
