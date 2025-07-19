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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestResult;

public class IdentityVerificationApplicationContext {
  Map<String, Object> requestBaseParams;
  HttpRequestResult executionResult;

  public IdentityVerificationApplicationContext() {
    this.requestBaseParams = new HashMap<>();
    this.executionResult = new HttpRequestResult();
  }

  public IdentityVerificationApplicationContext(
      Map<String, Object> requestBaseParams, HttpRequestResult executionResult) {
    this.requestBaseParams = requestBaseParams;
    this.executionResult = executionResult;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>(requestBaseParams);
    result.put("response_body", executionResult.body().toMap());
    result.put("response_header", executionResult.headers());
    return result;
  }
}
