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

package org.idp.server.federation.sso.oidc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;

public class UserinfoHttpRequestsExecutor implements UserinfoExecutor {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public UserinfoHttpRequestsExecutor() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "http_requests";
  }

  @Override
  public UserinfoExecutionResult execute(
      UserinfoExecutionRequest request, OAuthExtensionUserinfoExecutionConfig configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());

    List<HttpRequestExecutionConfig> httpRequestExecutionConfigs = configuration.httpRequests();

    Map<String, Object> results = new HashMap<>();
    List<HttpRequestResult> httpRequestResults = new ArrayList<>();
    for (HttpRequestExecutionConfig httpRequestExecutionConfig : httpRequestExecutionConfigs) {

      HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);
      HttpRequestResult executionResult =
          httpRequestExecutor.execute(httpRequestExecutionConfig, httpRequestBaseParams);

      if (executionResult.isClientError()) {
        return UserinfoExecutionResult.clientError(executionResult.body().toMap());
      }

      if (executionResult.isServerError()) {
        return UserinfoExecutionResult.serverError(executionResult.body().toMap());
      }

      httpRequestResults.add(executionResult);
      param.put(
          "execution_http_requests",
          httpRequestResults.stream().map(HttpRequestResult::toMap).toList());
    }

    results.put(
        "userinfo_execution_http_requests",
        httpRequestResults.stream().map(HttpRequestResult::toMap).toList());

    return UserinfoExecutionResult.success(results);
  }
}
