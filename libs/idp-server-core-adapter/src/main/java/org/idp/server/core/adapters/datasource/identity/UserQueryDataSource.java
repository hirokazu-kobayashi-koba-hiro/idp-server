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
import java.util.stream.Collectors;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.UserQueries;
import org.idp.server.core.oidc.identity.exception.UserNotFoundException;
import org.idp.server.core.oidc.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.SqlTooManyResultsException;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserQueryDataSource implements UserQueryRepository {

  JsonConverter jsonConverter;
  UserSqlExecutors executors;

  public UserQueryDataSource() {
    this.executors = new UserSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public User get(Tenant tenant, UserIdentifier userIdentifier) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new UserNotFoundException(String.format("not found user (%s)", userIdentifier.value()));
    }

    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findById(Tenant tenant, UserIdentifier userIdentifier) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    HashMap<String, String> mergedResult = new HashMap<>(result);
    Map<String, String> assignedOrganization =
        executor.selectAssignedOrganization(tenant, userIdentifier);
    Map<String, String> assignedTenant = executor.selectAssignedTenant(tenant, userIdentifier);
    mergedResult.putAll(assignedOrganization);
    mergedResult.putAll(assignedTenant);

    return ModelConverter.convert(mergedResult);
  }

  @Override
  public User findByExternalIdpSubject(
      Tenant tenant, String externalIdpSubject, String providerId) {
    try {
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result =
          executor.selectByExternalIdpSubject(tenant, externalIdpSubject, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
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
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result = executor.selectByName(tenant, name, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public User findByDeviceId(Tenant tenant, String deviceId, String providerId) {
    try {
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result = executor.selectByDeviceId(tenant, deviceId, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
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
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result = executor.selectByEmail(tenant, email, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
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
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result = executor.selectByPhone(tenant, phone, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
      }

      UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
      return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public long findTotalCount(Tenant tenant, UserQueries queries) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<User> findList(Tenant tenant, UserQueries queries) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }

  @Override
  public User findByProvider(Tenant tenant, String providerId, String providerUserId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectByProvider(tenant, providerId, providerUserId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  @Override
  public User findByAuthenticationDevice(Tenant tenant, String deviceId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectByAuthenticationDevice(tenant, deviceId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    UserIdentifier userIdentifier = ModelConverter.extractUserIdentifier(result);
    return collectAssignedDataAndConvert(tenant, userIdentifier, executor, result);
  }

  private User collectAssignedDataAndConvert(
      Tenant tenant,
      UserIdentifier userIdentifier,
      UserSqlExecutor executor,
      Map<String, String> result) {
    HashMap<String, String> mergedResult = new HashMap<>(result);
    Map<String, String> assignedOrganization =
        executor.selectAssignedOrganization(tenant, userIdentifier);
    Map<String, String> assignedTenant = executor.selectAssignedTenant(tenant, userIdentifier);
    mergedResult.putAll(assignedOrganization);
    mergedResult.putAll(assignedTenant);

    return ModelConverter.convert(mergedResult);
  }
}
