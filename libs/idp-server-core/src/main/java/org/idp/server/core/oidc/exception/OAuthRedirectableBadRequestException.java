/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.exception;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.OAuthRequestContext;

public class OAuthRedirectableBadRequestException extends RuntimeException {

  String error;
  String errorDescription;
  OAuthRequestContext oAuthRequestContext;

  public OAuthRedirectableBadRequestException(String message) {
    super(message);
  }

  public OAuthRedirectableBadRequestException(
      String error, String message, OAuthRequestContext oAuthRequestContext) {
    super(message);
    this.error = error;
    this.errorDescription = message;
    this.oAuthRequestContext = oAuthRequestContext;
  }

  public OAuthRequestContext oAuthRequestContext() {
    return oAuthRequestContext;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
