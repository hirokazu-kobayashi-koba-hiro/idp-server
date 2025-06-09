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

package org.idp.server.core.oidc.rar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.vc.CredentialDefinition;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AuthorizationDetails implements Iterable<AuthorizationDetail> {

  List<AuthorizationDetail> values;

  public AuthorizationDetails() {
    this.values = new ArrayList<>();
  }

  public AuthorizationDetails(List<AuthorizationDetail> values) {
    this.values = values;
  }

  public static AuthorizationDetails fromString(String string) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(string);
      List<Map<String, Object>> listAsMap = jsonNodeWrapper.toListAsMap();
      List<AuthorizationDetail> authorizationDetailsList =
          listAsMap.stream().map(AuthorizationDetail::new).toList();

      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }

  public static AuthorizationDetails fromObject(Object object) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(object);
      List<Map<String, Object>> listAsMap = jsonNodeWrapper.toListAsMap();
      List<AuthorizationDetail> authorizationDetailsList =
          listAsMap.stream().map(AuthorizationDetail::new).toList();

      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }

  @Override
  public Iterator<AuthorizationDetail> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public List<AuthorizationDetail> values() {
    return values;
  }

  public List<Map<String, Object>> toMapValues() {
    return values.stream().map(AuthorizationDetail::values).toList();
  }

  public boolean hasVerifiableCredential() {
    return values.stream().anyMatch(AuthorizationDetail::isVerifiableCredential);
  }

  public List<CredentialDefinition> credentialDefinitions() {
    return values.stream().map(AuthorizationDetail::credentialDefinition).toList();
  }

  public boolean isOneshotToken() {
    return values.stream().anyMatch(AuthorizationDetail::isOneshotToken);
  }
}
