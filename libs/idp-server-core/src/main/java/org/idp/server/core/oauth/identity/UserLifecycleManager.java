package org.idp.server.core.oauth.identity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.type.exception.UnSupportedException;

public class UserLifecycleManager {

  private static final Map<UserStatus, Set<UserStatus>> allowedTransitions =
      Map.of(
          UserStatus.UNREGISTERED, EnumSet.of(UserStatus.REGISTERED),
          UserStatus.REGISTERED, EnumSet.of(UserStatus.VERIFIED, UserStatus.DELETED),
          UserStatus.VERIFIED, EnumSet.of(UserStatus.ACTIVE),
          UserStatus.ACTIVE,
              EnumSet.of(
                  UserStatus.LOCKED,
                  UserStatus.DISABLED,
                  UserStatus.SUSPENDED,
                  UserStatus.DEACTIVATED),
          UserStatus.LOCKED, EnumSet.of(UserStatus.ACTIVE),
          UserStatus.DISABLED, EnumSet.of(UserStatus.ACTIVE),
          UserStatus.SUSPENDED, EnumSet.of(UserStatus.ACTIVE),
          UserStatus.DEACTIVATED, EnumSet.of(UserStatus.DELETED_PENDING),
          UserStatus.DELETED_PENDING, EnumSet.of(UserStatus.DELETED));

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
