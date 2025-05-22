/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.handler.io;

public class CibaAuthorizeResponse {
  CibaAuthorizeStatus status;

  public CibaAuthorizeResponse(CibaAuthorizeStatus status) {
    this.status = status;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
