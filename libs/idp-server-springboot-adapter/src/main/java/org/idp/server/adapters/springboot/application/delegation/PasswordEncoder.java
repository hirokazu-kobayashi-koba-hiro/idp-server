package org.idp.server.adapters.springboot.application.delegation;

import org.idp.server.core.oidc.identity.authentication.PasswordEncodeDelegation;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncoder implements PasswordEncodeDelegation {

  BCryptPasswordEncoder bCryptPasswordEncoder;

  public PasswordEncoder(BCryptPasswordEncoder bCryptPasswordEncoder) {
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
  }

  @Override
  public String encode(String rawPassword) {
    return bCryptPasswordEncoder.encode(rawPassword);
  }
}
