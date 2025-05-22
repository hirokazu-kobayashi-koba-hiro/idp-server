/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.rar;

public class AuthorizationDetailsInvalidException extends RuntimeException {

  String error;
  String errorDescription;

  public AuthorizationDetailsInvalidException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public AuthorizationDetailsInvalidException(
      String error, String errorDescription, Throwable throwable) {
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
