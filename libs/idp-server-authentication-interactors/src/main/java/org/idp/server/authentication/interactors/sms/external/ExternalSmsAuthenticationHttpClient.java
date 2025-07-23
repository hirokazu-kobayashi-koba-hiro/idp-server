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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutionRequest;
import org.idp.server.platform.http.*;
import org.idp.server.platform.type.RequestAttributes;

public class ExternalSmsAuthenticationHttpClient {

  HttpRequestExecutor httpRequestExecutor;

  public ExternalSmsAuthenticationHttpClient() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalSmsAuthenticationHttpRequestResult execute(
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      ExternalSmsAuthenticationExecutionConfiguration configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    param.put("request_attributes", requestAttributes);
    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration, httpRequestBaseParams);

    return new ExternalSmsAuthenticationHttpRequestResult(executionResult);
  }
}
