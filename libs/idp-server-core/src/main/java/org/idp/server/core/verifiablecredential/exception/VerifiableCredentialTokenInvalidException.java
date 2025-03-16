package org.idp.server.core.verifiablecredential.exception;

public class VerifiableCredentialTokenInvalidException extends RuntimeException {
  public VerifiableCredentialTokenInvalidException(String message) {
    super(message);
  }

  public VerifiableCredentialTokenInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
