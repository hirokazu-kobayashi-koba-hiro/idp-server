package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.federation.FederationInteractionResult;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationResultConditions;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthenticationTransaction {
  AuthorizationIdentifier identifier;
  AuthenticationRequest request;
  AuthenticationPolicy authenticationPolicy;
  AuthenticationInteractionResults interactionResults;

  public static AuthenticationTransaction createOnOAuthFlow(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthorizationIdentifier identifier =
        new AuthorizationIdentifier(requestResponse.authorizationRequestIdentifier());
    AuthenticationRequest authenticationRequest = toAuthenticationRequest(tenant, requestResponse);
    AuthenticationPolicy authenticationPolicy = requestResponse.findSatisfiedAuthenticationPolicy();
    return new AuthenticationTransaction(identifier, authenticationRequest, authenticationPolicy);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthorizationRequest authorizationRequest = requestResponse.authorizationRequest();
    AuthorizationFlow authorizationFlow = AuthorizationFlow.OAUTH;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    User user = User.notFound();
    AuthenticationContext context =
        new AuthenticationContext(authorizationRequest.acrValues(), authorizationRequest.scopes());
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt =
        createdAt.plusSeconds(requestResponse.oauthAuthorizationRequestExpiresIn());
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        context,
        createdAt,
        expiredAt);
  }

  public AuthenticationTransaction updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    Map<String, AuthenticationInteractionResult> resultMap = interactionResults.toMap();

    AuthenticationRequest updatedRequest =
        interactionRequestResult.isIdentifyUserEventType()
            ? request.updateWithUser(interactionRequestResult)
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

    AuthenticationInteractionResults updatedResults =
        new AuthenticationInteractionResults(resultMap);
    return new AuthenticationTransaction(
        identifier, updatedRequest, authenticationPolicy, updatedResults);
  }

  public AuthenticationTransaction updateWith(FederationInteractionResult result) {
    Map<String, AuthenticationInteractionResult> resultMap = interactionResults.toMap();
    AuthenticationRequest updatedRequest = request.updateWithUser(result);

    if (interactionResults.contains(result.interactionTypeName())) {

      AuthenticationInteractionResult foundResult =
          interactionResults.get(result.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction = foundResult.updateWith(result);
      resultMap.remove(result.interactionTypeName());
      resultMap.put(result.interactionTypeName(), updatedInteraction);

    } else {

      int successCount = result.isSuccess() ? 1 : 0;
      int failureCount = result.isSuccess() ? 0 : 1;
      AuthenticationInteractionResult authenticationInteractionResult =
          new AuthenticationInteractionResult(1, successCount, failureCount);
      resultMap.put(result.interactionTypeName(), authenticationInteractionResult);
    }

    AuthenticationInteractionResults updatedResults =
        new AuthenticationInteractionResults(resultMap);
    return new AuthenticationTransaction(
        identifier, updatedRequest, authenticationPolicy, updatedResults);
  }

  public AuthenticationTransaction() {}

  public AuthenticationTransaction(
      AuthorizationIdentifier identifier,
      AuthenticationRequest request,
      AuthenticationPolicy authenticationPolicy) {
    this(identifier, request, authenticationPolicy, new AuthenticationInteractionResults());
  }

  public AuthenticationTransaction(
      AuthorizationIdentifier identifier,
      AuthenticationRequest request,
      AuthenticationPolicy authenticationPolicy,
      AuthenticationInteractionResults interactionResults) {
    this.identifier = identifier;
    this.request = request;
    this.authenticationPolicy = authenticationPolicy;
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

  public boolean hasAuthenticationPolicy() {
    return authenticationPolicy != null && authenticationPolicy.exists();
  }

  public AuthenticationPolicy authenticationPolicy() {
    return authenticationPolicy;
  }

  public boolean isSuccess() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditions authenticationResultConditions =
          authenticationPolicy.successConditions();
      return MfaConditionEvaluator.isSuccessSatisfied(
          authenticationResultConditions, interactionResults);
    }
    return interactionResults.containsAnySuccess();
  }

  public boolean isFailure() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditions authenticationResultConditions =
          authenticationPolicy.failureConditions();
      return MfaConditionEvaluator.isFailureSatisfied(
          authenticationResultConditions, interactionResults);
    }
    return interactionResults.containsDenyInteraction();
  }

  public boolean isLocked() {
    if (hasAuthenticationPolicy()) {
      AuthenticationResultConditions authenticationResultConditions =
          authenticationPolicy.lockConditions();
      return MfaConditionEvaluator.isLockedSatisfied(
          authenticationResultConditions, interactionResults);
    }
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

  public AuthenticationContext requestContext() {
    return request.context();
  }

  // TODO
  public Authentication authentication() {
    LocalDateTime time = SystemDateTime.now();
    List<String> methods = new ArrayList<>();
    List<String> acrValues = new ArrayList<>();
    return new Authentication().setTime(time);
  }
}
