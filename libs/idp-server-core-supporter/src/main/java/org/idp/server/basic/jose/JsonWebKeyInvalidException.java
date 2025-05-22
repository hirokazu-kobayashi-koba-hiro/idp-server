/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

/** JwkInvalidException */
public class JsonWebKeyInvalidException extends Exception {
  public JsonWebKeyInvalidException(String message) {
    super(message);
  }

  public JsonWebKeyInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
