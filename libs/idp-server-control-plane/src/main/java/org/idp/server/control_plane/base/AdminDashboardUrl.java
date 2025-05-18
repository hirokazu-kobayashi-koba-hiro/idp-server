package org.idp.server.control_plane.base;

public class AdminDashboardUrl {
  String value;

  public AdminDashboardUrl() {}

  public AdminDashboardUrl(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
