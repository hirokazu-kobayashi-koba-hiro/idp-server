/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authentication.interactors.sms.external;

import java.util.Map;
import org.idp.server.platform.http.HttpRequestResult;

public class ExternalSmsAuthenticationHttpRequestResult {

  HttpRequestResult executionResult;

  public ExternalSmsAuthenticationHttpRequestResult(HttpRequestResult executionResult) {
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
