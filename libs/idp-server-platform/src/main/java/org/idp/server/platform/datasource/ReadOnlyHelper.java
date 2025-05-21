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
