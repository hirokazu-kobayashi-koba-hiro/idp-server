package org.idp.sample.user;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public void register(TokenIssuer tokenIssuer, User user) {
    userRepository.register(tokenIssuer, user);
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
}
