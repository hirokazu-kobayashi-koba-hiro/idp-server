package org.idp.server.handler.oauth.datasource.memory;

/** IdpServerFailedInitializationException */
public class IdpServerFailedInitializationException extends RuntimeException {

  public IdpServerFailedInitializationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
