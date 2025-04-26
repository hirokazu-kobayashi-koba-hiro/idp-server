package org.idp.server.adapters.springboot;

import org.idp.server.core.identity.PasswordVerificationDelegation;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordVerification implements PasswordVerificationDelegation {

  BCryptPasswordEncoder bCryptPasswordEncoder;

  public PasswordVerification(BCryptPasswordEncoder bCryptPasswordEncoder) {
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
  }

  @Override
  public boolean verify(String rawPassword, String encodedPassword) {
    return bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
  }
}
