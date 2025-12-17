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

package org.idp.server.usecases;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Management-type entry service proxy for Organization-level Control Plane APIs.
 *
 * <h2>Proxy Selection Guide</h2>
 *
 * <pre>
 * ┌─────────────────────────────────┬─────────────────────────────────┐
 * │ Layer                           │ Proxy                           │
 * ├─────────────────────────────────┼─────────────────────────────────┤
 * │ Application Plane               │ TenantAwareEntryServiceProxy    │
 * │ System-level Control Plane      │ TenantAwareEntryServiceProxy    │
 * │ Organization-level Control Plane│ ManagementTypeEntryServiceProxy │
 * └─────────────────────────────────┴─────────────────────────────────┘
 * </pre>
 *
 * <h2>Features</h2>
 *
 * <ul>
 *   <li>Transaction management (commit/rollback)
 *   <li>No RLS setup
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Organization-level Control Plane API
 * this.orgRoleManagementApi = ManagementTypeEntryServiceProxy.createProxy(
 *     new OrgRoleManagementEntryService(...), OrgRoleManagementApi.class, databaseTypeProvider);
 *
 * this.organizationTenantResolverApi = ManagementTypeEntryServiceProxy.createProxy(
 *     new OrganizationTenantResolverEntryService(...), OrganizationTenantResolverApi.class, databaseTypeProvider);
 * }</pre>
 *
 * @see TenantAwareEntryServiceProxy for Application Plane and System-level Control Plane APIs
 */
public class ManagementTypeEntryServiceProxy implements InvocationHandler {

  private final Object target;
  private final ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider;
  private final LoggerWrapper log = LoggerWrapper.getLogger(ManagementTypeEntryServiceProxy.class);

  private ManagementTypeEntryServiceProxy(
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
            new ManagementTypeEntryServiceProxy(target, databaseTypeProvider));
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
