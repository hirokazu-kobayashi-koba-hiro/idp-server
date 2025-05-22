/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.x509;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.idp.server.basic.base64.Base64Codeable;

public class X509Certification implements Base64Codeable {
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
    PublicKey publicKey = x509Certificate.getPublicKey();
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

  public String derWithBase64() {
    return encodeString(der);
  }

  public X509SubjectAlternativeNames subjectAlternativeNames() {
    return subjectAlternativeNames;
  }

  public String otherName() {
    return subjectAlternativeNames.otherName();
  }

  public boolean hasOtherName() {
    return subjectAlternativeNames.hasOtherName();
  }

  public String rfc822Name() {
    return subjectAlternativeNames.rfc822Name();
  }

  public boolean hasRfc822Name() {
    return subjectAlternativeNames.hasRfc822Name();
  }

  public String dNSName() {
    return subjectAlternativeNames.dNSName();
  }

  public boolean hasDNSName() {
    return subjectAlternativeNames.hasDNSName();
  }

  public String x400Address() {
    return subjectAlternativeNames.x400Address();
  }

  public boolean hasX400Address() {
    return subjectAlternativeNames.hasX400Address();
  }

  public String directoryName() {
    return subjectAlternativeNames.directoryName();
  }

  public boolean hasDirectoryName() {
    return subjectAlternativeNames.hasDirectoryName();
  }

  public String ediPartyName() {
    return subjectAlternativeNames.ediPartyName();
  }

  public boolean hasEditPartyName() {
    return subjectAlternativeNames.hasEditPartyName();
  }

  public String uniformResourceIdentifier() {
    return subjectAlternativeNames.uniformResourceIdentifier();
  }

  public boolean hasUniformResourceIdentifier() {
    return subjectAlternativeNames.hasUniformResourceIdentifier();
  }

  public String registeredID() {
    return subjectAlternativeNames.registeredID();
  }

  public boolean hasRegisteredID() {
    return subjectAlternativeNames.hasRegisteredID();
  }

  public String iPAddress() {
    return subjectAlternativeNames.iPAddress();
  }

  public boolean hasIPAddress() {
    return subjectAlternativeNames.hasIPAddress();
  }

  public PublicKey toPublicKey() {
    return x509Certificate.getPublicKey();
  }
}
