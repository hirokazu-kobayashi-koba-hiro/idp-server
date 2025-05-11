package org.idp.server.control_plane.management.onboarding.io;

import java.util.Map;

public class OnboardingResponse {

  OnboardingStatus status;
  Map<String, Object> contents;

  public OnboardingResponse(OnboardingStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public OnboardingStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isOk() {
    return this.status.isOk();
  }
}
