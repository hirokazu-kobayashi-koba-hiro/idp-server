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

package org.idp.server.control_plane.management.authentication.interaction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementRequest;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Unified context for all authentication interaction management operations.
 *
 * <p>This context supports:
 *
 * <ul>
 *   <li>get operation (result only)
 *   <li>findList operation (result only)
 * </ul>
 *
 * <p>This enables:
 *
 * <ul>
 *   <li>Single context implementation for all read operations
 *   <li>Complete AuditableContext implementation
 *   <li>Error tracking via exception field
 *   <li>Partial context construction for early failures
 * </ul>
 */
public class AuthenticationInteractionManagementContext implements AuditableContext {

  TenantIdentifier tenantIdentifier;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  AuthenticationInteraction result;
  AuthenticationInteractionManagementRequest request;
  ManagementApiException exception;

  public AuthenticationInteractionManagementContext(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      AuthenticationInteraction result,
      AuthenticationInteractionManagementRequest request,
      ManagementApiException exception) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.result = result;
    this.request = request;
    this.exception = exception;
  }

  public AuthenticationInteraction result() {
    return result;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException exception() {
    return exception;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "authentication_interaction";
  }

  @Override
  public String description() {
    return "authentication interaction management api";
  }

  @Override
  public String tenantId() {
    return oAuthToken.tenantIdentifier().value();
  }

  @Override
  public String clientId() {
    return oAuthToken.requestedClientId().value();
  }

  @Override
  public String userId() {
    return operator.sub();
  }

  @Override
  public String externalUserId() {
    return operator.externalUserId();
  }

  @Override
  public Map<String, Object> userPayload() {
    return operator.toMap();
  }

  @Override
  public String targetResource() {
    return requestAttributes.resource().value();
  }

  @Override
  public String targetResourceAction() {
    return requestAttributes.action().value();
  }

  @Override
  public String ipAddress() {
    return requestAttributes.getIpAddress().value();
  }

  @Override
  public String userAgent() {
    return requestAttributes.getUserAgent().value();
  }

  @Override
  public Map<String, Object> request() {
    return request != null ? request.toMap() : Collections.emptyMap();
  }

  @Override
  public Map<String, Object> before() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> after() {
    return result != null ? result.toMap() : Collections.emptyMap();
  }

  @Override
  public String outcomeResult() {
    return exception != null ? "failure" : "success";
  }

  @Override
  public String outcomeReason() {
    return exception != null ? exception.errorCode() : null;
  }

  @Override
  public String targetTenantId() {
    return tenantIdentifier.value();
  }

  @Override
  public Map<String, Object> attributes() {
    Map<String, Object> attributes = new HashMap<>();
    if (exception != null) {
      Map<String, Object> error = new HashMap<>();
      error.put("error_code", exception.errorCode());
      error.put("error_description", exception.errorDescription());

      // Add error details if available
      Map<String, Object> errorDetails = exception.errorDetails();
      if (errorDetails != null && !errorDetails.isEmpty()) {
        error.putAll(errorDetails);
      }
      attributes.put("error", error);
    }

    return attributes;
  }

  @Override
  public boolean dryRun() {
    return false;
  }
}
