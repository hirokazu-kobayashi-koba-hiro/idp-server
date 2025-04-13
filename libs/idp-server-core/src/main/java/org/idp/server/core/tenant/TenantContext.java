package org.idp.server.core.tenant;

public class TenantContext {

  private static final ThreadLocal<TenantIdentifier> context = new ThreadLocal<>();

  public static void set(TenantIdentifier tenantId) {
    context.set(tenantId);
  }

  public static TenantIdentifier get() {
    return context.get();
  }

  public static void clear() {
    context.remove();
  }
}
