/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.response;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.basic.type.oauth.ErrorResponseCreatable;

public class BackchannelAuthenticationErrorResponse implements ErrorResponseCreatable {
  Error error;
  ErrorDescription errorDescription;

  public BackchannelAuthenticationErrorResponse() {}

  public BackchannelAuthenticationErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }

  public String contents() {
    return toErrorResponse(error, errorDescription);
  }
}
