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

import java.util.function.Supplier;

public class ReadOnlyHelper {

  public static <T> T readFromReplica(Supplier<T> supplier) {
    OperationType previous = OperationContext.get();
    try {
      OperationContext.set(OperationType.READ);
      return supplier.get();
    } finally {
      OperationContext.set(previous);
    }
  }
}
