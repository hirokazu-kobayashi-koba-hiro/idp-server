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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestMockConfig;

public class EmailSenderConfiguration {

  String function;
  String sender;
  String subject;
  String body;
  HttpRequestExecutionConfig httpRequest = new HttpRequestExecutionConfig();
  List<HttpRequestExecutionConfig> httpRequests = new ArrayList<>();
  HttpRequestMockConfig mock = new HttpRequestMockConfig();
  SmtpEmailSenderConfig smtp = new SmtpEmailSenderConfig();
  Map<String, Object> details = new HashMap<>();

  public EmailSenderConfiguration() {}

  public String function() {
    return function;
  }

  public String sender() {
    return sender;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }

  public HttpRequestExecutionConfig httpRequest() {
    if (httpRequest == null) {
      return new HttpRequestExecutionConfig();
    }
    return httpRequest;
  }

  public List<HttpRequestExecutionConfig> httpRequests() {
    if (httpRequests == null) {
      return new ArrayList<>();
    }
    return httpRequests;
  }

  public List<Map<String, Object>> httpRequestsAsMap() {
    if (httpRequests == null) {
      return new ArrayList<>();
    }
    return httpRequests.stream().map(HttpRequestExecutionConfig::toMap).toList();
  }

  public HttpRequestMockConfig mock() {
    if (mock == null) {
      return new HttpRequestMockConfig();
    }
    return mock;
  }

  public SmtpEmailSenderConfig smtp() {
    if (smtp == null) {
      return new SmtpEmailSenderConfig();
    }
    return smtp;
  }

  public Map<String, Object> details() {
    if (details == null) {
      return new HashMap<>();
    }
    return details;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("function", function);
    map.put("http_request", httpRequest().toMap());
    map.put("http_requests", httpRequestsAsMap());
    map.put("mock", mock().toMap());
    map.put("smtp", smtp().toMap());
    map.put("details", details());
    return map;
  }
}
