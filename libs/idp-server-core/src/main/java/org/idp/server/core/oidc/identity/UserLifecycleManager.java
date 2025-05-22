/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.exception.UnSupportedException;

public class UserLifecycleManager {

  private static final Map<UserStatus, Set<UserStatus>> allowedTransitions =
      Map.of(
          UserStatus.UNREGISTERED, EnumSet.of(UserStatus.REGISTERED),
          UserStatus.REGISTERED,
              EnumSet.of(
                  UserStatus.IDENTITY_VERIFIED, UserStatus.CONTACT_VERIFIED, UserStatus.DELETED),
          UserStatus.CONTACT_VERIFIED, EnumSet.of(UserStatus.IDENTITY_VERIFIED),
          UserStatus.IDENTITY_VERIFIED,
              EnumSet.of(
                  UserStatus.IDENTITY_VERIFIED,
                  UserStatus.LOCKED,
                  UserStatus.DISABLED,
                  UserStatus.SUSPENDED,
                  UserStatus.DEACTIVATED),
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
