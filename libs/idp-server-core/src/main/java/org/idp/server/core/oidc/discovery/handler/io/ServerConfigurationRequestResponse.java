/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.discovery.handler.io;

import java.util.Map;

public class ServerConfigurationRequestResponse {

  ServerConfigurationRequestStatus status;
  Map<String, Object> content;

  public ServerConfigurationRequestResponse(
      ServerConfigurationRequestStatus status, Map<String, Object> content) {
    this.status = status;
    this.content = content;
  }

  public ServerConfigurationRequestStatus status() {
    return status;
  }

  public Map<String, Object> content() {
    return content;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
