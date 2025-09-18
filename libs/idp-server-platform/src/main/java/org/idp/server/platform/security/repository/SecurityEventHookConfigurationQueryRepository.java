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

package org.idp.server.platform.security.repository;

import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;

public interface SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigurations find(Tenant tenant);

  SecurityEventHookConfiguration find(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier);

  SecurityEventHookConfiguration findWithDisabled(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier, boolean includeDisabled);

  SecurityEventHookConfiguration find(Tenant tenant, String type);

  long findTotalCount(Tenant tenant);

  List<SecurityEventHookConfiguration> findList(Tenant tenant, int limit, int offset);
}
