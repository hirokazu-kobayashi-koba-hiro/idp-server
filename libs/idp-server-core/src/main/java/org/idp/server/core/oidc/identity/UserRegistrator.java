/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity;

import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrator {

  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;

  public UserRegistrator(
      UserQueryRepository userQueryRepository, UserCommandRepository userCommandRepository) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userQueryRepository.findByEmail(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      UserUpdater userUpdater = new UserUpdater(user, existingUser);
      User updatedUser = userUpdater.update();
      userCommandRepository.update(tenant, updatedUser);
      return updatedUser;
    }

    userCommandRepository.register(tenant, user);

    return user;
  }
}
