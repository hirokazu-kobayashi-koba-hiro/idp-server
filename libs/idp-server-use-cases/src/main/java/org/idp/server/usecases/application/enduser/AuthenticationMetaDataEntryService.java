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

package org.idp.server.usecases.application.enduser;

import java.util.Map;
import java.util.Optional;
import org.idp.server.authentication.interactors.fidouaf.*;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.*;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction(readOnly = true)
public class AuthenticationMetaDataEntryService implements AuthenticationMetaDataApi {

  // FIDO UAF facets (TrustedFacets) is a public discovery document derived from rarely-changing
  // tenant authentication configuration. Caching the rendered body lets repeated client fetches
  // skip the executor (which may issue an outbound HTTP call) and the response mapping. The TTL
  // bounds how long a configuration change takes to propagate to the served document.
  static final String FIDO_UAF_FACETS_CACHE_KEY_PREFIX = "fido_uaf_facets:";
  static final int FIDO_UAF_FACETS_CACHE_TTL_SECONDS = 300;

  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  AuthenticationExecutors authenticationExecutors;
  TenantQueryRepository tenantQueryRepository;
  CacheStore cacheStore;

  public AuthenticationMetaDataEntryService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      AuthenticationExecutors authenticationExecutors,
      TenantQueryRepository tenantQueryRepository,
      CacheStore cacheStore) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.authenticationExecutors = authenticationExecutors;
    this.tenantQueryRepository = tenantQueryRepository;
    this.cacheStore = cacheStore;
  }

  @Override
  public AuthenticationMetadataResponse getFidoUafFacets(
      TenantIdentifier tenantIdentifier, RequestAttributes requestAttributes) {

    String cacheKey = FIDO_UAF_FACETS_CACHE_KEY_PREFIX + tenantIdentifier.value();
    Optional<Map> cached = cacheStore.find(cacheKey, Map.class);
    if (cached.isPresent()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cachedBody = (Map<String, Object>) cached.get();
      // only successful results are cached, so a hit is always an OK response
      return new AuthenticationMetadataResponse(AuthenticationExecutionStatus.OK, cachedBody);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationConfiguration authenticationConfiguration =
        authenticationConfigurationQueryRepository.get(tenant, "fido-uaf");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        authenticationConfiguration.getAuthenticationConfig("fido-uaf-facets");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant,
            new AuthenticationTransactionIdentifier(),
            new AuthenticationExecutionRequest(),
            requestAttributes,
            execution);

    AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(executionResult.contents());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> responseBody =
        MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

    // cache only successful results so a transient executor failure is not pinned for the TTL
    if (executionResult.status().isOk()) {
      cacheStore.put(cacheKey, responseBody, FIDO_UAF_FACETS_CACHE_TTL_SECONDS);
    }

    return new AuthenticationMetadataResponse(executionResult.status(), responseBody);
  }
}
