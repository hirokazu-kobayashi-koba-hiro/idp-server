package org.idp.server.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.idp.server.core.basic.sql.*;
import org.idp.server.core.tenant.MissingRequiredTenantIdentifierException;
import org.idp.server.core.tenant.TenantIdentifier;

public class TenantAwareEntryServiceProxy implements InvocationHandler {
  private final Object target;
  private final OperationType operationType;
  private final DialectProvider dialectProvider;
  Logger log = Logger.getLogger(TenantAwareEntryServiceProxy.class.getName());

  public TenantAwareEntryServiceProxy(
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
      TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);

      TransactionManager.createConnection(DatabaseType.POSTGRESQL);
      DatabaseType databaseType = dialectProvider.provide(tenantIdentifier);
      TransactionManager.closeConnection();

      TransactionManager.beginTransaction(databaseType);

      log.info(
          databaseType.name()
              + ": begin transaction: "
              + target.getClass().getName()
              + ": "
              + method.getName());
      try {
        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();
        log.info(
            databaseType.name()
                + ": commit transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName());
        return result;
      } catch (Exception e) {
        TransactionManager.rollbackTransaction();
        log.info(
            databaseType.name()
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

  private TenantIdentifier resolveTenantIdentifier(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof TenantIdentifier tenantId) {
        return tenantId;
      }
    }
    throw new MissingRequiredTenantIdentifierException(
        "Missing required TenantIdentifier. Please ensure it is explicitly passed to the service.");
  }

  @SuppressWarnings("unchecked")
  public static <T> T createProxy(
      T target, Class<T> interfaceType, OperationType opType, DialectProvider provider) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new TenantAwareEntryServiceProxy(target, opType, provider));
  }
}
