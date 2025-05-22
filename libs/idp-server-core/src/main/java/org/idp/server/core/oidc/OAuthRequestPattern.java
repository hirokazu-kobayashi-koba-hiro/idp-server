/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

/** OAuthRequestPattern */
public enum OAuthRequestPattern {
  NORMAL,
  REQUEST_OBJECT,
  REQUEST_URI,
  PUSHED_REQUEST_URI;

  public boolean isRequestParameter() {
    return this == REQUEST_OBJECT || this == REQUEST_URI;
  }

  public boolean isPushed() {
    return this == PUSHED_REQUEST_URI;
  }
}
