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

package org.idp.server.platform.notification.sms;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * SmsSender that delegates only the SMS delivery to an external API (e.g. Twilio) via {@link
 * HttpRequestExecutor}. OTP generation and verification stay inside idp-server. This is the SMS
 * counterpart of {@code HttpRequestEmailSender}.
 *
 * <p>The {@code setting} map is expected to carry the external API configuration under the {@code
 * http_request} key, which is deserialized into {@link HttpRequestExecutionConfig}.
 */
public class HttpRequestSmsSender implements SmsSender {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestSmsSender.class);

  /**
   * Factory constructor with dependency injection support. This constructor enables OAuth token
   * caching and proper DI integration.
   *
   * @param httpRequestExecutor the HTTP request executor with OAuth caching support
   */
  public HttpRequestSmsSender(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SmsSenderType type() {
    return new SmsSenderType("http_request");
  }

  @Override
  public SmsSendResult send(SmsSendingRequest request, Map<String, Object> setting) {
    try {
      HttpRequestExecutionConfig configuration =
          jsonConverter.read(setting.get("http_request"), HttpRequestExecutionConfig.class);

      Map<String, Object> param = new HashMap<>();
      param.put("request_body", request.toMap());

      HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

      HttpRequestResult executionResult =
          httpRequestExecutor.execute(configuration, httpRequestBaseParams);

      if (executionResult.isClientError()) {
        return new SmsSendResult(false, Map.of("http_request", executionResult.toMap()));
      }

      if (executionResult.isServerError()) {
        return new SmsSendResult(false, Map.of("http_request", executionResult.toMap()));
      }

      return new SmsSendResult(true, Map.of("http_request", executionResult.toMap()));
    } catch (Exception e) {

      log.error(e.getMessage(), e);
      return new SmsSendResult(
          false,
          Map.of("http_request", Map.of("error", "server_error", "error_message", e.getMessage())));
    }
  }
}
