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

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.basic.type.AuthFlow;
import org.idp.server.basic.type.ciba.BindingMessage;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetail;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(map.get("id"));
    AuthFlow authFlow = AuthFlow.of(map.get("flow"));
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(map.get("authorization_id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    User user = toUser(map);
    AuthenticationContext context = toAuthenticationContext(map);
    AuthenticationPolicy authenticationPolicy =
        jsonConverter.read(map.get("authentication_policy"), AuthenticationPolicy.class);
    LocalDateTime createdAt = LocalDateTime.parse(map.get("created_at"));
    LocalDateTime expiredAt = LocalDateTime.parse(map.get("expired_at"));
    AuthenticationRequest request =
        new AuthenticationRequest(
            authFlow, tenantIdentifier, requestedClientId, user, context, createdAt, expiredAt);

    AuthenticationInteractionResults interactionResults = toAuthenticationInteractionResults(map);
    AuthenticationTransactionAttributes attributes = toAuthenticationTransactionAttributes(map);

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        request,
        authenticationPolicy,
        interactionResults,
        attributes);
  }

  private static AuthenticationContext toAuthenticationContext(Map<String, String> map) {
    if (map.containsKey("context") && map.get("context") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("context"));
      String acrValues = jsonNodeWrapper.getValueOrEmptyAsString("acr_values");
      String scopes = jsonNodeWrapper.getValueOrEmptyAsString("scopes");
      String bindingMessage = jsonNodeWrapper.getValueOrEmptyAsString("binding_message");
      Object authorizationDetails = jsonNodeWrapper.getValue("authorization_details");
      JsonNodeWrapper detailsNode = JsonNodeWrapper.fromObject(authorizationDetails);
      List<Map<String, Object>> listAsMap = detailsNode.toListAsMap();
      List<AuthorizationDetail> authorizationDetailsList =
          listAsMap.stream().map(AuthorizationDetail::new).toList();

      return new AuthenticationContext(
          new AcrValues(acrValues),
          new Scopes(scopes),
          new BindingMessage(bindingMessage),
          new AuthorizationDetails(authorizationDetailsList));
    }

    return new AuthenticationContext();
  }

  static User toUser(Map<String, String> map) {
    if (map.containsKey("user_payload") && map.get("user_payload") != null) {
      return jsonConverter.read(map.get("user_payload"), User.class);
    }
    return User.notFound();
  }

  static AuthenticationInteractionResults toAuthenticationInteractionResults(
      Map<String, String> map) {
    if (map.containsKey("interactions") && map.get("interactions") != null) {

      HashMap<String, AuthenticationInteractionResult> results = new HashMap<>();
      JsonNodeWrapper interactions = JsonNodeWrapper.fromString(map.get("interactions"));

      for (Iterator<String> it = interactions.fieldNames(); it.hasNext(); ) {
        String interaction = it.next();
        JsonNodeWrapper node = interactions.getValueAsJsonNode(interaction);
        int callCount = node.getValueAsInt("call_count");
        int successCount = node.getValueAsInt("success_count");
        int failureCount = node.getValueAsInt("failure_count");
        AuthenticationInteractionResult authenticationInteractionResult =
            new AuthenticationInteractionResult(callCount, successCount, failureCount);
        results.put(interaction, authenticationInteractionResult);
      }

      return new AuthenticationInteractionResults(results);
    }

    return new AuthenticationInteractionResults();
  }

  static AuthenticationTransactionAttributes toAuthenticationTransactionAttributes(
      Map<String, String> map) {
    if (map.containsKey("attributes") && map.get("attributes") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("attributes"));
      Map<String, Object> attributesMap = jsonNodeWrapper.toMap();
      return new AuthenticationTransactionAttributes(attributesMap);
    }

    return new AuthenticationTransactionAttributes();
  }
}
