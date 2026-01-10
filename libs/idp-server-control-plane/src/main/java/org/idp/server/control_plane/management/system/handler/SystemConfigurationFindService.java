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
import org.idp.server.control_plane.management.system.io.SystemConfigurationFindRequest;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving system configuration.
 *
 * <p>Handles system configuration retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Configuration retrieval from resolver
 *   <li>Response creation
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Exception handling
 * </ul>
 */
public class SystemConfigurationFindService
    implements SystemConfigurationManagementService<SystemConfigurationFindRequest> {

  private final SystemConfigurationResolver resolver;
  private final LoggerWrapper log = LoggerWrapper.getLogger(SystemConfigurationFindService.class);

  public SystemConfigurationFindService(SystemConfigurationResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public SystemConfigurationManagementResponse execute(
      SystemConfigurationManagementContextBuilder builder,
      User operator,
      OAuthToken oAuthToken,
      SystemConfigurationFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    log.info("System configuration retrieval requested");

    SystemConfiguration configuration = resolver.resolve();

    // For read operations, set before = current state (no after for read)
    builder.withBefore(configuration);

    return SystemConfigurationManagementResponse.success(configuration);
  }
}
