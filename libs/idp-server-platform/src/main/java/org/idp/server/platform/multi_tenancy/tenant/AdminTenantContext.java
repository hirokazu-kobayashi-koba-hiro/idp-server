/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.tenant;

public class AdminTenantContext {

  private static TenantIdentifier tenantIdentifier;

  public static void configure(String tenantId) {
    tenantIdentifier = new TenantIdentifier(tenantId);
  }

  public static TenantIdentifier getTenantIdentifier() {
    if (tenantIdentifier == null) {
      throw new RuntimeException("AdminTenantContext is not initialized");
    }
    return tenantIdentifier;
  }

  public static boolean isAdmin(TenantIdentifier tenantIdentifier) {
    return tenantIdentifier.equals(AdminTenantContext.tenantIdentifier);
  }
}
