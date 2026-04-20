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

package org.idp.server.core.openid.oauth.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OAuthAuthenticationStatusResponse {

  OAuthAuthenticationStatusStatus status;
  String authenticationStatus;
  Map<String, Object> interactionResults;
  List<String> authenticationMethods;

  public OAuthAuthenticationStatusResponse(
      OAuthAuthenticationStatusStatus status,
      String authenticationStatus,
      Map<String, Object> interactionResults,
      List<String> authenticationMethods) {
    this.status = status;
    this.authenticationStatus = authenticationStatus;
    this.interactionResults = interactionResults;
    this.authenticationMethods = authenticationMethods;
  }

  public OAuthAuthenticationStatusStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("status", authenticationStatus);
    contents.put("interaction_results", interactionResults);
    contents.put("authentication_methods", authenticationMethods);
    return contents;
  }
}
