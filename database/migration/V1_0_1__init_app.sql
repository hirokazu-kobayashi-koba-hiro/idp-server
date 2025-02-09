CREATE TABLE tenant (
  id varchar(256) NOT NULL PRIMARY KEY,
  name varchar(256) NOT NULL,
  type varchar(10) NOT NULL,
  issuer text NOT NULL,
  created_at timestamp NOT NULL default now(),
  updated_at timestamp NOT NULL default now()
);

CREATE TABLE "user" (
  id varchar(256) NOT NULL PRIMARY KEY,
  tenant_id varchar(256) NOT NULL,
  name varchar(256),
  given_name varchar(256),
  family_name varchar(256),
  middle_name varchar(256),
  nickname varchar(256),
  preferred_username varchar(256),
  profile varchar(256),
  picture varchar(256),
  website varchar(256),
  email varchar(256),
  email_verified boolean,
  gender varchar(256),
  birthdate varchar(256),
  zoneinfo varchar(256),
  locale varchar(256),
  phone_number varchar(256),
  phone_number_verified boolean,
  address text NOT NULL,
  custom_properties text,
  credentials text,
  password text,
  created_at timestamp default now() NOT NULL,
  updated_at timestamp default now() NOT NULL,
  CONSTRAINT uk_tenant_id_email unique (tenant_id, email)
);

--test data
INSERT INTO public.tenant (id, name, type, issuer) VALUES ('123', 'sample', 'ADMIN', 'http://localhost:8080/123');
INSERT INTO public.tenant (id, name, type, issuer) VALUES ('999', 'unsupported', 'PUBLIC', 'http://localhost:8080/999');

INSERT INTO public."user" (id, tenant_id, name, given_name, family_name, middle_name, nickname, preferred_username, profile, picture, website, email, email_verified, gender, birthdate, zoneinfo, locale, phone_number, phone_number_verified, address, custom_properties, credentials, password, created_at, updated_at) VALUES ('001', '123', 'ito ichiro', 'ichiro', 'ito', 'mac', 'ito', 'ichiro', 'https://example.com/profiles/123', 'https://example.com/pictures/123', 'https://example.com', 'ito.ichiro@gmail.com', 'true', 'other', '2000-02-02', 'ja-jp', 'locale', '09012345678', 'false', '', '{"key":"value"}', '[{ "@context": [ "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1" ], "id": "http://example.edu/credentials/1872", "type": [ "VerifiableCredential", "AlumniCredential" ], "issuer": "https://example.edu/issuers/565049", "issuanceDate": "2010-01-01T19:23:24Z", "credentialSubject": { "id": "did:example:ebfeb1f712ebc6f1c276e12ec21", "alumniOf": { "id": "did:example:c276e12ec21ebfeb1f712ebc6f1", "name": [ { "value": "Example University", "lang": "en" }, { "value": "Exemple d''Universite", "lang": "fr" } ] } }, "proof": { "type": "RsaSignature2018", "created": "2017-06-18T21:19:10Z", "proofPurpose": "assertionMethod", "verificationMethod": "https://example.edu/issuers/565049#key-1", "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5XsITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUcX16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtjPAYuNzVBAh4vGHSrQyHUdBBPM" } }]', 'successUserCode', '2023-08-03 23:44:20.259371', '2023-08-03 23:44:20.259371');