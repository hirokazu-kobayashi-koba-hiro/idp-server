package org.idp.server.basic.vc;

import com.nimbusds.jose.JOSEException;
import foundation.identity.jsonld.JsonLDException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.Map;
import org.idp.server.core.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.vc.VcInvalidKeyException;
import org.junit.jupiter.api.Test;

public class CredentialHandlerTest {

  @Test
  void handle()
      throws JsonLDException,
          GeneralSecurityException,
          ParseException,
          IOException,
          JOSEException,
          JsonWebKeyInvalidException,
          VcInvalidKeyException {
    String privateKey =
        """
                {
                    "p": "-uM0cfGWCMFTuFSxfpATzCuS1Fl17-bvWFEo1VXjNzh35KmkLkZzDQMbaQ0ff2yartXZh7Y3v73NMz3oSVMN2y126N8ci-FvATkobqikV4KyD_WOHxrFDp6tELUQ8plqGRBZCs9F81JqstkH2Z_KHjyGZH_BLi_td7ZTrGa-Ojc",
                    "kty": "RSA",
                    "q": "nfZW25PFJ3onheraGnqwU29b8_RXmX9MpqxbnOqW-py3swHtVw2ZnrfXYAbR4fXtBdITDzAfkQnywOCTJDjr_BU2RD92Tz9XFC-ZIXgpR2pWYed73_4N5caxCugpttfi5h-B-GRKD02akLhhu_Es0ysQDK9Hh87cDXvxQjL6cAM",
                    "d": "YcrrNjMom-1HbVg8xzE67e4KmEjG0OFq4x3KopI6dvzLc0kscY2NeeV0NmKb5UqxB618IY8mLt3sy3HI2CUib4YVkt71U9_OA-EK8nz0-n0Vmswqcj40G4NTqe94b-v8KcXSZ8f3jA9zXMuhqWBCOgHLf5ZN202MCtTY7XVBlCC9zP9HVUrCX4ddA0FyHECQoQuWAMe8V4PTf9nfBU7ljTj_N01WGP0NC02PMjQJGYEJDSjYmPPlDaKrlzLPOAKEx7e8qLAiHTo-6xN3MHw3GZr1E5CEsKto0RC_dR8it3ae33y2qQJEVd04PUq4fg7fW_7NnwFRv2zwLK6picWZoQ",
                    "e": "AQAB",
                    "use": "sig",
                    "kid": "vc_test",
                    "qi": "G4PGCB3z6AuDRXQ-gFDQNTRPbMD6kEN0frvMpmRsvZ8EqAbzxW4nIJFrRTVa79nLTt4Nhqem3BtvY1Gfsm-x1kn1Ch_G6pnQRGYM_FPtysMrv3yDjzfIh6iFRqST0pyQAGo5Rv4nm1Gb5oTSBPM8lxcCYwSQmvhPqaFpFcO3zKI",
                    "dp": "msb2gOEIrWgPbSWaxri97fRnxedW8eSnffW72jj9TwDgPkpFxmKh8cIb8-grqWHn37qAU9AffoPqhxVHK404fCIFPM7__m_aGogXoIUbtf0kOuJDhn7uWQGdRTRfOztYEBRcymVrAxyyRJnN1P1WJOc0BeYm2Iaq2nK6Fh7gdms",
                    "alg": "RS256",
                    "dq": "JNYUbUIpgMpAuz8IO5KO2fYnGF3Lq9T-2ANrnc0rBihTwl_ZrCpUv6mZhSAyA6Ko8mmJ3knXoqgSPx5f0dugVHnR3Np7yYE42lE7QY3W-nt2x4AG6DZDoWjTUnrPd38iv41rbrRcfPMY71TAHebhECGDraGWabo4oMBdg5WFx2c",
                    "n": "ms7Cnct0_NIp0TNcjV5y3GN8DpCKq7T6vCIekucAAey46gPbXFIco4-x0Fq9UNKtJ517axIBRh9WFXXpAlABtfLfdKAhjqZTOp1NigRM65UMqcmEnY--3Ee5cNt1FqJuQGueutuJmNoN0_d7Wy64e3-dEkPa6JKrm-GM80Z8nrdB-AxX_K_5u2r-_nwMY3rXV28lxu__n-pw48cGKGw2dXSHmEEeRl282T1AS7bH0bMBqtrEUrSAhuUQB-og_KBuZp5c2_gWB1-1kmxkX3OV3BP2esePzMqaigiUc-oG7fhL2gANk2OSLws858Dh81qOiN5hJHDyJ1fjca5KBWi-pQ"
                }
                """;
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    Map map = jsonConverter.read(vc, Map.class);
  }

  String vc =
      """
                    {
                        "@context": [
                            "https://www.w3.org/2018/credentials/v1",
                            "https://www.w3.org/2018/credentials/examples/v1"
                        ],
                        "id": "http://example.edu/credentials/1872",
                        "type": [
                            "VerifiableCredential",
                            "AlumniCredential"
                        ],
                        "issuer": "https://example.edu/issuers/565049",
                        "issuanceDate": "2010-01-01T19:23:24Z",
                        "credentialSubject": {
                            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
                            "alumniOf": {
                                "id": "did:example:c276e12ec21ebfeb1f712ebc6f1",
                                "name": [
                                    {
                                        "value": "Example University",
                                        "lang": "en"
                                    },
                                    {
                                        "value": "Exemple d'Universite",
                                        "lang": "fr"
                                    }
                                ]
                            }
                        }
                    }
                    """;
}
