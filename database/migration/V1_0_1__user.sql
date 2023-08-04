CREATE TABLE "user" (
  id varchar(256) NOT NULL PRIMARY KEY,
  token_issuer text NOT NULL,
  name varchar(256) NOT NULL,
  given_name varchar(256) NOT NULL,
  family_name varchar(256) NOT NULL,
  middle_name varchar(256) NOT NULL,
  nickname varchar(256) NOT NULL,
  preferred_username varchar(256) NOT NULL,
  profile varchar(256) NOT NULL,
  picture varchar(256) NOT NULL,
  website varchar(256) NOT NULL,
  email varchar(256) NOT NULL,
  email_verified varchar(256) NOT NULL,
  gender varchar(256) NOT NULL,
  birthdate varchar(256) NOT NULL,
  zoneinfo varchar(256) NOT NULL,
  locale varchar(256) NOT NULL,
  phone_number varchar(256) NOT NULL,
  phone_number_verified varchar(256) NOT NULL,
  address text NOT NULL,
  custom_properties text NOT NULL,
  credentials text NOT NULL,
  password text NOT NULL,
  created_at timestamp default now() NOT NULL,
  updated_at timestamp default now() NOT NULL
);

--test data
INSERT INTO public."user" (id, token_issuer, name, given_name, family_name, middle_name, nickname, preferred_username, profile, picture, website, email, email_verified, gender, birthdate, zoneinfo, locale, phone_number, phone_number_verified, address, custom_properties, credentials, password, created_at, updated_at) VALUES ('001', '', 'ito ichiro', 'ichiro', 'ito', 'mac', 'ito', 'ichiro', 'https://example.com/profiles/123', 'https://example.com/pictures/123', 'https://example.com', 'ito.ichiro@gmail.com', 'true', 'other', '2000-02-02', 'ja-jp', 'locale', '09012345678', 'false', '', '{"key":"value"}', '[{ "@context": [ "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1" ], "id": "http://example.edu/credentials/1872", "type": [ "VerifiableCredential", "AlumniCredential" ], "issuer": "https://example.edu/issuers/565049", "issuanceDate": "2010-01-01T19:23:24Z", "credentialSubject": { "id": "did:example:ebfeb1f712ebc6f1c276e12ec21", "alumniOf": { "id": "did:example:c276e12ec21ebfeb1f712ebc6f1", "name": [ { "value": "Example University", "lang": "en" }, { "value": "Exemple d''Universite", "lang": "fr" } ] } }, "proof": { "type": "RsaSignature2018", "created": "2017-06-18T21:19:10Z", "proofPurpose": "assertionMethod", "verificationMethod": "https://example.edu/issuers/565049#key-1", "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5XsITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUcX16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtjPAYuNzVBAh4vGHSrQyHUdBBPM" } }]', 'successUserCode', '2023-08-03 23:44:20.259371', '2023-08-03 23:44:20.259371');