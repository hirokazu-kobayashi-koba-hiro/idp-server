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
