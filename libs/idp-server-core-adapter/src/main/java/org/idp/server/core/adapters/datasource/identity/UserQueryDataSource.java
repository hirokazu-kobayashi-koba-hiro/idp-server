package org.idp.server.core.adapters.datasource.identity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.exception.UserNotFoundException;
import org.idp.server.core.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.datasource.SqlTooManyResultsException;

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

    return ModelConverter.convert(result);
  }

  @Override
  public User findById(Tenant tenant, UserIdentifier userIdentifier) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return User.notFound();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public User findByEmail(Tenant tenant, String email, String providerId) {
    try {
      UserSqlExecutor executor = executors.get(tenant.databaseType());
      Map<String, String> result = executor.selectByEmail(tenant, email, providerId);

      if (Objects.isNull(result) || result.isEmpty()) {
        return new User();
      }

      return ModelConverter.convert(result);
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

      return ModelConverter.convert(result);
    } catch (SqlTooManyResultsException exception) {

      throw new UserTooManyFoundResultException(exception.getMessage());
    }
  }

  @Override
  public List<User> findList(Tenant tenant, int limit, int offset) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

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

    return ModelConverter.convert(result);
  }

  @Override
  public User findByAuthenticationDevice(Tenant tenant, String deviceId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectByAuthenticationDevice(tenant, deviceId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    return ModelConverter.convert(result);
  }
}
