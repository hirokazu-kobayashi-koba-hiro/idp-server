/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.http.QueryParams;

public class AuthorizationRequestBuilder {
  String authorizationEndpoint;
  String clientId;
  String redirectUri;
  String scope;
  String responseType;
  List<Map<String, Object>> authorizationDetails;
  Map<String, String> customParameters = new HashMap<>();
  QueryParams queryParams = new QueryParams();

  public AuthorizationRequestBuilder(
      String authorizationEndpoint,
      String clientId,
      String redirectUri,
      String scope,
      String responseType) {
    this.authorizationEndpoint = authorizationEndpoint;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.scope = scope;
    this.responseType = responseType;
  }

  public AuthorizationRequestBuilder addAuthorizationDetail(
      List<Map<String, Object>> authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }

  public String build() {
    return "";
  }
}
