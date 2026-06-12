/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationMetadataResponse;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the cache-aside invariants of {@code getFidoUafFacets}:
 *
 * <ul>
 *   <li>cache hit returns immediately with an OK response and never runs the executor
 *   <li>cache miss with a successful executor result populates the cache
 *   <li>cache miss with a failed executor result does NOT populate the cache
 * </ul>
 */
class AuthenticationMetaDataEntryServiceTest {

  AuthenticationConfigurationQueryRepository configRepository;
  AuthenticationExecutors executors;
  TenantQueryRepository tenantQueryRepository;
  CacheStore cacheStore;
  AuthenticationMetaDataEntryService service;

  TenantIdentifier tenantIdentifier = new TenantIdentifier("1e68932e-ed4a-43e7-b412-460665e42df3");
  String expectedCacheKey = "fido_uaf_facets:" + tenantIdentifier.value();

  @BeforeEach
  void setUp() {
    configRepository = mock(AuthenticationConfigurationQueryRepository.class);
    executors = mock(AuthenticationExecutors.class);
    tenantQueryRepository = mock(TenantQueryRepository.class);
    cacheStore = mock(CacheStore.class);
    service =
        new AuthenticationMetaDataEntryService(
            configRepository, executors, tenantQueryRepository, cacheStore);
  }

  @Test
  void cacheHitReturnsOkWithoutRunningExecutor() {
    Map<String, Object> cachedBody = Map.of("trustedFacets", List.of());
    when(cacheStore.find(eq(expectedCacheKey), eq(Map.class))).thenReturn(Optional.of(cachedBody));

    AuthenticationMetadataResponse response =
        service.getFidoUafFacets(tenantIdentifier, new RequestAttributes());

    assertTrue(response.isSuccess());
    assertEquals(cachedBody, response.contents());
    // a hit must not touch the tenant store, config or the (potentially outbound) executor
    verifyNoInteractions(tenantQueryRepository, configRepository, executors);
    verify(cacheStore, never()).put(any(), any(), anyInt());
  }

  @Test
  void cacheMissWithSuccessfulExecutorPopulatesCache() {
    when(cacheStore.find(eq(expectedCacheKey), eq(Map.class))).thenReturn(Optional.empty());
    stubExecutorChain(AuthenticationExecutionResult.success(Map.of("trustedFacets", List.of())));

    AuthenticationMetadataResponse response =
        service.getFidoUafFacets(tenantIdentifier, new RequestAttributes());

    assertTrue(response.isSuccess());
    verify(cacheStore).put(eq(expectedCacheKey), any(), eq(300));
  }

  @Test
  void cacheMissWithFailedExecutorDoesNotPopulateCache() {
    when(cacheStore.find(eq(expectedCacheKey), eq(Map.class))).thenReturn(Optional.empty());
    stubExecutorChain(AuthenticationExecutionResult.serverError(Map.of("error", "upstream_down")));

    AuthenticationMetadataResponse response =
        service.getFidoUafFacets(tenantIdentifier, new RequestAttributes());

    assertFalse(response.isSuccess());
    verify(cacheStore, never()).put(any(), any(), anyInt());
  }

  private void stubExecutorChain(AuthenticationExecutionResult executionResult) {
    Tenant tenant = mock(Tenant.class);
    AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
    AuthenticationInteractionConfig interactionConfig = mock(AuthenticationInteractionConfig.class);
    AuthenticationExecutionConfig executionConfig = mock(AuthenticationExecutionConfig.class);
    AuthenticationResponseConfig responseConfig = mock(AuthenticationResponseConfig.class);
    AuthenticationExecutor executor = mock(AuthenticationExecutor.class);

    when(tenantQueryRepository.get(tenantIdentifier)).thenReturn(tenant);
    when(configRepository.get(tenant, "fido-uaf")).thenReturn(configuration);
    when(configuration.getAuthenticationConfig("fido-uaf-facets")).thenReturn(interactionConfig);
    when(interactionConfig.execution()).thenReturn(executionConfig);
    when(interactionConfig.response()).thenReturn(responseConfig);
    when(executionConfig.function()).thenReturn("http_request");
    when(responseConfig.bodyMappingRules()).thenReturn(List.of());
    when(executors.get("http_request")).thenReturn(executor);
    when(executor.execute(any(), any(), any(), any(), any())).thenReturn(executionResult);
  }
}
