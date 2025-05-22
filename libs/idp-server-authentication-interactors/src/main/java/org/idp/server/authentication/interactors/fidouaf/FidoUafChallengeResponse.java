/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.fidouaf;

import java.io.Serializable;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class FidoUafChallengeResponse implements Serializable, JsonReadable {

  int statusCode;
  Map<String, Object> contents;

  public FidoUafChallengeResponse() {}

  public FidoUafChallengeResponse(int statusCode, Map<String, Object> contents) {
    this.statusCode = statusCode;
    this.contents = contents;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isSuccess() {
    return statusCode < 400;
  }

  public boolean isError() {
    return statusCode >= 400;
  }
}
