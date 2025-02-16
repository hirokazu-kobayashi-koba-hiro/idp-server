package org.idp.sample.infrastructure.datasource.user;

import java.util.List;
import java.util.Objects;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.user.UserRepository;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.oauth.identity.User;
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
      throw new RuntimeException("not found user");
    }
    return user;
  }

  @Override
  public User findBy(Tenant tenant, String email) {
    User user = mapper.selectBy(tenant, email);
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
}
