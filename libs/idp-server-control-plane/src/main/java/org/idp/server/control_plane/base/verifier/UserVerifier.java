/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserVerifier {
  UserQueryRepository userQueryRepository;

  public UserVerifier(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  public VerificationResult verify(Tenant tenant, User user) {
    List<String> errors = new ArrayList<>();

    User byId = userQueryRepository.findById(tenant, user.userIdentifier());
    if (byId.exists()) {
      errors.add("User id is already exists");
    }

    User byEmail = userQueryRepository.findByEmail(tenant, user.email(), user.providerId());
    if (byEmail.exists()) {
      errors.add("User email is already exists");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
