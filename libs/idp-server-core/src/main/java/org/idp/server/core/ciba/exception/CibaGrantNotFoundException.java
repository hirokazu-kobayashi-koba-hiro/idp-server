package org.idp.server.core.ciba.exception;

import org.idp.server.platform.exception.NotFoundException;

public class CibaGrantNotFoundException extends NotFoundException {
  public CibaGrantNotFoundException(String message) {
    super(message);
  }
}
