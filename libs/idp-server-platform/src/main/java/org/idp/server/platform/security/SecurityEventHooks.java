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

import java.util.Map;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.exception.UnSupportedException;

public class SecurityEventHooks {

  Map<SecurityEventHookType, SecurityEventHookExecutor> values;

  public SecurityEventHooks(Map<SecurityEventHookType, SecurityEventHookExecutor> values) {
    this.values = values;
  }

  public SecurityEventHookExecutor get(SecurityEventHookType type) {

    SecurityEventHookExecutor securityEventHookExecutor = values.get(type);

    if (securityEventHookExecutor == null) {
      throw new UnSupportedException("No executor registered for type " + type);
    }

    return securityEventHookExecutor;
  }
}
