package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.AuthorizationFlow;
import org.idp.server.core.type.oauth.RequestedClientId;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthorizationIdentifier identifier = new AuthorizationIdentifier(map.get("authorization_id"));
    AuthorizationFlow authorizationFlow = AuthorizationFlow.of(map.get("authorization_flow"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    User user = toUser(map);
    List<String> availableAuthenticationTypes =
        jsonConverter.read(map.get("available_authentication_types"), List.class);
    List<String> requiredAnyOfAuthenticationTypes = toRequiredAnyOfAuthenticationTypes(map);
    LocalDateTime createdAt = LocalDateTime.parse(map.get("created_at"));
    LocalDateTime expiredAt = LocalDateTime.parse(map.get("expired_at"));
    AuthenticationRequest request =
        new AuthenticationRequest(
            authorizationFlow,
            tenantIdentifier,
            requestedClientId,
            user,
            availableAuthenticationTypes,
            requiredAnyOfAuthenticationTypes,
            createdAt,
            expiredAt);
    AuthenticationInteractionType lastInteractionType =
        new AuthenticationInteractionType(map.get("last_interaction_type"));
    AuthenticationInteractionResults interactionResults = toAuthenticationInteractionResults(map);
    return new AuthenticationTransaction(
        identifier, request, lastInteractionType, interactionResults);
  }

  static User toUser(Map<String, String> map) {
    if (map.containsKey("user_payload") && map.get("user_payload") != null) {
      return jsonConverter.read(map.get("user_payload"), User.class);
    }
    return User.notFound();
  }

  static List<String> toRequiredAnyOfAuthenticationTypes(Map<String, String> map) {
    if (map.containsKey("required_any_of_authentication_types")
        && map.get("required_any_of_authentication_types") != null) {
      return jsonConverter.read(map.get("required_any_of_authentication_types"), List.class);
    }
    return List.of();
  }

  static AuthenticationInteractionResults toAuthenticationInteractionResults(
      Map<String, String> map) {
    if (map.containsKey("interactions") && map.get("interactions") != null) {

      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(map.get("interactions"));
      Set<AuthenticationInteractionResult> results = new HashSet<>();
      for (JsonNodeWrapper wrapper : jsonNodeWrapper.elements()) {
        String type = wrapper.getValueOrEmptyAsString("type");
        int callCount = wrapper.getValueAsInt("call_count");
        int successCount = wrapper.getValueAsInt("success_count");
        int failureCount = wrapper.getValueAsInt("failure_count");
        results.add(
            new AuthenticationInteractionResult(type, callCount, successCount, failureCount));
      }
      return new AuthenticationInteractionResults(results);
    }

    return new AuthenticationInteractionResults();
  }
}
