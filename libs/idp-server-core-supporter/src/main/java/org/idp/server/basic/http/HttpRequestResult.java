/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;

public class HttpRequestResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  public HttpRequestResult() {}

  public HttpRequestResult(
      int statusCode, Map<String, List<String>> headers, JsonNodeWrapper body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isSuccess() {
    return statusCode < 400;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public JsonNodeWrapper body() {
    return body;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("status_code", statusCode);
    map.put("headers", headers);
    map.put("body", body.toMap());
    return map;
  }

  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean isServerError() {
    return statusCode >= 500;
  }
}
