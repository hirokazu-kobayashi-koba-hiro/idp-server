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

package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutionRequest;
import org.idp.server.platform.http.*;
import org.idp.server.platform.type.RequestAttributes;

public class ExternalFidoUafServerHttpClient {

  HttpRequestExecutor httpRequestExecutor;

  public ExternalFidoUafServerHttpClient() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalFidoUafServerHttpRequestResult execute(
      FidoUafExecutionRequest request,
      RequestAttributes requestAttributes,
      ExternalFidoUafServerExecutionConfiguration configuration) {

    Map<String, Object> params = new HashMap<>();
    params.put("request_body", request.toMap());
    params.put("request_attributes", requestAttributes);
    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(params);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration, httpRequestBaseParams);

    return new ExternalFidoUafServerHttpRequestResult(executionResult);
  }
}
