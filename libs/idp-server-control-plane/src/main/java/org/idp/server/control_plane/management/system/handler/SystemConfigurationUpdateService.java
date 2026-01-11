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

package org.idp.server.control_plane.management.system.handler;

import org.idp.server.control_plane.management.system.SystemConfigurationManagementContextBuilder;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationRepository;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating system configuration.
 *
 * <p>Handles system configuration update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Configuration conversion
 *   <li>Repository operations
 *   <li>Cache invalidation
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Exception handling
 * </ul>
 */
public class SystemConfigurationUpdateService
    implements SystemConfigurationManagementService<SystemConfigurationUpdateRequest> {

  private final SystemConfigurationRepository repository;
  private final SystemConfigurationResolver resolver;
  private final LoggerWrapper log = LoggerWrapper.getLogger(SystemConfigurationUpdateService.class);

  public SystemConfigurationUpdateService(
      SystemConfigurationRepository repository, SystemConfigurationResolver resolver) {
    this.repository = repository;
    this.resolver = resolver;
  }

  @Override
  public SystemConfigurationManagementResponse execute(
      SystemConfigurationManagementContextBuilder builder,
      User operator,
      OAuthToken oAuthToken,
      SystemConfigurationUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    log.info("System configuration update requested, dryRun: {}", dryRun);

    if (!request.hasConfiguration()) {
      return SystemConfigurationManagementResponse.error("Configuration is required");
    }

    // Capture current configuration before update
    SystemConfiguration currentConfiguration = resolver.resolve();
    builder.withBefore(currentConfiguration);

    SystemConfiguration newConfiguration = request.toSystemConfiguration();

    if (dryRun) {
      log.info("Dry-run: validation passed");
      builder.withAfter(newConfiguration);
      return SystemConfigurationManagementResponse.validationPassed(newConfiguration);
    }

    // Save configuration
    repository.register(newConfiguration);

    // Invalidate cache so next resolve() gets fresh data
    resolver.invalidateCache();

    // Capture configuration after update
    builder.withAfter(newConfiguration);

    log.info("System configuration updated successfully");
    return SystemConfigurationManagementResponse.updated(newConfiguration);
  }
}
