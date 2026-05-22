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

package org.idp.server.core.adapters.datasource.identity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.exception.UserNotFoundException;
import org.idp.server.core.openid.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.SqlTooManyResultsException;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.policy.UserAttributeLoadRule;

public class UserQueryDataSource implements UserQueryRepository {

  UserSqlExecutor executor;
  CacheStore cacheStore;

  public UserQueryDataSource(UserSqlExecutor executor) {
    this(executor, new NoOperationCacheStore());
  }

  public UserQueryDataSource(UserSqlExecutor executor, CacheStore cacheStore) {
    this.executor = executor;
    this.cacheStore = cacheStore;
  }

  @Override
  public User get(Tenant tenant, UserIdentifier userIdentifier) {
    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new UserNotFoundException(String.format("not found user (%s)", userIdentifier.value()));
    }

    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findById(Tenant tenant, UserIdentifier userIdentifier) {
    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findByExternalIdpSubject(
      Tenant tenant, String externalIdpSubject, String providerId) {
    try {
      Map<String, String> result =
          executor.selectByExternalIdpSubject(tenant, externalIdpSubject, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByName(Tenant tenant, String name, String providerId) {
    try {
      Map<String, String> result = executor.selectByName(tenant, name, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String providerId) {
    try {
      Map<String, String> result = executor.selectByDeviceId(tenant, deviceId, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByEmail(Tenant tenant, String email, String providerId) {
    try {
      Map<String, String> result = executor.selectByEmail(tenant, email, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByPhone(Tenant tenant, String phone, String providerId) {
    try {
      Map<String, String> result = executor.selectByPhone(tenant, phone, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public long findTotalCount(Tenant tenant, UserQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<User> findList(Tenant tenant, UserQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }

  @Override
  public User findByProvider(Tenant tenant, String providerId, String providerUserId) {
    Map<String, String> result = executor.selectByProvider(tenant, providerId, providerUserId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findByAuthenticationDevice(Tenant tenant, String deviceId) {
    Map<String, String> result = executor.selectByAuthenticationDevice(tenant, deviceId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findByPreferredUsername(Tenant tenant, String providerId, String preferredUsername) {
    try {
      Map<String, String> result =
          executor.selectByPreferredUsername(tenant, providerId, preferredUsername);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByPreferredUsernameNoProvider(Tenant tenant, String preferredUsername) {
    try {
      Map<String, String> result =
          executor.selectByPreferredUsernameNoProvider(tenant, preferredUsername);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByFidoCredentialId(Tenant tenant, String credentialId) {
    try {
      Map<String, String> result = executor.selectByFidoCredentialId(tenant, credentialId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return User.notFound();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public UserStatus findStatusById(Tenant tenant, UserIdentifier userIdentifier) {
    String key = statusKey(tenant.identifier(), userIdentifier);
    Optional<UserStatus> cached = cacheStore.find(key, UserStatus.class);
    if (cached.isPresent()) {
      return cached.get();
    }

    Map<String, String> result = executor.selectStatus(tenant, userIdentifier);
    if (Objects.isNull(result) || result.isEmpty()) {
      return null;
    }

    String statusName = result.get("status");
    if (statusName == null) {
      return null;
    }

    UserStatus status = UserStatus.valueOf(statusName);
    cacheStore.put(key, status);
    return status;
  }

  public static String statusKey(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":UserStatus:" + userIdentifier.value();
  }

  private User collectAssignedDataAndConvert(
      Tenant tenant,
      UserIdentifier userIdentifier,
      UserSqlExecutor executor,
      Map<String, String> result) {
    HashMap<String, String> mergedResult = new HashMap<>(result);

    UserAttributeLoadRule loadRule = tenant.userAttributeLoadRule();

    if (loadRule.includeAssignedOrganizations()) {
      Map<String, String> assignedOrganization =
          executor.selectAssignedOrganization(tenant, userIdentifier);
      mergedResult.putAll(assignedOrganization);
    }

    if (loadRule.includeAssignedTenants()) {
      Map<String, String> assignedTenant = executor.selectAssignedTenant(tenant, userIdentifier);
      mergedResult.putAll(assignedTenant);
    }

    return ModelConverter.convert(mergedResult);
  }
}
