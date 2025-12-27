# OpenID Connect for Identity Assurance（IDA）

OpenID Connect for Identity Assurance は、身元確認（本人確認）の結果を標準化された方法で RP に伝達するための仕様です。

---

## 第1部: 概要編

### Identity Assurance とは？

Identity Assurance（IDA）は、「ユーザーが本当に主張する人物である」ことを**どの程度確信できるか**を表す概念です。

```
従来の OIDC:
  「このユーザーは user@example.com として認証された」
  → でも、その人が本当に誰なのかは分からない

OIDC for IDA:
  「このユーザーは山田太郎で、パスポートで本人確認済み」
  → 身元確認の方法と結果も伝える

  ┌──────────────────────────────────────────────────┐
  │                  ID Token / UserInfo             │
  ├──────────────────────────────────────────────────┤
  │  sub: user-123                                   │
  │  name: 山田太郎                                   │
  │  verified_claims:                                │
  │    verification:                                 │
  │      trust_framework: jp_aml                     │
  │      evidence:                                   │
  │        - type: document                          │
  │          document_type: passport                 │
  │    claims:                                       │
  │      given_name: 太郎                            │
  │      family_name: 山田                           │
  │      birthdate: 1990-01-01                       │
  └──────────────────────────────────────────────────┘
```

### なぜ IDA が必要なのか？

| シナリオ | 従来の問題 | IDA による解決 |
|---------|-----------|---------------|
| 金融口座開設 | 本人確認書類の情報が伝わらない | 確認方法・書類種別を標準形式で伝達 |
| 規制対応（KYC/AML） | 確認レベルが不明 | trust_framework で法的枠組みを明示 |
| 相互運用性 | OP ごとに独自形式 | 標準化された verified_claims 形式 |
| 監査対応 | 確認プロセスの記録が散逸 | evidence で確認証跡を提供 |

### 主要な概念

| 概念 | 説明 |
|------|------|
| `verified_claims` | 身元確認されたクレームを含むコンテナ |
| `verification` | 身元確認プロセスの情報 |
| `trust_framework` | 準拠する法的・規制枠組み |
| `evidence` | 身元確認に使用された証拠 |
| `assurance_level` | 確認の信頼レベル |

---

## 第2部: 詳細編

### verified_claims の構造

```json
{
  "verified_claims": {
    "verification": {
      "trust_framework": "jp_aml",
      "time": "2024-01-15T10:30:00Z",
      "verification_process": "eKYC",
      "evidence": [
        {
          "type": "document",
          "method": "pipp",
          "time": "2024-01-15T10:25:00Z",
          "document": {
            "type": "passport",
            "issuer": {
              "name": "日本国",
              "country": "JP"
            },
            "number": "AB1234567",
            "date_of_issuance": "2020-04-01",
            "date_of_expiry": "2030-03-31"
          }
        }
      ]
    },
    "claims": {
      "given_name": "太郎",
      "family_name": "山田",
      "birthdate": "1990-01-01",
      "place_of_birth": {
        "country": "JP",
        "locality": "東京都"
      },
      "nationalities": ["JP"],
      "address": {
        "formatted": "東京都千代田区丸の内1-1-1",
        "country": "JP",
        "region": "東京都",
        "locality": "千代田区",
        "postal_code": "100-0005"
      }
    }
  }
}
```

### verification オブジェクト

| フィールド | 説明 |
|-----------|------|
| `trust_framework` | 準拠する法的枠組み |
| `time` | 確認が行われた時刻 |
| `verification_process` | 確認プロセスの識別子 |
| `evidence` | 確認に使用された証拠の配列 |
| `assurance_level` | 確認の信頼レベル |

### trust_framework の例

| 値 | 説明 |
|----|------|
| `jp_aml` | 日本の犯罪収益移転防止法 |
| `de_aml` | ドイツのマネーロンダリング防止法 |
| `eidas` | EU の eIDAS 規則 |
| `nist_800_63A` | NIST SP 800-63A |
| `uk_tfida` | 英国 Trust Framework for Identity Assurance |

### evidence（証拠）

#### document（書類による確認）

```json
{
  "type": "document",
  "method": "pipp",
  "verifier": {
    "organization": "Example Bank",
    "txn": "verification-12345"
  },
  "time": "2024-01-15T10:25:00Z",
  "document": {
    "type": "passport",
    "issuer": {
      "name": "日本国",
      "country": "JP"
    },
    "number": "AB1234567",
    "date_of_issuance": "2020-04-01",
    "date_of_expiry": "2030-03-31"
  }
}
```

