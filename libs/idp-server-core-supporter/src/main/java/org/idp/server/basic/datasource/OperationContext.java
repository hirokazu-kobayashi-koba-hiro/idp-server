package org.idp.server.basic.datasource;

public class OperationContext {
  private static final ThreadLocal<OperationType> context =
      ThreadLocal.withInitial(() -> OperationType.WRITE);

  public static void set(OperationType type) {
    context.set(type);
  }

  public static OperationType get() {
    return context.get();
  }

  public static void clear() {
    context.remove();
  }
}
