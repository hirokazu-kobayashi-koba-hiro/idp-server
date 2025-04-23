package org.idp.server.core.ciba.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class CibaGrantNotFoundException extends NotFoundException {
  public CibaGrantNotFoundException(String message) {
    super(message);
  }
}
