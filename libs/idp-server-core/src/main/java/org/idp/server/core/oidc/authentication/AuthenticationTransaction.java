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
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.core.oidc.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.oidc.configuration.authentication.AcrMapper;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationResultConditions;
import org.idp.server.core.oidc.federation.FederationInteractionResult;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.exception.BadRequestException;

public class AuthenticationTransaction {
  AuthorizationIdentifier identifier;
  AuthenticationRequest request;
  AuthenticationPolicy authenticationPolicy;
  AuthenticationInteractionResults interactionResults;

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

  public AuthorizationIdentifier identifier() {
    return identifier;
  }

  public AuthorizationFlow flow() {
    return request.authorizationFlow();
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

  public Authentication authentication() {
    LocalDateTime time = SystemDateTime.now();
    AcrMapper acrMapper = authenticationPolicy.acrMapper();
    List<String> methods = interactionResults.authenticationMethods();
    List<String> acrValues = acrMapper.resolveAcrFrom(methods);
    return new Authentication().setTime(time).addMethods(methods).addAcrValues(acrValues);
  }
}
