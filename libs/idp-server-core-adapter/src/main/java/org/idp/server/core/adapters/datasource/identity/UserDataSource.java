package org.idp.server.core.adapters.datasource.identity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.identity.exception.UserNotFoundException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UserDataSource implements UserRepository {

  JsonConverter jsonConverter;
  UserSqlExecutors executors;

  public UserDataSource() {
    this.executors = new UserSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, User user) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, user);
  }

  @Override
  public User get(Tenant tenant, String userId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, userId);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new UserNotFoundException(String.format("not found user (%s)", userId));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public User findByEmail(Tenant tenant, String email, String providerId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectByEmail(tenant, email, providerId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public User findByPhone(Tenant tenant, String phone, String providerId) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectByPhone(tenant, phone, providerId);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    return ModelConverter.convert(result);
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
  public void update(Tenant tenant, User user) {
    UserSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, user);
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
