package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.RequestedClientId;

public class AuthenticationTransaction {
  AuthorizationIdentifier identifier;
  AuthenticationRequest request;
  AuthenticationInteractionType lastInteractionType;
  AuthenticationInteractionResults interactionResults;

  public static AuthenticationTransaction createOnOAuthFlow(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthorizationIdentifier identifier =
        new AuthorizationIdentifier(requestResponse.authorizationRequestIdentifier());
    AuthenticationRequest authenticationRequest = toAuthenticationRequest(tenant, requestResponse);
    return new AuthenticationTransaction(identifier, authenticationRequest);
  }

  public static AuthenticationTransaction createOnCibaFlow(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {
    AuthorizationIdentifier identifier =
        new AuthorizationIdentifier(cibaIssueResponse.backchannelAuthenticationRequestIdentifier());
    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, cibaIssueResponse);
    return new AuthenticationTransaction(identifier, authenticationRequest);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthorizationRequest authorizationRequest = requestResponse.authorizationRequest();
    AuthorizationFlow authorizationFlow = AuthorizationFlow.OAUTH;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    User user = User.notFound();
    List<String> availableAuthenticationTypes = requestResponse.availableAuthenticationTypes();
    List<String> requiredAnyOfAuthenticationTypes =
        requestResponse.requiredAnyOfAuthenticationTypes();
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt =
        createdAt.plusSeconds(requestResponse.oauthAuthorizationRequestExpiresIn());
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        availableAuthenticationTypes,
        requiredAnyOfAuthenticationTypes,
        createdAt,
        expiredAt);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest = cibaIssueResponse.request();
    ExpiresIn expiresIn = cibaIssueResponse.expiresIn();
    AuthorizationFlow authorizationFlow = AuthorizationFlow.CIBA;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = backchannelAuthenticationRequest.requestedClientId();
    User user = cibaIssueResponse.user();
    List<String> availableAuthenticationTypes = cibaIssueResponse.availableAuthenticationTypes();
    List<String> requiredAnyOfAuthenticationTypes =
        cibaIssueResponse.requiredAnyOfAuthenticationTypes();
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn.value());
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        availableAuthenticationTypes,
        requiredAnyOfAuthenticationTypes,
        createdAt,
        expiredAt);
  }

  public AuthenticationTransaction update(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    Set<AuthenticationInteractionResult> set = interactionResults.toSet();

    AuthenticationRequest updatedRequest =
        interactionRequestResult.isIdentifyUserEventType()
            ? request.updateUser(interactionRequestResult)
            : request;
    if (interactionResults.contains(interactionRequestResult.interactionTypeName())) {

      AuthenticationInteractionResult foundResult =
          interactionResults.get(interactionRequestResult.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction =
          foundResult.update(interactionRequestResult);
      set.remove(foundResult);
      set.add(updatedInteraction);

    } else {

      int successCount = interactionRequestResult.isSuccess() ? 1 : 0;
      int failureCount = interactionRequestResult.isSuccess() ? 0 : 1;
      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(
              interactionRequestResult.interactionTypeName(), 1, successCount, failureCount);
      set.add(result);
    }

    AuthenticationInteractionType lastInteractionType = interactionRequestResult.type();
    AuthenticationInteractionResults updatedResults = new AuthenticationInteractionResults(set);
    return new AuthenticationTransaction(
        identifier, updatedRequest, lastInteractionType, updatedResults);
  }

  public AuthenticationTransaction() {}

  AuthenticationTransaction(AuthorizationIdentifier identifier, AuthenticationRequest request) {
    this(
        identifier,
        request,
        new AuthenticationInteractionType(),
        new AuthenticationInteractionResults());
  }

  public AuthenticationTransaction(
      AuthorizationIdentifier identifier,
      AuthenticationRequest request,
      AuthenticationInteractionType lastInteractionType,
      AuthenticationInteractionResults interactionResults) {
    this.identifier = identifier;
    this.request = request;
    this.lastInteractionType = lastInteractionType;
    this.interactionResults = interactionResults;
  }

  public AuthorizationIdentifier identifier() {
    return identifier;
  }

  public AuthenticationRequest request() {
    return request;
  }

  public User user() {
    if (request.hasUser()) {
      return request.user();
    }
    return User.notFound();
  }

  public AuthenticationInteractionResults interactionResults() {
    return interactionResults;
  }

  public Set<AuthenticationInteractionResult> interactionResultsAsSet() {
    return interactionResults.toSet();
  }

  public AuthenticationInteractionType lastInteractionType() {
    return lastInteractionType;
  }

  public AuthenticationInteractionResult lastInteraction() {
    return interactionResults.get(lastInteractionType.name());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.putAll(request.toMap());
    return map;
  }

  public boolean isComplete() {
    if (request.requiredAnyOfAuthenticationTypes().isEmpty()) {
      return interactionResults.containsAnySuccess();
    }

    return request.requiredAnyOfAuthenticationTypes().stream()
        .anyMatch(required -> interactionResults.contains(required));
  }

  // TODO implement. this is debug code
  public boolean isDeny() {

    return false;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public boolean hasInteractions() {
    return interactionResults != null && interactionResults.exists();
  }
}
