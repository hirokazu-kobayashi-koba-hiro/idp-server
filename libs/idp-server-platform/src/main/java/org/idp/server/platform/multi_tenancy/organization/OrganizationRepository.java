/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.organization;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface OrganizationRepository {
  void register(Tenant tenant, Organization organization);

  void update(Tenant tenant, Organization organization);

  Organization get(Tenant tenant, OrganizationIdentifier identifier);
}
