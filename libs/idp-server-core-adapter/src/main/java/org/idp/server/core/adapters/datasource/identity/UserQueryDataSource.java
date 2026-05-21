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
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.exception.UserNotFoundException;
import org.idp.server.core.openid.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.SqlTooManyResultsException;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.policy.UserAttributeLoadRule;

public class UserQueryDataSource implements UserQueryRepository {

  UserSqlExecutor executor;
  CacheStore cacheStore;

  public UserQueryDataSource(UserSqlExecutor executor, CacheStore cacheStore) {
    this.executor = executor;
    this.cacheStore = cacheStore;
  }

  @Override
  public User get(Tenant tenant, UserIdentifier userIdentifier) {
    Optional<User> cached = findInCache(tenant.identifier(), userIdentifier);
    if (cached.isPresent()) {
      return cached.get();
    }

    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new UserNotFoundException(String.format("not found user (%s)", userIdentifier.value()));
    }

    User user = collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    putInCache(tenant.identifier(), user);
    return user;
  }

  @Override
  public User findById(Tenant tenant, UserIdentifier userIdentifier) {
    Optional<User> cached = findInCache(tenant.identifier(), userIdentifier);
    if (cached.isPresent()) {
      return cached.get();
    }

    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    User user = collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    putInCache(tenant.identifier(), user);
    return user;
  }

  @Override
  public User findByExternalIdpSubject(
      Tenant tenant, String externalIdpSubject, String providerId) {
    try {
      Map<String, String> result =
          executor.selectByExternalIdpSubject(tenant, externalIdpSubject, providerId);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByName(Tenant tenant, String name, String providerId) {
    try {
      Map<String, String> result = executor.selectByName(tenant, name, providerId);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String providerId) {
    try {
      Map<String, String> result = executor.selectByDeviceId(tenant, deviceId, providerId);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByEmail(Tenant tenant, String email, String providerId) {
    try {
      Map<String, String> result = executor.selectByEmail(tenant, email, providerId);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByPhone(Tenant tenant, String phone, String providerId) {
    try {
      Map<String, String> result = executor.selectByPhone(tenant, phone, providerId);
      return convertAndCache(tenant, result);
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
    return convertAndCache(tenant, result);
  }

  @Override
  public User findByAuthenticationDevice(Tenant tenant, String deviceId) {
    Map<String, String> result = executor.selectByAuthenticationDevice(tenant, deviceId);
    return convertAndCache(tenant, result);
  }

  @Override
  public User findByPreferredUsername(Tenant tenant, String providerId, String preferredUsername) {
    try {
      Map<String, String> result =
          executor.selectByPreferredUsername(tenant, providerId, preferredUsername);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByPreferredUsernameNoProvider(Tenant tenant, String preferredUsername) {
    try {
      Map<String, String> result =
          executor.selectByPreferredUsernameNoProvider(tenant, preferredUsername);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByFidoCredentialId(Tenant tenant, String credentialId) {
    try {
      Map<String, String> result = executor.selectByFidoCredentialId(tenant, credentialId);
      return convertAndCache(tenant, result);
    } catch (SqlTooManyResultsException exception) {
      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  private User convertAndCache(Tenant tenant, Map<String, String> result) {
    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
    User user = collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    putInCache(tenant.identifier(), user);
    return user;
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

  private Optional<User> findInCache(
      TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier) {
    return cacheStore.find(userKey(tenantIdentifier, userIdentifier), User.class);
  }

  private void putInCache(TenantIdentifier tenantIdentifier, User user) {
    if (user == null || !user.exists()) {
      return;
    }
    cacheStore.put(userKey(tenantIdentifier, new UserIdentifier(user.sub())), user);
  }

  /**
   * Cache key for User by primary identifier (UUID).
   *
   * <p>Used by both {@link UserQueryDataSource} (read) and {@code UserCommandDataSource}
   * (invalidate on write). Lookups by other keys (email / phone / device_id etc.) hit DB once, then
   * their result is cached under this UUID key so that subsequent UUID-based accesses within the
   * same flow (FIDO authentication, token introspection, etc.) hit the cache.
   */
  public static String userKey(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + User.class.getSimpleName()
        + ":"
        + userIdentifier.value();
  }
}
