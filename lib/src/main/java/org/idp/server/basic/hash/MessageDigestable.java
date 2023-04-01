package org.idp.server.basic.hash;

import java.security.MessageDigest;

public interface MessageDigestable {

  default byte[] digestWithMD5(String value) {
    MessageDigest messageDigest = HashAlgorithm.MD5.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha1(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_1.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha256(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_256.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha384(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_384.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha512(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_512.messageDigest();
    return messageDigest.digest(value.getBytes());
  }
}
