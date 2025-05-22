/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security.repository;

import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookConfigurationCommandRepository {

  void register(Tenant tenant, SecurityEventHookConfiguration configuration);

  void update(Tenant tenant, SecurityEventHookConfiguration configuration);

  void delete(Tenant tenant, SecurityEventHookConfiguration configuration);
}
