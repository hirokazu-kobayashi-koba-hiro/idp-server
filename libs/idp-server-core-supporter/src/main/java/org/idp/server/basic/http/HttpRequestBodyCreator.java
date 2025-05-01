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
