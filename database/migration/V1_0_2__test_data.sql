INSERT INTO public.tenant (id, name, type, issuer)
VALUES ('67e7eae6-62b0-4500-9eff-87459f63fc66', 'sample', 'ADMIN', 'http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66');
INSERT INTO public.tenant (id, name, type, issuer)
VALUES ('94d8598e-f238-4150-85c2-c4accf515784', 'unsupported', 'PUBLIC', 'http://localhost:8080/94d8598e-f238-4150-85c2-c4accf515784');

INSERT INTO public.idp_user (id, tenant_id, name, given_name, family_name, middle_name, nickname, preferred_username,
                           profile, picture, website, email, email_verified, gender, birthdate, zoneinfo, locale,
                           phone_number, phone_number_verified, address, custom_properties, credentials, password,
                           created_at, updated_at)
VALUES ('3ec055a8-8000-44a2-8677-e70ebff414e2', '67e7eae6-62b0-4500-9eff-87459f63fc66', 'ito ichiro', 'ichiro', 'ito', 'mac', 'ito', 'ichiro', 'https://example.com/profiles/123',
        'https://example.com/pictures/123', 'https://example.com', 'ito.ichiro@gmail.com', 'true', 'other',
        '2000-02-02', 'ja-jp', 'locale', '09012345678', 'false', '', '{"key":"value"}',
        '[{ "@context": [ "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1" ], "id": "http://example.edu/credentials/1872", "type": [ "VerifiableCredential", "AlumniCredential" ], "issuer": "https://example.edu/issuers/565049", "issuanceDate": "2010-01-01T19:23:24Z", "credentialSubject": { "id": "did:example:ebfeb1f712ebc6f1c276e12ec21", "alumniOf": { "id": "did:example:c276e12ec21ebfeb1f712ebc6f1", "name": [ { "value": "Example University", "lang": "en" }, { "value": "Exemple d''Universite", "lang": "fr" } ] } }, "proof": { "type": "RsaSignature2018", "created": "2017-06-18T21:19:10Z", "proofPurpose": "assertionMethod", "verificationMethod": "https://example.edu/issuers/565049#key-1", "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5XsITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUcX16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtjPAYuNzVBAh4vGHSrQyHUdBBPM" } }]',
        'successUserCode', '2023-08-03 23:44:20.259371', '2023-08-03 23:44:20.259371');