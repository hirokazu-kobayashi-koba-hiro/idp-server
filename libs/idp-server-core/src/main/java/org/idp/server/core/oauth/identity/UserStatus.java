package org.idp.server.core.oauth.identity;

public enum UserStatus {
  UNREGISTERED("Account has not been created"),
  REGISTERED("Registered but email not verified"),
  VERIFIED("Email verified, ready to activate"),
  ACTIVE("Account is active and usable"),
  LOCKED("Temporarily locked due to failures"),
  DISABLED("Disabled by user or admin"),
  SUSPENDED("Suspended due to policy violations"),
  DEACTIVATED("Deactivation requested, in grace period"),
  DELETED_PENDING("Pending deletion after grace period"),
  DELETED("Permanently deleted"),
  FEDERATED_ONLY("Login via external IdP only");

  final String description;

  UserStatus(String description) {
    this.description = description;
  }

  public String description() {
    return description;
  }

  public static UserStatus of(String string) {
    for (UserStatus userStatus : UserStatus.values()) {
      if (userStatus.name().equalsIgnoreCase(string)) {
        return userStatus;
      }
    }
    return UNREGISTERED;
  }
}
