package org.idp.server.core.tenant;

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
}
