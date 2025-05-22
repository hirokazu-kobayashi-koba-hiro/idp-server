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

import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookExecutor {

  SecurityEventHookType type();

  default boolean shouldNotExecute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {
    return !hookConfiguration.hasTrigger(securityEvent.type().value());
  }

  SecurityEventHookResult execute(
      Tenant tenant, SecurityEvent securityEvent, SecurityEventHookConfiguration configuration);
}
