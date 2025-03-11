package org.idp.server.infrastructure.datasource.user;

import java.util.List;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.user.UserNotFoundException;
import org.idp.server.domain.model.user.UserRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserDataSource implements UserRepository {

  UserMapper mapper;
  JsonConverter jsonConverter;

  public UserDataSource(UserMapper mapper) {
    this.mapper = mapper;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, User user) {
    mapper.insert(tenant, user);
  }

  @Override
  public User find(String userId) {
    User user = mapper.select(userId);
    if (Objects.isNull(user)) {
      return new User();
    }
    return user;
  }

  @Override
  public User get(String userId) {
    User user = mapper.select(userId);
    if (Objects.isNull(user)) {
      throw new UserNotFoundException(String.format("not found user (%s)", userId));
    }
    return user;
  }

  @Override
  public User findBy(Tenant tenant, String email, String providerId) {
    User user = mapper.selectBy(tenant, email, providerId);
    if (Objects.isNull(user)) {
      return new User();
    }
    return user;
  }

  @Override
  public List<User> findList(Tenant tenant, int limit, int offset) {
    List<User> userList = mapper.selectList(tenant, limit, offset);
    if (Objects.isNull(userList)) {
      return List.of();
    }
    return userList;
  }

  @Override
  public void update(User user) {
    String customPropertiesString = jsonConverter.write(user.customPropertiesValue());
    mapper.update(user, customPropertiesString);
  }

  @Override
  public User findByProvider(String tokenIssuer, String providerId, String providerUserId) {
    User user = mapper.selectByProvider(tokenIssuer, providerId, providerUserId);

    if (Objects.isNull(user)) {
      return new User();
    }

    return user;
  }
}
