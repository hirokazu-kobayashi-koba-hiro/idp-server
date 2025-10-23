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

package org.idp.server.control_plane.management.tenant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class TenantManagementRegistrationContext implements AuditableContext {

  Tenant adminTenant; // nullable: null when Tenant retrieval fails
  TenantIdentifier adminTenantIdentifier;
  Tenant newTenant; // nullable: null in error scenarios
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization; // nullable: system-level API doesn't have Organization
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  TenantRequest request;
  boolean dryRun;
  ManagementApiException exception; // nullable: null in success scenarios

  // Constructor 1: Full information (existing pattern)
  public TenantManagementRegistrationContext(
      Tenant adminTenant,
      Tenant newTenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      User operator,
      TenantRequest request,
      boolean dryRun) {
    this(
        adminTenant,
        null,
        newTenant,
        authorizationServerConfiguration,
        organization,
        operator,
        null,
        null,
        request,
        dryRun,
        null);
  }

  // Constructor 2: With OAuthToken and RequestAttributes (new pattern)
  public TenantManagementRegistrationContext(
      Tenant adminTenant,
      Tenant newTenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      TenantRequest request,
      boolean dryRun) {
    this(
        adminTenant,
        null,
        newTenant,
        authorizationServerConfiguration,
        organization,
        operator,
        oAuthToken,
        requestAttributes,
        request,
        dryRun,
        null);
  }

  // Constructor 3: With exception (error scenarios)
  public TenantManagementRegistrationContext(
      Tenant adminTenant,
      TenantIdentifier adminTenantIdentifier,
      Tenant newTenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      TenantRequest request,
      boolean dryRun,
      ManagementApiException exception) {
    this.adminTenant = adminTenant;
    this.adminTenantIdentifier =
        adminTenantIdentifier != null
            ? adminTenantIdentifier
            : (adminTenant != null ? adminTenant.identifier() : null);
    this.newTenant = newTenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
    this.exception = exception;
  }

  public Tenant adminTenant() {
    return adminTenant;
  }

  public Tenant newTenant() {
    return newTenant;
  }

  public AuthorizationServerConfiguration authorizationServerConfiguration() {
    return authorizationServerConfiguration;
  }

  public Organization organization() {
    return organization;
  }

  // TODO
  public User user() {
    return User.notFound();
  }

  @Override
  public String type() {
    return "tenant";
  }

  @Override
  public String description() {
    return "";
  }

  @Override
  public String tenantId() {
    return "";
  }

  @Override
  public String clientId() {
    return "";
  }

  @Override
  public String userId() {
    return "";
  }

  @Override
  public String externalUserId() {
    return "";
  }

  @Override
  public Map<String, Object> userPayload() {
    return Map.of();
  }

  @Override
  public String targetResource() {
    return "";
  }

  @Override
  public String targetResourceAction() {
    return "";
  }

  @Override
  public String ipAddress() {
    return "";
  }

  @Override
  public String userAgent() {
    return "";
  }

  @Override
  public Map<String, Object> request() {
    return Map.of();
  }

  @Override
  public Map<String, Object> before() {
    return Map.of();
  }

  @Override
  public Map<String, Object> after() {
    return Map.of();
  }

  @Override
  public String outcomeResult() {
    return "";
  }

  @Override
  public String outcomeReason() {
    return "";
  }

  @Override
  public String targetTenantId() {
    return newTenant.identifierValue();
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }

  @Override
  public Map<String, Object> attributes() {
    return Collections.emptyMap();
  }

  public TenantManagementResponse toResponse() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", newTenant.toMap());
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.CREATED, contents);
  }
}
