package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.request.AuthorizationRequest;

public class AuthenticationTransaction {
  AuthorizationIdentifier identifier;
  AuthenticationRequest request;
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

  public AuthenticationTransaction updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    Map<String, AuthenticationInteractionResult> resultMap = interactionResults.toMap();

    AuthenticationRequest updatedRequest =
        interactionRequestResult.isIdentifyUserEventType()
            ? request.updateUser(interactionRequestResult)
            : request;
    if (interactionResults.contains(interactionRequestResult.interactionTypeName())) {

      AuthenticationInteractionResult foundResult =
          interactionResults.get(interactionRequestResult.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction =
          foundResult.updateWith(interactionRequestResult);
      resultMap.remove(interactionRequestResult.interactionTypeName());
      resultMap.put(interactionRequestResult.interactionTypeName(), updatedInteraction);

    } else {

      int successCount = interactionRequestResult.isSuccess() ? 1 : 0;
      int failureCount = interactionRequestResult.isSuccess() ? 0 : 1;
      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(1, successCount, failureCount);
      resultMap.put(interactionRequestResult.interactionTypeName(), result);
    }

    AuthenticationInteractionResults updatedResults = new AuthenticationInteractionResults(resultMap);
    return new AuthenticationTransaction(
        identifier, updatedRequest, updatedResults);
  }

  public AuthenticationTransaction() {}

  AuthenticationTransaction(AuthorizationIdentifier identifier, AuthenticationRequest request) {
    this(
        identifier,
        request,
        new AuthenticationInteractionResults());
  }

  public AuthenticationTransaction(
      AuthorizationIdentifier identifier,
      AuthenticationRequest request,
      AuthenticationInteractionResults interactionResults) {
    this.identifier = identifier;
    this.request = request;
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

  public Map<String, AuthenticationInteractionResult> interactionResultsAsMap() {
    return interactionResults.toMap();
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

  public boolean hasUser() {
    return request.hasUser();
  }
}
