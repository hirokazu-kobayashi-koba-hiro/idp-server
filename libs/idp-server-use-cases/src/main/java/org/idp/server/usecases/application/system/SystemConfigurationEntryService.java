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
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationApi;
import org.idp.server.platform.system.SystemConfigurationResolver;

/**
 * Entry service for system configuration access.
 *
 * <p>This service provides transaction-managed access to system configuration, making it safe to
 * call from Filters and other components outside the normal request transaction context.
 *
 * @see TenantMetaDataEntryService
 */
@Transaction(readOnly = true)
public class SystemConfigurationEntryService implements SystemConfigurationApi {

  private final SystemConfigurationResolver systemConfigurationResolver;

  public SystemConfigurationEntryService(SystemConfigurationResolver systemConfigurationResolver) {
    this.systemConfigurationResolver = systemConfigurationResolver;
  }

  @Override
  public SystemConfiguration get() {
    return systemConfigurationResolver.resolve();
  }
}
