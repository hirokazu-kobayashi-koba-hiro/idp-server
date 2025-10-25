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

package org.idp.server.usecases.application.system;

import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.health.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

@Transaction
public class HealthCheckEntryService implements HealthCheckApi {

  private final TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(HealthCheckEntryService.class);

  public HealthCheckEntryService(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  public HealthCheckResult check(TenantIdentifier tenantIdentifier) {
    log.debug(
        "HealthCheckEntryService.check: starting health check for tenant={}",
        tenantIdentifier.value());

    HealthCheckResult.Builder builder = new HealthCheckResult.Builder();

    try {
      // Check database connectivity by attempting to fetch tenant
      Tenant tenant = tenantQueryRepository.find(tenantIdentifier);
      builder.status(HealthStatus.UP);

      log.debug(
          "HealthCheckEntryService.check: health check passed for tenant={}, status=UP",
          tenantIdentifier.value());

      return builder.build();
    } catch (Exception e) {
      builder.status(HealthStatus.DOWN);

      log.error(
          "HealthCheckEntryService.check: health check failed for tenant={}, status=DOWN, error={}",
          tenantIdentifier.value(),
          e.getMessage(),
          e);

      return builder.build();
    }
  }
}
