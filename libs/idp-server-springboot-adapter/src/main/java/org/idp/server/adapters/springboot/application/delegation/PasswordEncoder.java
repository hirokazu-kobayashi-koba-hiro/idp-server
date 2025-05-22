/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
