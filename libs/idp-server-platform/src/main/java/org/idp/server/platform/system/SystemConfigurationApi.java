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

package org.idp.server.platform.system;

/**
 * API interface for retrieving system configuration.
 *
 * <p>This interface is designed to be used by Filters and other components that need to access
 * system configuration outside of normal request transaction context.
 *
 * <p>Implementations should be wrapped with transaction proxy to ensure proper database access.
 *
 * @see org.idp.server.platform.multi_tenancy.tenant.TenantMetaDataApi
 */
public interface SystemConfigurationApi {

  /**
   * Gets the current system configuration.
   *
   * @return the system configuration
   */
  SystemConfiguration get();
}
