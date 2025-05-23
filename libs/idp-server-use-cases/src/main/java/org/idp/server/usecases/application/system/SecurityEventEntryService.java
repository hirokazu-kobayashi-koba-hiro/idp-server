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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.platform.security.SecurityEventHooks;
import org.idp.server.platform.security.handler.SecurityEventHandler;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;

@Transaction
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;
  TenantQueryRepository tenantQueryRepository;

  public SecurityEventEntryService(
      SecurityEventHooks securityEventHooks,
      SecurityEventCommandRepository securityEventCommandRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHookConfigurationQueryRepository hookQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.securityEventHandler =
        new SecurityEventHandler(
            securityEventHooks,
            securityEventCommandRepository,
            securityEventHookResultCommandRepository,
            hookQueryRepository);
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    securityEventHandler.handle(tenant, securityEvent);
  }
}
