package org.idp.server.core.oidc.identity;

import java.util.Arrays;
import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.hash.MessageDigestable;

/**
 * example at_hash case
 *
 * <p>Access Token hash value. Its value is the base64url encoding of the left-most half of the hash
 * of the octets of the ASCII representation of the access_token value, where the hash algorithm
 * used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header. For
 * instance, if the alg is RS256, hash the access_token value with SHA-256, then take the left-most
 * 128 bits and base64url encode them. The at_hash value is a case sensitive string.
 */
public interface ClaimHashable extends MessageDigestable, Base64Codeable {

  default String hash(String input, String algorithm) {
    String alg = algorithm.substring(2, 5);
    switch (alg) {
      case "256" -> {
        byte[] bytes = digestWithSha256(input);
        byte[] halfBytes = Arrays.copyOfRange(bytes, 0, bytes.length / 2);
        return encodeWithUrlSafe(halfBytes);
      }
      case "384" -> {
        byte[] bytes = digestWithSha384(input);
        byte[] halfBytes = Arrays.copyOfRange(bytes, 0, bytes.length / 2);
        return encodeWithUrlSafe(halfBytes);
      }
      case "512" -> {
        byte[] bytes = digestWithSha512(input);
        byte[] halfBytes = Arrays.copyOfRange(bytes, 0, bytes.length / 2);
        return encodeWithUrlSafe(halfBytes);
      }
    }
    return "";
  }
}
