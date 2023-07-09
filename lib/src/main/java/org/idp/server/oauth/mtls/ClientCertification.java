package org.idp.server.oauth.mtls;

import java.util.Objects;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;

public class ClientCertification {
  X509Certification x509Certification;

  public ClientCertification() {}

  public ClientCertification(X509Certification x509Certification) {
    this.x509Certification = x509Certification;
  }

  public static ClientCertification parse(String value) throws X509CertInvalidException {
    X509Certification x509Certification = X509Certification.parse(value);
    return new ClientCertification(x509Certification);
  }

  public boolean exists() {
    return Objects.nonNull(x509Certification);
  }

  public String subject() {
    return x509Certification.subject();
  }

  public byte[] der() {
    return x509Certification.der();
  }
}
