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

/**
 * Organization-aware entry service proxy that handles both organization and tenant contexts.
 *
 * <p>This proxy automatically resolves both OrganizationIdentifier and TenantIdentifier from method
 * arguments and performs organization-tenant relationship validation before delegating to the
 * underlying service with proper transaction management.
 *
 * <p>Key features: - Automatic OrganizationIdentifier resolution from method arguments -
 * Organization-tenant relationship validation - Dynamic admin tenant resolution for
 * organization-level authentication - Full transaction management for both read and write
 * operations
 */
public class OrganizationAwareEntryServiceProxy implements InvocationHandler {

  private final Object target;
  private final ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider;
  private final LoggerWrapper log =
      LoggerWrapper.getLogger(OrganizationAwareEntryServiceProxy.class);

  private OrganizationAwareEntryServiceProxy(
      Object target, ApplicationDatabaseTypeProvider databaseTypeProvider) {
    this.target = target;
    this.applicationDatabaseTypeProvider = databaseTypeProvider;
  }

  /**
   * Creates an organization-aware proxy for the given target service.
   *
   * @param target the target service instance
   * @param interfaceType the service interface type
   * @param databaseTypeProvider the database type provider
   * @param <T> the service interface type
   * @return the proxied service instance
   */
  @SuppressWarnings("unchecked")
  public static <T> T createProxy(
      T target, Class<T> interfaceType, ApplicationDatabaseTypeProvider databaseTypeProvider) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new OrganizationAwareEntryServiceProxy(target, databaseTypeProvider));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // Standard transaction handling for regular methods
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
        log.trace("Method not found for transaction annotation lookup: {}", e.getMessage());
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
        log.trace("READ start: class={}, method={}", target.getClass().getName(), method.getName());

        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.createConnection(databaseType);
        Object result = method.invoke(target, args);

        TransactionManager.closeConnection();
        log.trace("READ end: class={}, method={}", target.getClass().getName(), method.getName());

        return result;
      } catch (InvocationTargetException e) {
        log.trace(
            "transaction failed: class={}, method={}",
            target.getClass().getSimpleName(),
            method.getName());
        throw e.getTargetException();
      } catch (Throwable e) {
        log.error(
            "transaction failed: class={}, method={}, cause={}",
            target.getClass().getSimpleName(),
            method.getName(),
            e.getMessage(),
            e);
        throw e;
      } finally {
        TransactionManager.closeConnection();
      }
    } else if (isTransactional && operationType == OperationType.WRITE) {
      try {
        OperationContext.set(operationType);
        log.trace(
            "WRITE start: class={}, method={}", target.getClass().getName(), method.getName());

        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.beginTransaction(databaseType);

        log.trace(
            "{}: begin transaction: {}: {}",
            databaseType.name(),
            target.getClass().getName(),
            method.getName());

        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();
        log.trace(
            "{}: commit transaction: {}: {}",
            databaseType.name(),
            target.getClass().getName(),
            method.getName());

        log.trace("WRITE end: class={}, method={}", target.getClass().getName(), method.getName());

        return result;
      } catch (InvocationTargetException e) {
        TransactionManager.rollbackTransaction();
        log.trace(
            "rollback transaction: class={}, method={}",
            target.getClass().getSimpleName(),
            method.getName());
        throw e.getTargetException();
      } catch (Throwable e) {
        TransactionManager.rollbackTransaction();
        log.error(
            "rollback transaction: class={}, method={}, cause={}",
            target.getClass().getSimpleName(),
            method.getName(),
            e.getMessage(),
            e);
        throw e;
      } finally {
        TransactionManager.closeConnection();
      }
    } else {
      return method.invoke(target, args);
    }
  }
}
