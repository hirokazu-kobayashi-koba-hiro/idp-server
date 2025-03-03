package org.idp.server.application.service.user.internal;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public void register(Tenant tenant, User user) {
    userRepository.register(tenant, user);
  }

  public User findBy(Tenant tenant, String email) {
    return userRepository.findBy(tenant, email);
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
