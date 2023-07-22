package org.idp.server.verifiablecredential.exception;

public class VerifiableCredentialRequestInvalidException extends Exception {

  public VerifiableCredentialRequestInvalidException(String message) {
    super(message);
  }

  public VerifiableCredentialRequestInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
