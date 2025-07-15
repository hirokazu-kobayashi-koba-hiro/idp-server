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

package org.idp.server.core.oidc.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.acr.AcrResolver;
import org.idp.server.core.oidc.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.oidc.authentication.loa.LoaDeniedScopeResolver;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationResultConditions;
import org.idp.server.core.oidc.federation.FederationInteractionResult;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.type.AuthFlow;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.exception.BadRequestException;

public class AuthenticationTransaction {
  AuthenticationTransactionIdentifier identifier;
  AuthorizationIdentifier authorizationIdentifier;
  AuthenticationRequest request;
  AuthenticationPolicy authenticationPolicy;
  AuthenticationInteractionResults interactionResults;
  AuthenticationTransactionAttributes attributes;

  public AuthenticationTransaction() {}

  public AuthenticationTransaction(
      AuthenticationTransactionIdentifier identifier,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationRequest request,
      AuthenticationPolicy authenticationPolicy,
      AuthenticationTransactionAttributes attributes) {
    this(
        identifier,
        authorizationIdentifier,
        request,
        authenticationPolicy,
        new AuthenticationInteractionResults(),
        attributes);
  }

  public AuthenticationTransaction(
      AuthenticationTransactionIdentifier identifier,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationRequest request,
      AuthenticationPolicy authenticationPolicy,
      AuthenticationInteractionResults interactionResults,
      AuthenticationTransactionAttributes attributes) {
    this.identifier = identifier;
    this.authorizationIdentifier = authorizationIdentifier;
    this.request = request;
    this.authenticationPolicy = authenticationPolicy;
    this.interactionResults = interactionResults;
    this.attributes = attributes;
  }

  public AuthenticationTransaction updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    Map<String, AuthenticationInteractionResult> resultMap = interactionResults.toMap();

    AuthenticationRequest updatedRequest = updateWithUser(interactionRequestResult);

    if (interactionResults.contains(interactionRequestResult.interactionTypeName())) {

      AuthenticationInteractionResult foundResult =
          interactionResults.get(interactionRequestResult.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction =
          foundResult.updateWith(interactionRequestResult);
      resultMap.remove(interactionRequestResult.interactionTypeName());
      resultMap.put(interactionRequestResult.interactionTypeName(), updatedInteraction);

    } else {

      String operationType = interactionRequestResult.operationType().name();
      String method = interactionRequestResult.method();
      int successCount = interactionRequestResult.isSuccess() ? 1 : 0;
      int failureCount = interactionRequestResult.isSuccess() ? 0 : 1;
      LocalDateTime interactionTime = SystemDateTime.now();
      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(
              operationType, method, 1, successCount, failureCount, interactionTime);
      resultMap.put(interactionRequestResult.interactionTypeName(), result);
    }

    AuthenticationInteractionResults updatedResults =
        new AuthenticationInteractionResults(resultMap);
    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        updatedRequest,
        authenticationPolicy,
        updatedResults,
        attributes);
  }

  private AuthenticationRequest updateWithUser(
      AuthenticationInteractionRequestResult interactionRequestResult) {

    if (!interactionRequestResult.hasUser()) {
      return request;
    }

    if (!request.hasUser()) {
      return request.updateWithUser(interactionRequestResult);
    }

    if (!request.isSameUser(interactionRequestResult.user())) {
      throw new BadRequestException("User is not the same as the request");
    }

    return request;
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

      String operationType = OperationType.AUTHENTICATION.name();
      String method = result.ssoProvider().name();
      int successCount = result.isSuccess() ? 1 : 0;
      int failureCount = result.isSuccess() ? 0 : 1;
      LocalDateTime interactionTime = SystemDateTime.now();
      AuthenticationInteractionResult authenticationInteractionResult =
          new AuthenticationInteractionResult(
              operationType, method, 1, successCount, failureCount, interactionTime);
      resultMap.put(result.interactionTypeName(), authenticationInteractionResult);
    }

    AuthenticationInteractionResults updatedResults =
        new AuthenticationInteractionResults(resultMap);
    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        updatedRequest,
        authenticationPolicy,
        updatedResults,
        attributes);
  }

  public AuthenticationTransactionIdentifier identifier() {
    return identifier;
  }

  public AuthorizationIdentifier authorizationIdentifier() {
    return authorizationIdentifier;
  }

  public AuthFlow flow() {
    return request.authorizationFlow();
  }

  public AuthenticationRequest request() {
    return request;
  }

  public AuthenticationDevice authenticationDevice() {
    return request.authenticationDevice();
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

  public Map<String, Object> interactionResultsAsMapObject() {
    return interactionResults.toMapAsObject();
  }

  public Map<String, Object> toRequestMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.putAll(request.toMapForPublic());
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

  public boolean isComplete() {
    return isSuccess() || isFailure() || isLocked();
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

  public boolean hasAuthenticationDevice() {
    return request.hasAuthenticationDevice();
  }

  public AuthenticationContext requestContext() {
    return request.context();
  }

  public AuthenticationTransactionAttributes attributes() {
    return attributes;
  }

  public boolean hasAttributes() {
    return attributes != null && attributes.exists();
  }

  public Authentication authentication() {
    if (!isSuccess()) {
      return new Authentication();
    }

    LocalDateTime time = interactionResults.authenticationTime();
    List<String> methods = interactionResults.authenticationMethods();
    String acr = AcrResolver.resolve(authenticationPolicy.acrMappingRules(), methods);
    return new Authentication().setTime(time).addMethods(methods).addAcr(acr);
  }

  public List<String> deniedScopes() {
    Map<String, List<String>> levelOfAuthenticationScopes =
        authenticationPolicy.levelOfAuthenticationScopes();
    List<String> methods = interactionResults.authenticationMethods();
    return LoaDeniedScopeResolver.resolve(levelOfAuthenticationScopes, methods);
  }

  public boolean hasAuthorizationIdentifier() {
    return authorizationIdentifier != null && authorizationIdentifier.exists();
  }
}
