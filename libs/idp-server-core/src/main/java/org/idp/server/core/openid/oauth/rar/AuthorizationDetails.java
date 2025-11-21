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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.vc.CredentialDefinition;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * AuthorizationDetails
 *
 * <p>Represents the authorization_details parameter as defined in RFC 9396 Section 2.
 *
 * <p>RFC 9396 Section 2 - Authorization Details Parameter:
 *
 * <pre>
 * The request parameter authorization_details contains, in JSON notation,
 * an array of objects. Each JSON object contains the data to specify the
 * authorization requirements for a certain type of resource.
 * </pre>
 *
 * <p>This class validates:
 *
 * <ul>
 *   <li>authorization_details MUST be a JSON array (RFC 9396 Section 2)
 *   <li>authorization_details array MUST NOT be empty
 *   <li>Each element MUST be a valid authorization detail object
 * </ul>
 *
 * <p>RFC 9396 Section 5 - Authorization Error Response:
 *
 * <pre>
 * The AS MUST refuse to process any unknown authorization details type or
 * authorization details not conforming to the respective type definition.
 * The AS MUST abort processing and respond with an error
 * invalid_authorization_details to the client.
 * </pre>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-2">RFC 9396 Section 2</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-5">RFC 9396 Section 5</a>
 */
public class AuthorizationDetails implements Iterable<AuthorizationDetail> {

  static final LoggerWrapper logger = LoggerWrapper.getLogger(AuthorizationDetails.class);
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
      logger.error("Failed to parse authorization_details from string", exception);
      logger.debug("authorization_details value: {}", string);
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
      logger.error("Failed to parse authorization_details from object", exception);
      logger.debug("authorization_details object: {}", object);
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
