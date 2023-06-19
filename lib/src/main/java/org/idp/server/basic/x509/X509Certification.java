package org.idp.server.basic.x509;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class X509Certification {
  X509Certificate x509Certificate;
  byte[] der;
  X509SubjectAlternativeNames subjectAlternativeNames;

  public X509Certification() {}

  public X509Certification(
      X509Certificate x509Certificate,
      byte[] der,
      X509SubjectAlternativeNames subjectAlternativeNames) {
    this.x509Certificate = x509Certificate;
    this.der = der;
    this.subjectAlternativeNames = subjectAlternativeNames;
  }

  public static X509Certification parse(String value) throws X509CertInvalidException {
    try {
      byte[] bytes = value.getBytes();
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      X509Certificate cert =
          (X509Certificate) certificateFactory.generateCertificate(byteArrayInputStream);
      byte[] der = cert.getEncoded();
      X509SubjectAlternativeNames subjectAlternativeNames = X509SubjectAlternativeNames.parse(cert);
      return new X509Certification(cert, der, subjectAlternativeNames);
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
  }

  public X509Certificate x509Certificate() {
    return x509Certificate;
  }

  public String subject() {
    return x509Certificate.getSubjectX500Principal().getName();
  }

  public byte[] der() {
    return der;
  }

  public X509SubjectAlternativeNames subjectAlternativeNames() {
    return subjectAlternativeNames;
  }
}
