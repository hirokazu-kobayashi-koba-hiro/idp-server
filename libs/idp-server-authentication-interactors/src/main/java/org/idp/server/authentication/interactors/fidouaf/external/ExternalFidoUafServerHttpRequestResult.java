/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.Map;
import org.idp.server.basic.http.HttpRequestResult;

public class ExternalFidoUafServerHttpRequestResult {

  HttpRequestResult executionResult;

  public ExternalFidoUafServerHttpRequestResult(HttpRequestResult executionResult) {
    this.executionResult = executionResult;
  }

  public int statusCode() {
    return executionResult.statusCode();
  }

  public boolean isSuccess() {
    return executionResult.isSuccess();
  }

  public boolean isClientError() {
    return executionResult.isClientError();
  }

  public boolean isServerError() {
    return executionResult.isServerError();
  }

  public Map<String, Object> responseBody() {
    return executionResult.body().toMap();
  }
}
