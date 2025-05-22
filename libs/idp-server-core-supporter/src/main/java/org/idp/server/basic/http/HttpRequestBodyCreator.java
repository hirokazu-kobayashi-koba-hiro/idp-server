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
import java.util.Map;

public class HttpRequestBodyCreator {

  HttpRequestBaseParams baseParams;
  HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys;
  HttpRequestStaticBody httpRequestStaticBody;

  public HttpRequestBodyCreator(
      HttpRequestBaseParams baseParams,
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys,
      HttpRequestStaticBody httpRequestStaticBody) {
    this.baseParams = baseParams;
    this.httpRequestDynamicBodyKeys = httpRequestDynamicBodyKeys;
    this.httpRequestStaticBody = httpRequestStaticBody;
  }

  public Map<String, Object> create() {
    Map<String, Object> body = new HashMap<>();

    if (httpRequestStaticBody.exists()) {
      body.putAll(httpRequestStaticBody.toMap());
    }

    if (httpRequestDynamicBodyKeys.shouldIncludeAll()) {

      body.putAll(baseParams.toMap());
    } else {
      for (String key : httpRequestDynamicBodyKeys) {
        Object value = baseParams.getValue(key);
        body.put(key, value);
      }
    }

    return body;
  }
}
