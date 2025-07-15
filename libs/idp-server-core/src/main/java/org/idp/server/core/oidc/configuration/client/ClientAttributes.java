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

package org.idp.server.core.oidc.configuration.client;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class ClientAttributes implements JsonReadable {

  String clientId;
  String clientName;
  String clientUri;
  String logoUri;
  String contacts;
  String tosUri;
  String policyUri;

  public ClientAttributes() {}

  public ClientAttributes(
      String clientId,
      String clientName,
      String clientUri,
      String logoUri,
      String contacts,
      String tosUri,
      String policyUri) {
    this.clientId = clientId;
    this.clientName = clientName;
    this.clientUri = clientUri;
    this.logoUri = logoUri;
    this.contacts = contacts;
    this.tosUri = tosUri;
    this.policyUri = policyUri;
  }

  public ClientIdentifier identifier() {
    return new ClientIdentifier(clientId);
  }

  public ClientName clientName() {
    return new ClientName(clientName);
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

  public boolean exists() {
    return clientId != null && !clientId.isEmpty();
  }

  public String nameValue() {
    return clientName;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("client_id", clientId);
    map.put("client_name", clientName);
    map.put("client_uri", clientUri);
    map.put("logo_uri", logoUri);
    map.put("contacts", contacts);
    map.put("tos_uri", tosUri);
    map.put("policy_uri", policyUri);
    return map;
  }
}