| method | 説明 |
|--------|------|
| `pipp` | Physical In-Person Proofing（対面確認） |
| `sripp` | Supervised Remote In-Person Proofing（リモート対面） |
| `eid` | 電子身分証（マイナンバーカード等） |
| `uripp` | Unsupervised Remote In-Person Proofing |

#### document_type の例

| 値 | 説明 |
|----|------|
| `passport` | パスポート |
| `driving_permit` | 運転免許証 |
| `residence_permit` | 在留カード |
| `idcard` | ID カード |
| `bank_statement` | 銀行明細 |
| `utility_statement` | 公共料金明細 |

#### electronic_record（電子記録による確認）

```json
{
  "type": "electronic_record",
  "check_details": [
    {
      "check_method": "kbv",
      "organization": "Credit Bureau",
      "time": "2024-01-15T10:20:00Z"
    }
  ],
  "record": {
    "type": "bank_account",
    "source": {
      "name": "Example Bank"
    }
  }
}
```

#### vouch（保証）

```json
{
  "type": "vouch",
  "validation_method": {
    "type": "vpip"
  },
  "verifier": {
    "organization": "Notary Public"
  },
  "attestation": {
    "type": "digital_attestation",
    "reference_number": "VOUCH-12345"
  }
}
```

#### electronic_signature（電子署名）

```json
{
  "type": "electronic_signature",
  "signature_type": "QES",
  "issuer": "Qualified Trust Service Provider",
  "serial_number": "1234567890",
  "created_at": "2024-01-15T10:15:00Z"
}
```

### 認可リクエストでの要求

#### claims パラメータ

```json
{
  "userinfo": {
    "verified_claims": {
      "verification": {
        "trust_framework": null,
        "evidence": [
          {
            "type": {
              "value": "document"
            },
            "document": {
              "type": null
            }
          }
        ]
      },
      "claims": {
        "given_name": null,
        "family_name": null,
        "birthdate": {
          "essential": true
        }
      }
    }
  }
}
```

#### purpose（目的の指定）

```json
{
  "userinfo": {
    "verified_claims": {
      "verification": {
        "trust_framework": null
      },
      "claims": {
        "given_name": {
          "purpose": "金融口座開設のための本人確認"
        },
        "family_name": {
          "purpose": "金融口座開設のための本人確認"
        },
        "birthdate": {
          "essential": true,
          "purpose": "年齢確認"
        }
      }
    }
  }
}
```

### 特定の trust_framework を要求

```json
{
  "userinfo": {
    "verified_claims": {
      "verification": {
        "trust_framework": {
          "value": "jp_aml"
        }
      },
      "claims": {
        "given_name": null,
        "family_name": null
      }
    }
  }
}
```

### 複数の verified_claims

異なる trust_framework で確認された複数のクレームセットを返すことができます。

```json
{
  "verified_claims": [
    {
      "verification": {
        "trust_framework": "jp_aml"
      },
      "claims": {
        "given_name": "太郎",
        "family_name": "山田"
      }
    },
    {
      "verification": {
        "trust_framework": "eidas",
        "assurance_level": "high"
      },
      "claims": {
        "given_name": "Taro",
        "family_name": "Yamada"
      }
    }
  ]
}
```

### 実装例

#### Java（OP 側）

```java
@Service
public class VerifiedClaimsService {

    public VerifiedClaims buildVerifiedClaims(User user, VerificationRecord record) {
        // verification オブジェクトの構築
        Verification verification = Verification.builder()
            .trustFramework("jp_aml")
            .time(record.getVerificationTime())
            .verificationProcess(record.getProcessId())
            .evidence(buildEvidence(record))
            .build();

        // 確認済みクレームの構築
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("given_name", user.getGivenName());
        claims.put("family_name", user.getFamilyName());
        claims.put("birthdate", user.getBirthdate().toString());

        if (user.getAddress() != null) {
            claims.put("address", buildAddress(user.getAddress()));
        }

        return VerifiedClaims.builder()
            .verification(verification)
            .claims(claims)
            .build();
    }

    private List<Evidence> buildEvidence(VerificationRecord record) {
        List<Evidence> evidenceList = new ArrayList<>();

        for (DocumentRecord doc : record.getDocuments()) {
            Evidence evidence = Evidence.builder()
                .type("document")
                .method(doc.getMethod())
                .time(doc.getVerificationTime())
                .document(Document.builder()
                    .type(doc.getDocumentType())
                    .issuer(Issuer.builder()
                        .name(doc.getIssuerName())
                        .country(doc.getIssuerCountry())
                        .build())
                    .number(doc.getDocumentNumber())
                    .dateOfIssuance(doc.getDateOfIssuance())
                    .dateOfExpiry(doc.getDateOfExpiry())
                    .build())
                .build();

            evidenceList.add(evidence);
        }

        return evidenceList;
    }
}
```

