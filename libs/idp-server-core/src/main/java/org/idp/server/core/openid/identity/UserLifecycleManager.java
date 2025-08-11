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

package org.idp.server.core.openid.identity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.exception.UnSupportedException;

public class UserLifecycleManager {

  private static final Map<UserStatus, Set<UserStatus>> allowedTransitions =
      Map.of(
          UserStatus.UNREGISTERED, EnumSet.of(UserStatus.REGISTERED),
          UserStatus.REGISTERED, EnumSet.of(UserStatus.IDENTITY_VERIFIED, UserStatus.DELETED),
          UserStatus.IDENTITY_VERIFIED,
              EnumSet.of(
                  UserStatus.IDENTITY_VERIFIED,
                  UserStatus.LOCKED,
                  UserStatus.DISABLED,
                  UserStatus.SUSPENDED,
                  UserStatus.DEACTIVATED),
          UserStatus.IDENTITY_VERIFICATION_REQUIRED, EnumSet.of(UserStatus.IDENTITY_VERIFIED),
          UserStatus.LOCKED, EnumSet.of(UserStatus.IDENTITY_VERIFIED, UserStatus.REGISTERED),
          UserStatus.DISABLED, EnumSet.of(UserStatus.IDENTITY_VERIFIED, UserStatus.REGISTERED),
          UserStatus.SUSPENDED, EnumSet.of(UserStatus.IDENTITY_VERIFIED, UserStatus.REGISTERED),
          UserStatus.DEACTIVATED, EnumSet.of(UserStatus.DELETED_PENDING, UserStatus.REGISTERED),
          UserStatus.DELETED_PENDING, EnumSet.of(UserStatus.DELETED, UserStatus.REGISTERED));

  public static boolean canTransit(UserStatus from, UserStatus to) {
    Set<UserStatus> nextStatuses = allowedTransitions.getOrDefault(from, Set.of());
    return nextStatuses.contains(to);
  }

  public static UserStatus transit(UserStatus from, UserStatus to) {
    if (!canTransit(from, to)) {
      throw new UnSupportedException("Transition from " + from + " to " + to + " is not allowed.");
    }
    return to;
  }
}
