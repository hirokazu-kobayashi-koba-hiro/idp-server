package org.idp.server.basic.hash;

import org.apache.commons.codec.binary.Hex;

import java.security.MessageDigest;
public interface MessageDigestable {

    default String digestWithMD5(String value) {
        MessageDigest messageDigest = HashAlgorithm.MD5.messageDigest();
        byte[] digest = messageDigest.digest(value.getBytes());
        return Hex.encodeHexString(digest);
    }
    default String digestWithSha1(String value) {
        MessageDigest messageDigest = HashAlgorithm.SHA_1.messageDigest();
        byte[] digest = messageDigest.digest(value.getBytes());
        return Hex.encodeHexString(digest);
    }

    default String digestWithSha256(String value) {
        MessageDigest messageDigest = HashAlgorithm.SHA_256.messageDigest();
        byte[] digest = messageDigest.digest(value.getBytes());
        return Hex.encodeHexString(digest);
    }

    default String digestWithSha384(String value) {
        MessageDigest messageDigest = HashAlgorithm.SHA_384.messageDigest();
        byte[] digest = messageDigest.digest(value.getBytes());
        return Hex.encodeHexString(digest);
    }

    default String digestWithSha512(String value) {
        MessageDigest messageDigest = HashAlgorithm.SHA_512.messageDigest();
        byte[] digest = messageDigest.digest(value.getBytes());
        return Hex.encodeHexString(digest);
    }

}
