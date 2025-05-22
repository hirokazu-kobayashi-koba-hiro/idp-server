/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface SecurityEventApi {
  void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent);
}
