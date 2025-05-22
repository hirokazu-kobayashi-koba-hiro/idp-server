/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms;

public enum SmsAuthenticationExecutionStatus {
  OK(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  SmsAuthenticationExecutionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }

  public boolean isClientError() {
    return this == CLIENT_ERROR;
  }

  public boolean isServerError() {
    return this == SERVER_ERROR;
  }

  public int code() {
    return statusCode;
  }
}
