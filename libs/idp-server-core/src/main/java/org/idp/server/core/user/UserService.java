package org.idp.server.core.user;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public class UserService {

  UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public void register(Tenant tenant, User user) {
    userRepository.register(tenant, user);
  }

  public User findBy(Tenant tenant, String email, String providerId) {
    return userRepository.findBy(tenant, email, providerId);
  }

  public User findByProvider(String tokenIssuer, String providerId, String providerUserId) {

    return userRepository.findByProvider(tokenIssuer, providerId, providerUserId);
  }

  public User get(String userId) {
    return userRepository.get(userId);
  }

  public boolean authenticate(User user, String password) {
    return true;
  }

  public List<User> find(Tenant tenant, int limit, int offset) {
    return userRepository.findList(tenant, limit, offset);
  }

  public void update(User user) {
    userRepository.update(user);
  }
}
