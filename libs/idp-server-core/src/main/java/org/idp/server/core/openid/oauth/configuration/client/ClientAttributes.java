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

package org.idp.server.core.openid.oauth.configuration.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class ClientAttributes implements JsonReadable {

  String clientId;
  String clientIdAlias;
  String clientName;
  String clientUri;
  String logoUri;
  List<String> contacts;
  String tosUri;
  String policyUri;

  public ClientAttributes() {}

  public ClientAttributes(
      String clientId,
      String clientIdAlias,
      String clientName,
      String clientUri,
      String logoUri,
      List<String> contacts,
      String tosUri,
      String policyUri) {
    this.clientId = clientId;
    this.clientIdAlias = clientIdAlias;
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

  public String clientIdAlias() {
    return clientIdAlias;
  }

  public boolean hasClientIdAlias() {
    return clientIdAlias != null && !clientIdAlias.isEmpty();
  }

  public ClientName clientName() {
    return new ClientName(clientName);
  }

  public boolean hasClientName() {
    return clientName != null && !clientName.isEmpty();
  }

  public String clientUri() {
    return clientUri;
  }

  public boolean hasClientUri() {
    return clientUri != null && !clientUri.isEmpty();
  }

  public String logoUri() {
    return logoUri;
  }

  public boolean hasLogoUri() {
    return logoUri != null && !logoUri.isEmpty();
  }

  public List<String> contacts() {
    return contacts;
  }

  public boolean hasContacts() {
    return contacts != null && !contacts.isEmpty();
  }

  public String tosUri() {
    return tosUri;
  }

  public boolean hasTosUri() {
    return tosUri != null && !tosUri.isEmpty();
  }

  public String policyUri() {
    return policyUri;
  }

  public boolean hasPolicyUri() {
    return policyUri != null && !policyUri.isEmpty();
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
    if (hasClientIdAlias()) map.put("client_id_alias", clientIdAlias);
    if (hasClientName()) map.put("client_name", clientName);
    if (hasClientUri()) map.put("client_uri", clientUri);
    if (hasLogoUri()) map.put("logo_uri", logoUri);
    if (hasContacts()) map.put("contacts", contacts);
    if (hasTosUri()) map.put("tos_uri", tosUri);
    if (hasPolicyUri()) map.put("policy_uri", policyUri);
    return map;
  }
}
