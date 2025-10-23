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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.io.TenantUpdateRequest;
import org.idp.server.control_plane.management.tenant.validator.TenantUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating tenants.
 *
 * <p>Handles tenant update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant existence verification
 *   <li>Context creation (before/after comparison)
 *   <li>Tenant update in repository
 * </ul>
 */
public class TenantUpdateService implements TenantManagementService<TenantUpdateRequest> {

  private final TenantQueryRepository tenantQueryRepository;
  private final TenantCommandRepository tenantCommandRepository;

  public TenantUpdateService(
      TenantQueryRepository tenantQueryRepository,
      TenantCommandRepository tenantCommandRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.tenantCommandRepository = tenantCommandRepository;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing tenant (throws ResourceNotFoundException if not found)
    Tenant before = tenantQueryRepository.get(request.tenantIdentifier());

    // 1. Request validation
    new TenantUpdateRequestValidator(request.tenantRequest()).validate();

    Tenant after = updateTenant(request.tenantRequest(), before);

    builder.withBefore(before);
    builder.withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);
    TenantManagementResponse response =
        new TenantManagementResponse(TenantManagementStatus.OK, contents);
    // 4. Dry-run check
    if (dryRun) {
      return response;
    }

    // 5. Repository operation
    tenantCommandRepository.update(after);

    return response;
  }

  public Tenant updateTenant(TenantRequest request, Tenant before) {
    TenantRegistrationRequest tenantRequest =
        JsonConverter.snakeCaseInstance().read(request.toMap(), TenantRegistrationRequest.class);

    TenantAttributes attributes =
        tenantRequest.attributes() != null
            ? new TenantAttributes(tenantRequest.attributes())
            : new TenantAttributes();

    UIConfiguration uiConfiguration =
        tenantRequest.uiConfig() != null
            ? new UIConfiguration(tenantRequest.uiConfig())
            : new UIConfiguration();

    CorsConfiguration corsConfiguration =
        tenantRequest.corsConfig() != null
            ? new CorsConfiguration(tenantRequest.corsConfig())
            : new CorsConfiguration();

    SessionConfiguration sessionConfiguration =
        tenantRequest.sessionConfig() != null
            ? new SessionConfiguration(tenantRequest.sessionConfig())
            : new SessionConfiguration();

    SecurityEventLogConfiguration securityEventLogConfiguration =
        tenantRequest.securityEventLogConfig() != null
            ? new SecurityEventLogConfiguration(tenantRequest.securityEventLogConfig())
            : new SecurityEventLogConfiguration();

    SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration =
        tenantRequest.securityEventUserConfig() != null
            ? new SecurityEventUserAttributeConfiguration(tenantRequest.securityEventUserConfig())
            : new SecurityEventUserAttributeConfiguration();

    TenantIdentityPolicy identityPolicyConfig =
        convertIdentityPolicyConfig(tenantRequest.identityPolicyConfig());

    return new Tenant(
        before.identifier(),
        before.name(),
        before.type(),
        tenantRequest.tenantDomain(),
        before.authorizationProvider(),
        attributes,
        uiConfiguration,
        corsConfiguration,
        sessionConfiguration,
        securityEventLogConfiguration,
        securityEventUserAttributeConfiguration,
        identityPolicyConfig);
  }

  private TenantIdentityPolicy convertIdentityPolicyConfig(Map<String, Object> configMap) {
    return TenantIdentityPolicy.fromMap(configMap);
  }
}
