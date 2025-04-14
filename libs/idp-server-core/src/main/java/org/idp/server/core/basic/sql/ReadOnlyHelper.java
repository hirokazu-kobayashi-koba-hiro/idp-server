package org.idp.server.core.basic.sql;

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
