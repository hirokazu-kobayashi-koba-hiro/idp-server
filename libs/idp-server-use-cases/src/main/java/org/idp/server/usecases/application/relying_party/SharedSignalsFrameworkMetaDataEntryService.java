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

package org.idp.server.usecases.application.relying_party;

import java.util.Map;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.security.event.hook.ssf.SharedSignalFrameworkMetadataConfig;
import org.idp.server.security.event.hook.ssf.SharedSignalsFrameworkMetaDataApi;
import org.idp.server.security.event.hook.ssf.io.SharedSignalsFrameworkConfigurationRequestResponse;
import org.idp.server.security.event.hook.ssf.io.SharedSignalsFrameworkConfigurationRequestStatus;
import org.idp.server.security.event.hook.ssf.io.SharedSignalsFrameworkJwksRequestResponse;
import org.idp.server.security.event.hook.ssf.io.SharedSignalsFrameworkJwksRequestStatus;

// TODO to be more correct place
@Transaction(readOnly = true)
public class SharedSignalsFrameworkMetaDataEntryService
    implements SharedSignalsFrameworkMetaDataApi {

  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public SharedSignalsFrameworkMetaDataEntryService(
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public SharedSignalsFrameworkConfigurationRequestResponse getConfiguration(
      TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantQueryRepository.find(tenantIdentifier);
    SecurityEventHookConfiguration securityEventHookConfiguration =
        securityEventHookConfigurationQueryRepository.find(tenant, "SSF");

    if (!securityEventHookConfiguration.exists()) {
      return new SharedSignalsFrameworkConfigurationRequestResponse(
          SharedSignalsFrameworkConfigurationRequestStatus.NOT_FOUND, null);
    }

    SharedSignalFrameworkMetadataConfig metadataConfig =
        jsonConverter.read(
            securityEventHookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);

    return new SharedSignalsFrameworkConfigurationRequestResponse(
        SharedSignalsFrameworkConfigurationRequestStatus.OK, metadataConfig.toConfigMap());
  }

  @Override
  public SharedSignalsFrameworkJwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {
    try {
      Tenant tenant = tenantQueryRepository.find(tenantIdentifier);
      SecurityEventHookConfiguration securityEventHookConfiguration =
          securityEventHookConfigurationQueryRepository.find(tenant, "SSF");

      if (!securityEventHookConfiguration.exists()) {
        return new SharedSignalsFrameworkJwksRequestResponse(
            SharedSignalsFrameworkJwksRequestStatus.NOT_FOUND, null);
      }

      SharedSignalFrameworkMetadataConfig metadataConfig =
          jsonConverter.read(
              securityEventHookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);
      Map<String, Object> contents = JwkParser.parsePublicKeys(metadataConfig.jwks());
      return new SharedSignalsFrameworkJwksRequestResponse(
          SharedSignalsFrameworkJwksRequestStatus.OK, contents);
    } catch (Exception e) {
      return new SharedSignalsFrameworkJwksRequestResponse(
          SharedSignalsFrameworkJwksRequestStatus.SERVER_ERROR, null);
    }
  }
}
