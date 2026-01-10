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

package org.idp.server.control_plane.management.system;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for SystemConfigurationManagementContext.
 *
 * <p>Collects all required data for audit logging throughout the operation lifecycle.
 */
public class SystemConfigurationManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final SystemConfigurationUpdateRequest request;
  private final boolean dryRun;

  private SystemConfiguration before;
  private SystemConfiguration after;

  public SystemConfigurationManagementContextBuilder(
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      SystemConfigurationUpdateRequest request,
      boolean dryRun) {
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public SystemConfigurationManagementContextBuilder withBefore(SystemConfiguration before) {
    this.before = before;
    return this;
  }

  public SystemConfigurationManagementContextBuilder withAfter(SystemConfiguration after) {
    this.after = after;
    return this;
  }

  /**
   * Builds complete context for successful operations.
   *
   * @return full context with before/after configurations
   */
  public AuditableContext build() {
    return new SystemConfigurationManagementContext(
        operator, oAuthToken, requestAttributes, before, after, request, dryRun, null);
  }

  /**
   * Builds partial context for error scenarios.
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    return new SystemConfigurationManagementContext(
        operator, oAuthToken, requestAttributes, before, after, request, dryRun, exception);
  }
}
