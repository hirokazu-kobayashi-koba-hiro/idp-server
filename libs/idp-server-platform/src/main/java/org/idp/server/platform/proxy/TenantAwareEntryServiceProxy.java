/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.MissingRequiredTenantIdentifierException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantAwareEntryServiceProxy implements InvocationHandler {
  private final Object target;
  private final ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider;
  LoggerWrapper log = LoggerWrapper.getLogger(TenantAwareEntryServiceProxy.class);

  public TenantAwareEntryServiceProxy(
      Object target, ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider) {
    this.target = target;
    this.applicationDatabaseTypeProvider = applicationDatabaseTypeProvider;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    boolean isTransactional =
        method.isAnnotationPresent(Transaction.class)
            || target.getClass().isAnnotationPresent(Transaction.class);

    Transaction tx = method.getClass().getAnnotation(Transaction.class);
    if (tx == null) {
      try {
        Method implMethod =
            target.getClass().getMethod(method.getName(), method.getParameterTypes());
        tx = implMethod.getAnnotation(Transaction.class);
      } catch (NoSuchMethodException e) {
        log.error(e.getMessage(), e);
      }
    }
    if (tx == null) {
      tx = target.getClass().getAnnotation(Transaction.class);
    }
    boolean readOnly = tx != null && tx.readOnly();
    OperationType operationType = readOnly ? OperationType.READ : OperationType.WRITE;

    if (isTransactional && operationType == OperationType.READ) {
      try {
        OperationContext.set(operationType);
        log.debug("READ start: " + target.getClass().getName() + ": " + method.getName() + " ...");
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.createConnection(databaseType, tenantIdentifier);
        Object result = method.invoke(target, args);

        TransactionManager.closeConnection();
        log.debug("READ end: " + target.getClass().getName() + ": " + method.getName() + " ...");

        return result;
      } catch (InvocationTargetException e) {
        log.warn(
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
      } finally {
        TransactionManager.closeConnection();
      }
    } else if (isTransactional && operationType == OperationType.WRITE) {
      try {
        OperationContext.set(operationType);
        log.debug("WRITE start: " + target.getClass().getName() + ": " + method.getName() + " ...");
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);

        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.beginTransaction(databaseType, tenantIdentifier);

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

        log.debug("WRITE end: " + target.getClass().getName() + ": " + method.getName() + " ...");

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
      } finally {
        TransactionManager.closeConnection();
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
      T target, Class<T> interfaceType, ApplicationDatabaseTypeProvider provider) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new TenantAwareEntryServiceProxy(target, provider));
  }
}
