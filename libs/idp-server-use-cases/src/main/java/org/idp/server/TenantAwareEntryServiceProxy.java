package org.idp.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.idp.server.basic.datasource.*;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.multi_tenancy.tenant.DialectProvider;
import org.idp.server.core.multi_tenancy.tenant.MissingRequiredTenantIdentifierException;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class TenantAwareEntryServiceProxy implements InvocationHandler {
  private final Object target;
  private final DialectProvider dialectProvider;
  LoggerWrapper log = LoggerWrapper.getLogger(TenantAwareEntryServiceProxy.class);

  public TenantAwareEntryServiceProxy(Object target, DialectProvider dialectProvider) {
    this.target = target;
    this.dialectProvider = dialectProvider;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    boolean isTransactional =
        method.isAnnotationPresent(Transaction.class)
            || target.getClass().isAnnotationPresent(Transaction.class);

    Transaction tx = method.getAnnotation(Transaction.class);
    if (tx == null) {
      tx = target.getClass().getAnnotation(Transaction.class);
    }
    boolean readOnly = tx != null && tx.readOnly();
    OperationType operationType = readOnly ? OperationType.READ : OperationType.WRITE;

    if (isTransactional && operationType == OperationType.READ) {
      try {
        OperationContext.set(operationType);
        log.info("READ start: " + target.getClass().getName() + ": " + method.getName() + " ...");
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
        TransactionManager.createConnection(DatabaseType.POSTGRESQL, tenantIdentifier.value());
        DatabaseType databaseType = dialectProvider.provide(tenantIdentifier);
        TransactionManager.closeConnection();

        TransactionManager.createConnection(databaseType, tenantIdentifier.value());
        Object result = method.invoke(target, args);

        TransactionManager.closeConnection();
        log.info("READ end: " + target.getClass().getName() + ": " + method.getName() + " ...");

        return result;
      } catch (InvocationTargetException e) {
        log.error(
            "fail (InvocationTargetException): "
                + target.getClass().getName()
                + ": "
                + method.getName()
                + ", cause: "
                + e.getTargetException().toString());
        throw e.getTargetException();
      } catch (Throwable e) {
        log.error(
            "fail: " + target.getClass().getName() + ": " + method.getName() + ", cause: " + e);
        throw e;
      }
    } else if (isTransactional && operationType == OperationType.WRITE) {
      try {
        OperationContext.set(operationType);
        log.info("WRITE start: " + target.getClass().getName() + ": " + method.getName() + " ...");
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);

        TransactionManager.createConnection(DatabaseType.POSTGRESQL, tenantIdentifier.value());
        DatabaseType databaseType = dialectProvider.provide(tenantIdentifier);
        TransactionManager.closeConnection();

        TransactionManager.beginTransaction(databaseType, tenantIdentifier.value());

        log.debug(
            databaseType.name()
                + ": begin transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName());

        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();
        log.debug(
            databaseType.name()
                + ": commit transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName());

        log.info("WRITE end: " + target.getClass().getName() + ": " + method.getName() + " ...");

        return result;
      } catch (InvocationTargetException e) {
        TransactionManager.rollbackTransaction();
        log.error(
            "rollback transaction (InvocationTargetException): "
                + target.getClass().getName()
                + ": "
                + method.getName()
                + ", cause: "
                + e.getTargetException().toString());
        throw e.getTargetException();
      } catch (Throwable e) {
        TransactionManager.rollbackTransaction();
        log.error(
            "rollback transaction: "
                + target.getClass().getName()
                + ": "
                + method.getName()
                + ", cause: "
                + e);
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
  public static <T> T createProxy(T target, Class<T> interfaceType, DialectProvider provider) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new TenantAwareEntryServiceProxy(target, provider));
  }
}
