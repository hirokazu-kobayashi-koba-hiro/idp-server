/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.notification.exception;

import org.idp.server.platform.exception.InternalServerErrorException;

public class EmailSendingInternalServerErrorException extends InternalServerErrorException {
  public EmailSendingInternalServerErrorException(String message) {
    super(message);
  }

  public EmailSendingInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
