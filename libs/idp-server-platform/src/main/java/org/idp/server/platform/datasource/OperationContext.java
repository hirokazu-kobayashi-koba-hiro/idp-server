/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.datasource;

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
