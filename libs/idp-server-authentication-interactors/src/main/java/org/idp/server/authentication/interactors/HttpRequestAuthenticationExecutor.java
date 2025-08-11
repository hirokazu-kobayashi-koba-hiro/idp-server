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

package org.idp.server.authentication.interactors;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class HttpRequestAuthenticationExecutor implements AuthenticationExecutor {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestAuthenticationExecutor() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "http_request";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    param.put("request_attributes", requestAttributes);
    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration.httpRequest(), httpRequestBaseParams);

    if (executionResult.isClientError()) {
      return AuthenticationExecutionResult.clientError(executionResult.body().toMap());
    }

    if (executionResult.isServerError()) {
      return AuthenticationExecutionResult.serverError(executionResult.body().toMap());
    }

    return AuthenticationExecutionResult.success(executionResult.body().toMap());
  }
}
