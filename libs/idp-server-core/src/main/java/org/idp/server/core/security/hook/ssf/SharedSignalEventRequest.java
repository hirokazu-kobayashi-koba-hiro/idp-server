package org.idp.server.core.security.hook.ssf;

import java.util.Map;

public class SharedSignalEventRequest {
  String endpoint;
  Map<String, String> headers;
  SecurityEventToken securityEventToken;

  public SharedSignalEventRequest() {}

  public SharedSignalEventRequest(
      String endpoint, Map<String, String> headers, SecurityEventToken securityEventToken) {
    this.endpoint = endpoint;
    this.headers = headers;
    this.securityEventToken = securityEventToken;
  }

  public String endpoint() {
    return endpoint;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public String securityEventTokenValue() {
    return securityEventToken.value();
  }
}
