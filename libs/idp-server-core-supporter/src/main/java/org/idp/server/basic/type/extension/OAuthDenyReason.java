/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.extension;

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
