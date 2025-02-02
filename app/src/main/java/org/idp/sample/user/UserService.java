package org.idp.sample.user;

import org.idp.sample.Tenant;
import org.idp.server.oauth.identity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
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

  public User find(String userId) {
    return userRepository.find(userId);
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
}
