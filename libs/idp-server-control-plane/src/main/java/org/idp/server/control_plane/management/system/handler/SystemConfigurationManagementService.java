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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for system configuration management operations.
 *
 * <p>Each operation (get, update) has its own Service implementation.
 *
 * @param <T> the request type for this operation
 */
public interface SystemConfigurationManagementService<T> {

  SystemConfigurationManagementResponse execute(
      SystemConfigurationManagementContextBuilder builder,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
