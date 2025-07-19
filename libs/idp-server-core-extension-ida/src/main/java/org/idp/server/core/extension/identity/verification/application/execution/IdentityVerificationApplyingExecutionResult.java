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

package org.idp.server.core.extension.identity.verification.application.execution;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;

public class IdentityVerificationApplyingExecutionResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  public IdentityVerificationApplyingExecutionResult() {}

  public IdentityVerificationApplyingExecutionResult(HttpRequestResult httpRequestResult) {
    this.statusCode = httpRequestResult.statusCode();
    this.headers = httpRequestResult.headers();
    this.body = httpRequestResult.body();
  }

  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean isServerError() {
    return statusCode >= 500;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public JsonNodeWrapper body() {
    return body;
  }

  public String extractValueFromBody(String key) {
    return body.getValueOrEmptyAsString(key);
  }
}
