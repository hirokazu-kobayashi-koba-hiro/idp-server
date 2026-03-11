/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.identity.event;

import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserStatusLockExecutor implements UserLifecycleEventExecutor {

  UserCommandRepository userCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(UserStatusLockExecutor.class);

  public UserStatusLockExecutor(UserCommandRepository userCommandRepository) {
    this.userCommandRepository = userCommandRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.LOCK;
  }

  @Override
  public String name() {
    return "user-status-lock";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    User user = userLifecycleEvent.user();
    return user.canTransit(user.status(), UserStatus.LOCKED);
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      User user = userLifecycleEvent.user();
      UserStatus previousStatus = user.status();

      user.transitStatus(UserStatus.LOCKED);
      userCommandRepository.updateStatus(tenant, user);

      log.info("User status changed from {} to LOCKED: sub={}", previousStatus.name(), user.sub());

      return UserLifecycleEventResult.success(
          name(),
          Map.of(
              "sub", user.sub(),
              "previous_status", previousStatus.name(),
              "new_status", UserStatus.LOCKED.name()));
    } catch (Exception e) {
      log.error("Failed to lock user status: {}", e.getMessage(), e);
      return UserLifecycleEventResult.failure(name(), Map.of("error", e.getMessage()));
    }
  }
}
