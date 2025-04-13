package org.idp.server.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.idp.server.core.basic.sql.*;
import org.idp.server.core.tenant.TenantContext;
import org.idp.server.core.tenant.TenantIdentifier;

public class EntryServiceInterceptor implements InvocationHandler {
  private final Object target;
  private final OperationType operationType;
  private final DialectProvider dialectProvider;
  Logger log = Logger.getLogger(EntryServiceInterceptor.class.getName());

  public EntryServiceInterceptor(
      Object target, OperationType operationType, DialectProvider dialectProvider) {
    this.target = target;
    this.operationType = operationType;
    this.dialectProvider = dialectProvider;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    boolean isTransactional =
        method.isAnnotationPresent(Transactional.class)
            || target.getClass().isAnnotationPresent(Transactional.class);

    if (isTransactional) {
      OperationContext.set(operationType);
      TenantIdentifier tenantId = TenantContext.get();

      TransactionManager.createConnection(Dialect.POSTGRESQL);
      Dialect dialect = dialectProvider.provide(tenantId);
      TransactionManager.closeConnection();

      TransactionManager.beginTransaction(dialect);

      log.info(
          dialect.name()
              + ": begin transaction: "
              + target.getClass().getName()
              + ": "
              + method.getName());
      try {
        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();
        log.info(
            dialect.name()
                + ": commit transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName());
        return result;
      } catch (Exception e) {
        TransactionManager.rollbackTransaction();
        log.info(
            dialect.name()
                + ": rollback transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName());
        throw e;
      }
    } else {
      return method.invoke(target, args);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T createProxy(
      T target, Class<T> interfaceType, OperationType opType, DialectProvider provider) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new EntryServiceInterceptor(target, opType, provider));
  }
}
