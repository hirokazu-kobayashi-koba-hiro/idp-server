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

package org.idp.server.core.openid.oauth.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OAuthViewData {
  String clientId;
  String clientName;
  String clientUri;
  String logoUri;
  String contacts;
  String tosUri;
  String policyUri;
  List<String> scopes;
  boolean sessionEnabled;
  List<Map<String, Object>> availableFederations;
  Map<String, String> customParams;
  Map<String, Object> additionalViewData;

  public OAuthViewData(
      String clientId,
      String clientName,
      String clientUri,
      String logoUri,
      String contacts,
      String tosUri,
      String policyUri,
      List<String> scopes,
      boolean sessionEnabled,
      List<Map<String, Object>> availableFederations,
      Map<String, String> customParams,
      Map<String, Object> additionalViewData) {
    this.clientId = clientId;
    this.clientName = clientName;
    this.clientUri = clientUri;
    this.logoUri = logoUri;
    this.contacts = contacts;
    this.tosUri = tosUri;
    this.policyUri = policyUri;
    this.scopes = scopes;
    this.sessionEnabled = sessionEnabled;
    this.availableFederations = availableFederations;
    this.customParams = customParams;
    this.additionalViewData = additionalViewData;
  }

  public String clientId() {
    return clientId;
  }

  public String clientName() {
    return clientName;
  }

  public String clientUri() {
    return clientUri;
  }

  public String logoUri() {
    return logoUri;
  }

  public String contacts() {
    return contacts;
  }

  public String tosUri() {
    return tosUri;
  }

  public String policyUri() {
    return policyUri;
  }

  public List<String> scopes() {
    return scopes;
  }

  public Map<String, String> customParams() {
    return customParams;
  }

  public Map<String, Object> contents() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("client_id", clientId);
    contents.put("client_name", clientName);
    contents.put("client_uri", clientUri);
    contents.put("logo_uri", logoUri);
    contents.put("contacts", contacts);
    contents.put("tos_uri", tosUri);
    contents.put("policy_uri", policyUri);
    contents.put("scopes", scopes);
    contents.put("session_enabled", sessionEnabled);

    if (availableFederations != null && !availableFederations.isEmpty()) {
      contents.put("available_federations", availableFederations);
    }

    contents.put("custom_params", customParams);
    contents.putAll(additionalViewData);
    return contents;
  }
}
