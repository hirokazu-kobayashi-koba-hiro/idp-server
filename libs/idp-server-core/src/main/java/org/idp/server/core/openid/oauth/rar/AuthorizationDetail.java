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

package org.idp.server.core.openid.oauth.rar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.oauth.type.vc.CredentialDefinition;

/**
 * AuthorizationDetail
 *
 * <p>Represents a single authorization detail object as defined in RFC 9396 Section 2.
 *
 * <p>RFC 9396 Section 2 - Authorization Details Structure:
 *
 * <pre>
 * type: An identifier for the authorization details type as a string.
 * This field is REQUIRED.
 *
 * locations: An array of strings representing the location of the resource.
 * actions: An array of strings representing the actions the client wants to perform.
 * datatypes: An array of strings representing the type of data requested.
 * identifier: A string identifier indicating the resource to which access is requested.
 * privileges: An array of strings representing access privileges.
 * </pre>
 *
 * <p>RFC 9396 Section 5 - Required Field Validation:
 *
 * <pre>
 * The AS MUST abort processing and respond with an error
 * invalid_authorization_details to the client if any of the following are true:
 * - is missing required fields for the authorization details type
 * </pre>
 *
 * <p>This class validates:
 *
 * <ul>
 *   <li>'type' field MUST be present and non-empty (RFC 9396 Section 2)
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-2">RFC 9396 Section 2</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-5">RFC 9396 Section 5</a>
 */
public class AuthorizationDetail {
  Map<String, Object> values;

  public AuthorizationDetail() {
    this.values = new HashMap<>();
  }

  public AuthorizationDetail(Map<String, Object> values) {
    this.values = values;
  }

  public String type() {
    return getValueOrEmpty("type");
  }

  public List<String> locations() {
    return getListOrEmpty("locations");
  }

  public List<String> actions() {
    return getListOrEmpty("actions");
  }

  public List<String> datatypes() {
    return getListOrEmpty("datatypes");
  }

  public String identifier() {
    return getValueOrEmpty("identifier");
  }

  public List<String> privileges() {
    return getListOrEmpty("privileges");
  }

  public boolean isVerifiableCredential() {
    return getValueOrEmpty("type").equals("openid_credential");
  }

  public String format() {
    return getValueOrEmpty("format");
  }

  public CredentialDefinition credentialDefinition() {
    return new CredentialDefinition(getMapOrEmpty("credential_definition"));
  }

  public String doctype() {
    return getValueOrEmpty("doctype");
  }

  public boolean isOneshotToken() {

    return getValueAsBoolean("oneshot_token");
  }

  public List<String> getListOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return List.of();
    }
    return (List<String>) value;
  }

  public String getValueOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  public boolean getValueAsBoolean(String key) {
    if (values.containsKey(key)) {
      return values.get(key).toString().equals("true");
    }
    return false;
  }

  public Map<String, Object> getMapOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return Map.of();
    }
    return (Map<String, Object>) value;
  }

  public boolean hasType() {
    return !type().isEmpty();
  }

  public Map<String, Object> values() {
    return values;
  }
}
