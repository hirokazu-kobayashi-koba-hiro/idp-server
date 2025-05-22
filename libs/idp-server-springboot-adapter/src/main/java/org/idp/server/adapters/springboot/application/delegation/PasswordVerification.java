/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.delegation;

import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;
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
