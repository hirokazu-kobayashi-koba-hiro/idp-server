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
package org.idp.server.usecases.control_plane.system_manager;

import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.management.risk.RiskAssessmentConfigManagementApi;
import org.idp.server.control_plane.management.risk.RiskAssessmentConfigManagementResponse;
import org.idp.server.control_plane.management.risk.RiskAssessmentConfigRequest;
import org.idp.server.core.openid.authentication.risk.RiskAssessmentConfig;
import org.idp.server.core.openid.authentication.risk.repository.RiskAssessmentConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.risk.repository.RiskAssessmentConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class RiskAssessmentConfigManagementEntryService
    implements RiskAssessmentConfigManagementApi {

  RiskAssessmentConfigurationCommandRepository commandRepository;
  RiskAssessmentConfigurationQueryRepository queryRepository;
  TenantQueryRepository tenantQueryRepository;
  JsonConverter jsonConverter;

  public RiskAssessmentConfigManagementEntryService(
      RiskAssessmentConfigurationCommandRepository commandRepository,
      RiskAssessmentConfigurationQueryRepository queryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public RiskAssessmentConfigManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    RiskAssessmentConfig config = queryRepository.find(tenant);

    if (!config.exists()) {
      return RiskAssessmentConfigManagementResponse.notFound();
    }

    return RiskAssessmentConfigManagementResponse.ok(config.toMap());
  }

  @Override
  public RiskAssessmentConfigManagementResponse put(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RiskAssessmentConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    RiskAssessmentConfig config =
        jsonConverter.read(jsonConverter.write(request.toMap()), RiskAssessmentConfig.class);

    if (dryRun) {
      return RiskAssessmentConfigManagementResponse.ok(config.toMap());
    }

    RiskAssessmentConfig existing = queryRepository.find(tenant);
    if (existing.exists()) {
      commandRepository.update(tenant, config);
    } else {
      commandRepository.register(tenant, config);
    }

    return RiskAssessmentConfigManagementResponse.ok(config.toMap());
  }

  @Override
  public RiskAssessmentConfigManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (dryRun) {
      return RiskAssessmentConfigManagementResponse.noContent();
    }

    commandRepository.delete(tenant);

    return RiskAssessmentConfigManagementResponse.noContent();
  }
}
