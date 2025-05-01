package org.idp.server.basic.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** HashAlgorithm */
enum HashAlgorithm {
  MD5("MD5"),
  SHA_1("SHA-1"),
  SHA_256("SHA-256"),
  SHA_384("SHA-384"),
  SHA_512("SHA-512");

  String value;
  MessageDigest messageDigest;

  HashAlgorithm(String value) {
    this.value = value;
    try {
      this.messageDigest = MessageDigest.getInstance(value);
    } catch (NoSuchAlgorithmException exception) {
      throw new RuntimeException(exception);
    }
  }

  public String value() {
    return value;
  }

  public MessageDigest messageDigest() {
    return messageDigest;
  }
}
