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
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.log.TenantLoggingContext;
import org.idp.server.platform.multi_tenancy.tenant.MissingRequiredTenantIdentifierException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Tenant-aware entry service proxy with RLS (Row-Level Security) support.
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
 *   <li>RLS setup: {@code SET app.tenant_id} for PostgreSQL row-level security
 *   <li>Transaction management (commit/rollback)
 *   <li>Tenant logging context
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Application Plane API
 * this.oAuthFlowApi = TenantAwareEntryServiceProxy.createProxy(
 *     new OAuthFlowEntryService(...), OAuthFlowApi.class, databaseTypeProvider);
 *
 * // System-level Control Plane API
 * this.clientManagementApi = TenantAwareEntryServiceProxy.createProxy(
 *     new ClientManagementEntryService(...), ClientManagementApi.class, databaseTypeProvider);
 * }</pre>
 *
 * @see ManagementTypeEntryServiceProxy for Organization-level Control Plane APIs
 */
public class TenantAwareEntryServiceProxy implements InvocationHandler {
  protected final Object target;
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
        log.trace(
            "Method not found for transaction annotation lookup: class={}, method={}, error={}",
            target.getClass().getSimpleName(),
            method.getName(),
            e.getMessage());
      }
    }
    if (tx == null) {
      tx = target.getClass().getAnnotation(Transaction.class);
    }
    boolean readOnly = tx != null && tx.readOnly();
    OperationType operationType = readOnly ? OperationType.READ : OperationType.WRITE;

    if (isTransactional && operationType == OperationType.READ) {
      long startTime = System.currentTimeMillis();
      try {
        OperationContext.set(operationType);
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
        TenantLoggingContext.setTenant(tenantIdentifier);

        resolveUserContext(args);

        String clientId = resolveClientIdAsString(args);
        if (clientId != null) {
          TenantLoggingContext.setClientId(clientId);
        }

        log.trace(
            "Transaction started: operation={}, service={}, method={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName());

        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.createConnection(databaseType, tenantIdentifier);
        Object result = method.invoke(target, args);

        TransactionManager.closeConnection();

        long duration = System.currentTimeMillis() - startTime;
        if (duration > 1000) {
          log.warn(
              "Long-running transaction completed: operation={}, service={}, method={}, duration={}ms",
              operationType,
              target.getClass().getSimpleName(),
              method.getName(),
              duration);
        }

        return result;
      } catch (InvocationTargetException e) {
        log.trace(
            "Transaction failed: operation={}, service={}, method={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName());
        throw e.getTargetException();
      } catch (Throwable e) {
        log.error(
            "Transaction failed: operation={}, service={}, method={}, cause={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName(),
            e.getMessage(),
            e);
        throw e;
      } finally {
        TenantLoggingContext.clearAll();
        TransactionManager.closeConnection();
      }
    } else if (isTransactional && operationType == OperationType.WRITE) {
      long startTime = System.currentTimeMillis();
      try {
        OperationContext.set(operationType);
        TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
        TenantLoggingContext.setTenant(tenantIdentifier);

        resolveUserContext(args);

        String clientId = resolveClientIdAsString(args);
        if (clientId != null) {
          TenantLoggingContext.setClientId(clientId);
        }

        DatabaseType databaseType = applicationDatabaseTypeProvider.provide();
        TransactionManager.beginTransaction(databaseType, tenantIdentifier);

        log.trace(
            "Transaction started: operation={}, service={}, method={}, db_type={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName(),
            databaseType.name());

        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();

        long duration = System.currentTimeMillis() - startTime;
        log.trace(
            "Transaction committed: operation={}, service={}, method={}, db_type={}, duration={}ms",
            operationType,
            target.getClass().getSimpleName(),
            method.getName(),
            databaseType.name(),
            duration);

        return result;
      } catch (InvocationTargetException e) {
        TransactionManager.rollbackTransaction();
        log.trace(
            "Transaction rollback: operation={}, service={}, method={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName());
        throw e.getTargetException();
      } catch (Throwable e) {
        TransactionManager.rollbackTransaction();
        log.error(
            "Transaction rollback: operation={}, service={}, method={}, cause={}",
            operationType,
            target.getClass().getSimpleName(),
            method.getName(),
            e.getMessage(),
            e);
        throw e;
      } finally {
        TenantLoggingContext.clearAll();
        TransactionManager.closeConnection();
      }
    } else {
      return method.invoke(target, args);
    }
  }

  protected TenantIdentifier resolveTenantIdentifier(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof TenantIdentifier tenantId) {
        return tenantId;
      }
    }
    throw new MissingRequiredTenantIdentifierException(
        "Missing required TenantIdentifier. Please ensure it is explicitly passed to the service.");
  }

  protected void resolveUserContext(Object[] args) {
    for (Object arg : args) {
      // Type-safe check using direct import (no reflection needed in use-cases layer)
      if (arg instanceof User user) {
        if (user.exists()) {
          String sub = user.sub();
          if (sub != null && !sub.isEmpty()) {
            TenantLoggingContext.setUserId(sub);
          }
          String exSub = user.externalUserId();
          if (exSub != null && !exSub.isEmpty()) {
            TenantLoggingContext.setUserExSub(exSub);
          }
          String preferredUsername = user.preferredUsername();
          if (preferredUsername != null && !preferredUsername.isEmpty()) {
            TenantLoggingContext.setUserName(preferredUsername);
          }
        }
      }
    }
  }

  protected String resolveClientIdAsString(Object[] args) {
    for (Object arg : args) {
      // Extract from Map<String, String[]> params
      if (arg instanceof java.util.Map<?, ?> params) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, String[]> paramMap = (java.util.Map<String, String[]>) params;
        String[] clientIds = paramMap.get("client_id");
        if (clientIds != null && clientIds.length > 0 && !clientIds[0].isEmpty()) {
          return clientIds[0];
        }
      }

      // Extract from Authorization header (Basic auth)
      if (arg instanceof String authHeader && authHeader.startsWith("Basic ")) {
        return extractClientIdFromBasicAuth(authHeader);
      }
    }
    return null;
  }

  private String extractClientIdFromBasicAuth(String authHeader) {
    try {
      String basic = authHeader.substring(6);
      String decoded = new String(java.util.Base64.getDecoder().decode(basic));
      String[] parts = decoded.split(":", 2);
      if (parts.length > 0 && !parts[0].isEmpty()) {
        return parts[0];
      }
    } catch (Exception e) {
      log.debug("Failed to extract client_id from Basic auth header: {}", e.getMessage());
    }
    return null;
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
