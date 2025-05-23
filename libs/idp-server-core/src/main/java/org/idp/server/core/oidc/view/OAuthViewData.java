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

package org.idp.server.core.oidc.view;

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
  Map<String, String> customParams;
  Map<String, Object> contents;

  public OAuthViewData(
      String clientId,
      String clientName,
      String clientUri,
      String logoUri,
      String contacts,
      String tosUri,
      String policyUri,
      List<String> scopes,
      Map<String, String> customParams,
      Map<String, Object> contents) {
    this.clientId = clientId;
    this.clientName = clientName;
    this.clientUri = clientUri;
    this.logoUri = logoUri;
    this.contacts = contacts;
    this.tosUri = tosUri;
    this.policyUri = policyUri;
    this.scopes = scopes;
    this.customParams = customParams;
    this.contents = contents;
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
    return contents;
  }
}
