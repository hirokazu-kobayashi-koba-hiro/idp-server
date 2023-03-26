package org.idp.server.basic.hash;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessageDigestableTest implements MessageDigestable {

    @Test
    void can_digest_with_md5() {
        String digest = digestWithMD5("test");
        System.out.println(digest);
        Assertions.assertEquals("098f6bcd4621d373cade4e832627b4f6", digest);
    }
    @Test
    void can_digest_with_sha1() {
        String digest = digestWithSha1("test");
        System.out.println(digest);
        Assertions.assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", digest);
    }

    @Test
    void can_digest_with_sha256() {
        String digest = digestWithSha256("test");
        System.out.println(digest);
        Assertions.assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", digest);
    }

    @Test
    void can_digest_with_sha384() {
        String digest = digestWithSha384("test");
        System.out.println(digest);
        Assertions.assertEquals("768412320f7b0aa5812fce428dc4706b3cae50e02a64caa16a782249bfe8efc4b7ef1ccb126255d196047dfedf17a0a9", digest);
    }

    @Test
    void can_digest_with_sha512() {
        String digest = digestWithSha512("test");
        System.out.println(digest);
        Assertions.assertEquals("ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff", digest);
    }
}
