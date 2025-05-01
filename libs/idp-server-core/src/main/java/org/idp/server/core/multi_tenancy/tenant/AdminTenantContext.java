package org.idp.server.core.multi_tenancy.tenant;

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
