package org.idp.server.type.extension;

public enum OAuthDenyReason {
  access_denied("The resource owner or authorization server denied the request.");

  String errorDescription;

  OAuthDenyReason(String errorDescription) {
    this.errorDescription = errorDescription;
  }

  public String errorDescription() {
    return errorDescription;
  }
}