#### JavaScript（RP 側）

```javascript
class VerifiedClaimsHandler {
  constructor(config) {
    this.requiredTrustFrameworks = config.requiredTrustFrameworks || [];
    this.requiredClaims = config.requiredClaims || [];
  }

  // verified_claims を検証
  validateVerifiedClaims(verifiedClaims) {
    if (!verifiedClaims) {
      throw new Error('verified_claims is missing');
    }

    // 配列の場合
    const claimsList = Array.isArray(verifiedClaims) ? verifiedClaims : [verifiedClaims];

    for (const vc of claimsList) {
      this.validateSingleVerifiedClaims(vc);
    }

    return true;
  }

  validateSingleVerifiedClaims(vc) {
    // verification の存在確認
    if (!vc.verification) {
      throw new Error('verification is missing');
    }

    // trust_framework の確認
    if (this.requiredTrustFrameworks.length > 0) {
      if (!this.requiredTrustFrameworks.includes(vc.verification.trust_framework)) {
        throw new Error(`Unsupported trust_framework: ${vc.verification.trust_framework}`);
      }
    }

    // 必須クレームの確認
    for (const claim of this.requiredClaims) {
      if (!vc.claims || vc.claims[claim] === undefined) {
        throw new Error(`Required claim missing: ${claim}`);
      }
    }

    return true;
  }

  // claims パラメータを構築
  buildClaimsRequest(options) {
    const request = {
      userinfo: {
        verified_claims: {
          verification: {
            trust_framework: options.trustFramework ? { value: options.trustFramework } : null,
            evidence: options.evidenceTypes ? options.evidenceTypes.map(type => ({
              type: { value: type }
            })) : null
          },
          claims: {}
        }
      }
    };

    // 要求するクレームを追加
    for (const claim of options.claims) {
      if (typeof claim === 'string') {
        request.userinfo.verified_claims.claims[claim] = null;
      } else {
        request.userinfo.verified_claims.claims[claim.name] = {
          essential: claim.essential || false,
          purpose: claim.purpose
        };
      }
    }

    return request;
  }
}

// 使用例
const handler = new VerifiedClaimsHandler({
  requiredTrustFrameworks: ['jp_aml', 'eidas'],
  requiredClaims: ['given_name', 'family_name', 'birthdate']
});

// claims パラメータを構築
const claimsRequest = handler.buildClaimsRequest({
  trustFramework: 'jp_aml',
  evidenceTypes: ['document'],
  claims: [
    { name: 'given_name', purpose: '本人確認' },
    { name: 'family_name', purpose: '本人確認' },
    { name: 'birthdate', essential: true, purpose: '年齢確認' }
  ]
});

// UserInfo レスポンスの検証
const userInfo = await fetchUserInfo(accessToken);
handler.validateVerifiedClaims(userInfo.verified_claims);
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://op.example.com",
  "verified_claims_supported": true,
  "trust_frameworks_supported": [
    "jp_aml",
    "eidas",
    "nist_800_63A"
  ],
  "evidence_supported": [
    "document",
    "electronic_record",
    "vouch",
    "electronic_signature"
  ],
  "documents_supported": [
    "passport",
    "driving_permit",
    "idcard",
    "residence_permit"
  ],
  "documents_methods_supported": [
    "pipp",
    "sripp",
    "eid"
  ],
  "claims_in_verified_claims_supported": [
    "given_name",
    "family_name",
    "birthdate",
    "place_of_birth",
    "nationalities",
    "address"
  ]
}
```

### assurance_level

eIDAS や NIST などの規格で定義された信頼レベル。

```json
{
  "verification": {
    "trust_framework": "eidas",
    "assurance_level": "high"
  }
}
```

| 規格 | レベル |
|------|--------|
| eIDAS | low, substantial, high |
| NIST 800-63A | IAL1, IAL2, IAL3 |

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 暗号化 | verified_claims は暗号化された UserInfo で返す |
| 最小限の開示 | 必要なクレームのみ要求 |
| purpose | 利用目的を明示 |
| 有効期限 | 確認の time を考慮（古すぎないか） |
| 証跡管理 | evidence 情報を監査用に保存 |

---

## 参考リンク

- [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html)
- [eIDAS Regulation](https://digital-strategy.ec.europa.eu/en/policies/eidas-regulation)
- [NIST SP 800-63A - Digital Identity Guidelines](https://pages.nist.gov/800-63-3/sp800-63a.html)
