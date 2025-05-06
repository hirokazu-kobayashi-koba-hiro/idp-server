package org.idp.server.basic.jose;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class NestedJsonWebEncryptionCreatorTest {
  String jwks = """
      {
          "keys": [
              {
                  "kty": "EC",
                  "d": "yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ",
                  "use": "sig",
                  "crv": "P-256",
                  "kid": "access_token",
                  "x": "iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0",
                  "y": "rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ",
                  "alg": "ES256"
              },
              {
                  "kty": "EC",
                  "d": "HrgT4zqM2BvrlwUWagyeNnZ40nZ7rTY4gYG9k99oGJg",
                  "use": "enc",
                  "crv": "P-256",
                  "kid": "request_enc_key",
                  "x": "PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo",
                  "y": "wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4",
                  "alg": "ECDH-ES"
              },
              {
                  "p": "6EbwM4mQBqW-ytGVm-73h0VhXEijFRh317Ye4aCPWboMjg5fJRcqRTNxY-xln-k5f2kcLhEDoEsiVlAdNIbZeQhFFVHcmYE7qkOBch4deicPuevvtgqF-bNbH70T1FAGh7EZCK-l-CLzTt0jgRRi-pvRQla3nH5lgGpJ1piyhzU",
                  "kty": "RSA",
                  "q": "wcjXiEPd7c8qjrnnaTl_ALtJeeC3UzJS_ZX_D1Fu6T8WtD4CidF34cdsOW_QEAcgNL2y6Yg_HM32XCQbZPENGMxHBfE_RVQ_PYmHJnhgYDYETc6yy9rV_rCdS-GAAmSzbOYzm8GG6Z2rwPNqM9NrGXOnBKvEqtFTLE9bf7P1R6c",
                  "d": "nQBW241LoMW27oOy4LWrBTplGUhh-4V29MqG-QX6m_aHmrTotCzf-AZ-NuL0EW3dE5tQNPvOu9SkjtGmtwmRXpacJY--JvUZzriWdezQBMR5D_RBGBUyV3Dv_Feg9FbZKsRvsk879FE--dnkgDvdd1qo9gisrueGk9-PMMz4eq1T6qHQnoJr4J1vvI3Ib0mjgAVxc3oumhJL1pr4LEVqlrwwXZGOx8ZbS9CajcYd1PvRxd2fy4dCh9M6twBHp0YQlrFshoxjxqTejSP5l3tbIScxySHaf7BFfRbvcE88uH8gjjgqDJAZ4-eRnRisiKTbiSMx5HuN_0NJybVUIBT1QQ",
                  "e": "AQAB",
                  "use": "enc",
                  "kid": "rsa_enc",
                  "qi": "faOGV_1Hxj3Nts7o-x5utKYYt8VrpPEGKekX36gUMTK-gBPTOgNmuNOJPz2fbLKRwcJqkOJfPUnzJhZj_C1f4MNfktORVWHX-mqhJIfzLqoCfk2LrEMSEIBlnc8jlogNlmDNlW0aDUbx2TXN2yMw13lGr09YCw_Pkd5JMSwSuOY",
                  "dp": "buF2RtPzSgkTNBSqm56O0SdAm-Ic37QneXT59vFDnSygU6vupXESf6hYB8BQnu6hwP23MxJyLbHQOW3TE0EQTaOx_sRuT2UOy2-gOo6_uZEuA63qZ3dMj2-cH2GONrrg8yOKdMgMrZBZn5sXGMZXnZSGZ2moCu-Xmp6ikuufxcU",
                  "alg": "RSA1_5",
                  "dq": "WPFV-7Uqp3vujJPHIwTAxhUwJEB_5C-0569w4hb-URAj25aak6cQ3xApHDO1y6V5ortu4sEmNpJSAPiRmkMJP9iCwLd50thYLmZxIbcehQpF73BvoCFRFxT5HVri5jZSJCmEhnjM82zq6CTRGfhvr77lab9tBPoOsse5t2NhsQE",
                  "n": "r9O2Ebn331GuRDhH1Q_DyJbPec_k3BdRjUmmNtx-jl7_D3z82uSdpWMaRV5LMRvQ1L71QeGRuWREVBbFoPNVQJqFmabqOff0QgOXojk13dzshrmgX5F01sDdGLS15QRLLgKQxn71wvQmi3cWDqFS_bDWz-MI4yVvcakm4f27Fxg7H08La81mqe_lS4D2Kq1HMjoDl1QMD01TiLDZ-_TpnC50Ng_qTcLRSGZxtl0k6TfkPoi0La1Ua1Qh5JK-qvNgt0OSmbHFXqJweKr5gi4kZq1u3YrUonU5t7OaF3MshDP1R4KkrDlVd_mNVTNaRSWfg1aE2OX6pW8C8ZEHlK7mkw"
              }
          ]
      }
      """;

  @Test
  public void createECDH_ES() throws JsonWebKeyInvalidException, JoseInvalidException {
    JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
    Map<String, Object> claims = Map.of("client_id", "123", "redirect_uri", "https://example.com/callback", "scope", "openid phone email address", "state", "state");
    String jwkValue = """
        {
              "kty": "EC",
              "d": "Ae0ukDMXt5y1eLU0ZlRFhL-kxO2StbgEV1vqWU7JajeLr5NaJhh_gDlA3UHOot38Sv-xIV4nWE5AEa3T-xWWp6no",
              "use": "sig",
              "crv": "P-521",
              "kid": "request_object",
              "x": "ATSp2c5ZsWleJRoBQV6ZWu2knJrgxFGIC_9MuZEd9-XNiRczdphUOLwLQpPMoMSkTY3mU-ku9YmJe8Ue4TuthrHv",
              "y": "Ae4y3oTp471l5oS2tUrClQjjCfmfjmore3lK8614MBeTUmPCM6YAp8Bw_qZ-QKpkqvOMAqqjUeaXJqQSHdQC2NPJ",
              "alg": "ES512"
          }
        """;
    JsonWebSignature jsonWebSignature = jsonWebSignatureFactory.createWithAsymmetricKey(claims, Map.of(), jwkValue);

    NestedJsonWebEncryptionCreator nestedJsonWebEncryptionCreator = new NestedJsonWebEncryptionCreator(jsonWebSignature, "ECDH-ES", "A256GCM", jwks);
    String jwe = nestedJsonWebEncryptionCreator.create();
    System.out.println(jwe);
  }

  @Test
  public void createRSA1_5() throws JsonWebKeyInvalidException, JoseInvalidException {
    JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
    Map<String, Object> claims = Map.of("client_id", "123", "redirect_uri", "https://example.com/callback", "scope", "openid phone email address", "state", "state");
    String jwkValue = """
        {
              "kty": "EC",
              "d": "Ae0ukDMXt5y1eLU0ZlRFhL-kxO2StbgEV1vqWU7JajeLr5NaJhh_gDlA3UHOot38Sv-xIV4nWE5AEa3T-xWWp6no",
              "use": "sig",
              "crv": "P-521",
              "kid": "request_object",
              "x": "ATSp2c5ZsWleJRoBQV6ZWu2knJrgxFGIC_9MuZEd9-XNiRczdphUOLwLQpPMoMSkTY3mU-ku9YmJe8Ue4TuthrHv",
              "y": "Ae4y3oTp471l5oS2tUrClQjjCfmfjmore3lK8614MBeTUmPCM6YAp8Bw_qZ-QKpkqvOMAqqjUeaXJqQSHdQC2NPJ",
              "alg": "ES512"
          }
        """;
    JsonWebSignature jsonWebSignature = jsonWebSignatureFactory.createWithAsymmetricKey(claims, Map.of(), jwkValue);

    NestedJsonWebEncryptionCreator nestedJsonWebEncryptionCreator = new NestedJsonWebEncryptionCreator(jsonWebSignature, "RSA1_5", "A256GCM", jwks);
    String jwe = nestedJsonWebEncryptionCreator.create();
    System.out.println(jwe);
  }
}
