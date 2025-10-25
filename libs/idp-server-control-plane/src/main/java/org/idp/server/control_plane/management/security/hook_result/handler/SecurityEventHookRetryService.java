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

package org.idp.server.control_plane.management.security.hook_result.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementStatus;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookRetryRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrying failed security event hook executions.
 *
 * <p>Handles security event hook retry logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Previous hook result existence verification
 *   <li>Security event reconstruction from previous execution
 *   <li>Latest hook configuration retrieval
 *   <li>Hook re-execution with original parameters
 *   <li>Status update (RETRY_SUCCESS or RETRY_FAILURE)
 *   <li>New hook result registration
 * </ul>
 *
 * <h2>Retry Workflow</h2>
 *
 * <ol>
 *   <li>Retrieve previous failed hook result by identifier
 *   <li>Verify hook result exists (throw ResourceNotFoundException if not)
 *   <li>Extract hook type and security event from previous result
 *   <li>Fetch latest hook configuration from repository
 *   <li>Execute hook with security event and configuration
 *   <li>Update previous result status based on retry outcome
 *   <li>Register new hook result
 * </ol>
 */
public class SecurityEventHookRetryService
    implements SecurityEventHookManagementService<SecurityEventHookRetryRequest> {

  private final SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository;
  private final SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository;
  private final SecurityEventHooks securityEventHooks;
  private final SecurityEventHookConfigurationQueryRepository
      securityEventHookConfigurationQueryRepository;

  public SecurityEventHookRetryService(
      SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventHookResultQueryRepository = securityEventHookResultQueryRepository;
    this.securityEventHookResultCommandRepository = securityEventHookResultCommandRepository;
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  @Override
  public SecurityEventHookManagementResponse execute(
      SecurityEventHookManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRetryRequest request,
      RequestAttributes requestAttributes) {

    SecurityEventHookResultIdentifier identifier = request.securityEventHookResultIdentifier();
    SecurityEventHookResult previousHookResult =
        securityEventHookResultQueryRepository.find(tenant, identifier);

    if (!previousHookResult.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook result not found: " + identifier.value());
    }

    SecurityEventHookType hookType = previousHookResult.type();
    SecurityEvent securityEvent = previousHookResult.securityEvent();
    SecurityEventHook securityEventHook = securityEventHooks.get(hookType);

    SecurityEventHookConfiguration securityEventHookConfiguration =
        securityEventHookConfigurationQueryRepository.find(tenant, hookType.name());
    SecurityEventHookResult securityEventHookResult =
        securityEventHook.execute(tenant, securityEvent, securityEventHookConfiguration);

    SecurityEventHookStatus retryStatus =
        securityEventHookResult.isSuccess()
            ? SecurityEventHookStatus.RETRY_SUCCESS
            : SecurityEventHookStatus.RETRY_FAILURE;
    securityEventHookResultCommandRepository.updateStatus(tenant, previousHookResult, retryStatus);
    securityEventHookResultCommandRepository.register(tenant, securityEventHookResult);

    return new SecurityEventHookManagementResponse(
        SecurityEventHookManagementStatus.OK, securityEventHookResult.toMap());
  }
}
