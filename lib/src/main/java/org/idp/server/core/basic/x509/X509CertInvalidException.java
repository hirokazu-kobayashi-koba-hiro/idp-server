package org.idp.server.core.basic.x509;

public class X509CertInvalidException extends Exception {

  public X509CertInvalidException(String message) {
    super(message);
  }

  public X509CertInvalidException(Throwable throwable) {
    super(throwable);
  }
}
