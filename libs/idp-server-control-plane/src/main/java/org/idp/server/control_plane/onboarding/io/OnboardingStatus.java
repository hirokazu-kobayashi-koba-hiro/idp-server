package org.idp.server.control_plane.onboarding.io;

public enum OnboardingStatus {
  OK(200),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  SERVER_ERROR(500);

  int statusCode;

  OnboardingStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}
