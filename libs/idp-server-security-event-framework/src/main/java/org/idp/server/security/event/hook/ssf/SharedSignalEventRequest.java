/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.security.event.hook.ssf;

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
