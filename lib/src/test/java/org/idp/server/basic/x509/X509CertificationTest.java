package org.idp.server.basic.x509;

import org.junit.jupiter.api.Test;

public class X509CertificationTest {

  @Test
  void parse() throws X509CertInvalidException {
    String cert =
        """
                -----BEGIN CERTIFICATE-----
                MIIDSDCCAjACCQDYdfWcznx8ATANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJK
                UDEOMAwGA1UECAwFVG9reW8xEzARBgNVBAcMClN1Z2luYW1pa3UxCzAJBgNVBAoM
                AmhrMQwwCgYDVQQLDANkZXYxFzAVBgNVBAMMDmlkcC5zZXJ2ZXIuY29tMB4XDTIz
                MDYwMzAyMzMzMVoXDTMzMDUzMTAyMzMzMVowZjELMAkGA1UEBhMCSlAxDjAMBgNV
                BAgMBVRva3lvMRMwEQYDVQQHDApTdWdpbmFtaWt1MQswCQYDVQQKDAJoazEMMAoG
                A1UECwwDZGV2MRcwFQYDVQQDDA5pZHAuc2VydmVyLmNvbTCCASIwDQYJKoZIhvcN
                AQEBBQADggEPADCCAQoCggEBALtyAfvO8x/TPUuaz2c5lCRQD+1PZblWAS+wucGB
                j1QGv/vdbjGJegzDd3rqvhJDCCLFKiN2ioXwcWpHj6F2IgsgQgQDHkHRdiHTm9Ht
                2ht42F0e2HnC3r+kzUjb9AplBWZko2bkO0xi6OQcBsxWZDo+TyLN1D2+Nb3U0lVY
                EBPAdWDR+hJJNwYvgIECz5y9XL6C7dfgDVX2NoK9P8lv0jEIuJTCeTD6T3shNSQs
                vV7eYQeEOk8dIxVNkt7wC3wtxOJYlwXUwTA6NHK/ljN2OnVnHyrpaOzltTDMJWLz
                0xdCt0FvcdDnvaUODmHz+DP26lmgQjemt9Twyal1HoWzkRUCAwEAATANBgkqhkiG
                9w0BAQsFAAOCAQEAqt04XqRsDftkuDjcJ0+LtIW94pzeSYdztGajBu4SNwywSX39
                7PFeZkj2Sv2G8UtvrFuDF41sWMHRA6Z9Kt96bsy4CLH87sj48/usA+TYVHoaka2G
                V0UPCar0d9VigZNUFK9/rjSiJKVjVYDSeqUrFoOrXzqiWdgnlIZxCQh2aZKu0udY
                eBebioc9eG4sUhh812VR/0hQ0HYgEXDRA6BcKhAQCAvI3AhUt2l78/dYOZPYyXs2
                4b+hJx+5KX4RwpWv6OuT/pI/VaD9vIptaXl2QB/E5GWdYDulbUAbznE6G+2Rjeze
                DKtHaj6Ta6n1PERdnQkBobMljM83QNI4RZSd0w==
                -----END CERTIFICATE-----
                """;

    X509Certification x509Certification = X509Certification.parse(cert);
    X509SubjectAlternativeNames x509SubjectAlternativeNames =
        x509Certification.subjectAlternativeNames();
    String subject = x509Certification.subject();
    System.out.println(subject);
  }
}
