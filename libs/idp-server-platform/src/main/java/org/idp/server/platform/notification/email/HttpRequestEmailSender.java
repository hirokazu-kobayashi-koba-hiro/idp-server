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

package org.idp.server.platform.notification.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;

public class HttpRequestEmailSender implements EmailSender {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestEmailSender() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "http_request";
  }

  @Override
  public EmailSendResult send(EmailSendingRequest request, EmailSenderConfiguration configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());

    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration.httpRequest(), httpRequestBaseParams);

    if (executionResult.isClientError()) {
      return new EmailSendResult(false, Map.of("http_request", executionResult.toMap()));
    }

    if (executionResult.isServerError()) {
      return new EmailSendResult(false, Map.of("http_request", executionResult.toMap()));
    }

    return new EmailSendResult(true, Map.of("http_request", executionResult.toMap()));
  }
}
