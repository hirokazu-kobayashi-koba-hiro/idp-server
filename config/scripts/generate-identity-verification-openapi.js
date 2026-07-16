#!/usr/bin/env node
/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * 身元確認（Identity Verification）設定JSONから OpenAPI 3.0.3 ドキュメントを生成する。
 *
 * 設定の `processes` に定義された各プロセスを、実際のエンドポイントURLに展開する:
 *
 *   - 初回申込みプロセス（先頭の非callbackプロセス）
 *       POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{process}
 *   - 継続プロセス（2番目以降、または dependencies.required_processes を持つもの）
 *       POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{id}/{process}
 *   - コールバックプロセス（"type": "callback"）
 *       POST /{tenant-id}/internal/v1/identity-verification/callback/{type}/{process}
 *
 * リクエストボディは `request.schema`（JSON Schema）から生成する。独自キーワード
 * （store / respond）は OpenAPI 非互換のため除去する。レスポンスは
 * `response.body_mapping_rules` から導出する（初回申込みのみ `id` がサーバーで強制付与される）。
 *
 * 使い方:
 *   node config/scripts/generate-identity-verification-openapi.js <config.json...> [options]
 *
 * オプション:
 *   -o, --output <file>   出力先ファイル（省略時は標準出力）
 *   --format <yaml|json>  出力形式（デフォルト: yaml）
 *   --server <url>        servers[0].url（デフォルト: http://localhost:8080）
 *   --title <title>       info.title の上書き
 *   --no-crud             一覧取得（GET）・削除（DELETE）エンドポイントを含めない
 *   -h, --help            ヘルプ表示
 *
 * 例:
 *   node config/scripts/generate-identity-verification-openapi.js \
 *     config/examples/e2e/test-tenant/identity/investment-account-opening.json \
 *     -o documentation/openapi/generated/investment-account-opening.yaml
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ME_BASE = '/{tenant-id}/v1/me/identity-verification/applications';
const CALLBACK_BASE = '/{tenant-id}/internal/v1/identity-verification/callback';
const REGISTRATION_BASE = '/{tenant-id}/internal/v1/identity-verification/results';

/** この repo の JSON Schema 実装の独自キーワード（OpenAPI 非互換） */
const CUSTOM_SCHEMA_KEYWORDS = new Set(['store', 'respond']);

function usage() {
  const lines = [
    '使い方: node generate-identity-verification-openapi.js <config.json...> [options]',
    '',
    '身元確認設定JSONから OpenAPI 3.0.3 ドキュメントを生成します。',
    '',
    'オプション:',
    '  -o, --output <file>   出力先ファイル（省略時は標準出力）',
    '  --format <yaml|json>  出力形式（デフォルト: yaml）',
    '  --server <url>        サーバーURL（デフォルト: http://localhost:8080）',
    '  --title <title>       info.title の上書き',
    '  --no-crud             一覧取得・削除エンドポイントを含めない',
    '  -h, --help            このヘルプを表示',
  ];
  console.log(lines.join('\n'));
}

function parseArgs(argv) {
  const options = {
    inputs: [],
    output: null,
    format: 'yaml',
    server: 'http://localhost:8080',
    title: null,
    crud: true,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '-h':
      case '--help':
        usage();
        process.exit(0);
        break;
      case '-o':
      case '--output':
        options.output = argv[++i];
        break;
      case '--format':
        options.format = argv[++i];
        break;
      case '--server':
        options.server = argv[++i];
        break;
      case '--title':
        options.title = argv[++i];
        break;
      case '--no-crud':
        options.crud = false;
        break;
      default:
        if (arg.startsWith('-')) {
          throw new Error(`不明なオプション: ${arg}`);
        }
        options.inputs.push(arg);
    }
  }
  if (options.inputs.length === 0) {
    usage();
    throw new Error('設定JSONファイルを1つ以上指定してください');
  }
  if (!['yaml', 'json'].includes(options.format)) {
    throw new Error(`--format は yaml か json を指定してください: ${options.format}`);
  }
  return options;
}

/** 設定ファイルを読み込む。単一オブジェクトと配列の両形式に対応。 */
function loadConfigs(inputs) {
  const configs = [];
  for (const input of inputs) {
    const raw = fs.readFileSync(input, 'utf8');
    const parsed = JSON.parse(raw);
    const items = Array.isArray(parsed) ? parsed : [parsed];
    for (const item of items) {
      if (!item || typeof item !== 'object' || !item.type || (!item.processes && !item.registration)) {
        throw new Error(`身元確認設定として不正です（type と processes / registration が必要）: ${input}`);
      }
      configs.push({ source: path.relative(process.cwd(), input), config: item });
    }
  }
  return configs;
}

/**
 * JSON Schema から独自キーワードを除去する。
 * キーワード位置のみ除去し、properties 配下のプロパティ名（例: "store" という名前の項目）は保持する。
 */
function sanitizeSchema(schema) {
  if (Array.isArray(schema)) {
    return schema.map(sanitizeSchema);
  }
  if (!schema || typeof schema !== 'object') {
    return schema;
  }
  const out = {};
  for (const [key, value] of Object.entries(schema)) {
    if (CUSTOM_SCHEMA_KEYWORDS.has(key)) {
      continue;
    }
    if (key === 'required' && Array.isArray(value) && value.length === 0) {
      // OpenAPI 3.0 の required は minItems: 1 のため空配列は出力しない
      continue;
    }
    if (key === 'properties' || key === 'patternProperties') {
      const map = {};
      for (const [name, sub] of Object.entries(value || {})) {
        map[name] = sanitizeSchema(sub);
      }
      out[key] = map;
    } else if (
      (key === 'items' || key === 'additionalProperties' || key === 'not') &&
      typeof value === 'object'
    ) {
      out[key] = sanitizeSchema(value);
    } else if (['allOf', 'anyOf', 'oneOf'].includes(key) && Array.isArray(value)) {
      out[key] = value.map(sanitizeSchema);
    } else {
      out[key] = JSON.parse(JSON.stringify(value));
    }
  }
  return out;
}

/**
 * プロセスを endpoint 種別に分類する。
 * - "type": "callback"、または request.basic_auth を持つ（外部サービス認証＝ユーザートークン
 *   なしで呼ばれる）プロセス → callback
 * - 先頭の非callbackプロセス → initial（required_processes 宣言があれば continuation）
 * - それ以外 → continuation
 */
function classifyProcesses(config) {
  const classified = [];
  let initialAssigned = false;
  for (const [name, proc] of Object.entries(config.processes)) {
    const basicAuth = (proc.request && proc.request.basic_auth) || {};
    const isCallback = proc.type === 'callback' || Object.keys(basicAuth).length > 0;
    let kind;
    if (isCallback) {
      kind = 'callback';
    } else if (!initialAssigned && !hasRequiredProcesses(proc)) {
      kind = 'initial';
      initialAssigned = true;
    } else {
      kind = 'continuation';
    }
    classified.push({ name, proc, kind });
  }
  return classified;
}

function hasRequiredProcesses(proc) {
  return requiredProcesses(proc).length > 0;
}

/** dependencies.required_processes（現行）とプロセス直下 required_processes（旧形式）の両対応 */
function requiredProcesses(proc) {
  const deps = proc.dependencies || {};
  const current = Array.isArray(deps.required_processes) ? deps.required_processes : [];
  const legacy = Array.isArray(proc.required_processes) ? proc.required_processes : [];
  return current.length > 0 ? current : legacy;
}

/** response.body_mapping_rules からレスポンススキーマを導出する */
function buildResponseSchema(proc, includeId) {
  const schema = { type: 'object' };
  const properties = {};
  const required = [];
  const notes = [];

  if (includeId) {
    properties.id = {
      type: 'string',
      description: '身元確認申込みID。継続プロセスのパスパラメータ {id} に使用する。サーバーが必ず付与する。',
    };
    required.push('id');
  }

  const rules = (proc.response && proc.response.body_mapping_rules) || [];
  for (const rule of rules) {
    if (!rule || !rule.to) {
      continue;
    }
    if (rule.to === '*') {
      schema.additionalProperties = true;
      notes.push(`\`${rule.from}\` の内容がトップレベルに展開される。`);
      continue;
    }
    const property = {};
    if (Object.prototype.hasOwnProperty.call(rule, 'static_value')) {
      property.type = jsonType(rule.static_value);
      property.example = rule.static_value;
      property.description = '固定値（設定の static_value）。';
    } else if (rule.from) {
      property.description = `設定のマッピングルール（from: \`${rule.from}\`）により設定される。`;
    }
    setNestedProperty(properties, rule.to.split('.'), property);
  }

  if (Object.keys(properties).length > 0) {
    schema.properties = properties;
  }
  if (required.length > 0) {
    schema.required = required;
  }
  if (rules.length === 0 && !includeId) {
    notes.push('この設定では response.body_mapping_rules が定義されていないため、ボディは空のオブジェクトになる。');
  }
  if (notes.length > 0) {
    schema.description = notes.join(' ');
  }
  return schema;
}

/** "a.b.c" 形式の to パスをネストした object スキーマとして構築する */
function setNestedProperty(properties, segments, property) {
  const [head, ...rest] = segments;
  if (rest.length === 0) {
    properties[head] = property;
    return;
  }
  if (!properties[head] || properties[head].type !== 'object') {
    properties[head] = { type: 'object', properties: {} };
  }
  if (!properties[head].properties) {
    properties[head].properties = {};
  }
  setNestedProperty(properties[head].properties, rest, property);
}

function jsonType(value) {
  if (value === null) return 'string';
  if (Array.isArray(value)) return 'array';
  switch (typeof value) {
    case 'number':
      return Number.isInteger(value) ? 'integer' : 'number';
    case 'boolean':
      return 'boolean';
    case 'object':
      return 'object';
    default:
      return 'string';
  }
}

const KIND_LABELS = {
  initial: '初回申込みプロセス',
  continuation: '継続プロセス',
  callback: 'コールバックプロセス（外部サービスからの通知）',
};

/** プロセス設定から description（Markdown）を組み立てる */
function buildDescription(config, name, proc, kind) {
  const lines = [];
  lines.push(`**処理タイプ**: ${KIND_LABELS[kind]}`);
  lines.push('');
  lines.push(`設定 \`type: ${config.type}\` のプロセス \`${name}\` を実行する。`);

  const flow = [];
  const hasSchema = !!(proc.request && proc.request.schema);
  flow.push(
    hasSchema
      ? '1. **リクエスト検証**: JSON Schema によるバリデーション（下記 Request Body 参照）'
      : '1. **リクエスト検証**: スキーマ定義なし（任意のボディを受け付ける）'
  );

  const verifications = (proc.pre_hook && proc.pre_hook.verifications) || [];
  const additionalParameters = (proc.pre_hook && proc.pre_hook.additional_parameters) || [];
  if (verifications.length > 0 || additionalParameters.length > 0) {
    const parts = [];
    for (const verification of verifications) {
      if (verification.type === 'duplicate_application') {
        parts.push('`duplicate_application`（進行中の同種申込みがあると 400）');
      } else if (verification.type === 'assert') {
        const messages = ((verification.details || {}).assertions || [])
          .map((assertion) => assertion.message)
          .filter(Boolean);
        parts.push(`\`assert\`（${messages.length > 0 ? messages.join(' / ') : '設定された条件を検証'}）`);
      } else {
        parts.push(`\`${verification.type}\``);
      }
    }
    for (const parameter of additionalParameters) {
      parts.push(`外部パラメータ解決: \`${parameter.type}\``);
    }
    flow.push(`2. **事前処理 (pre_hook)**: ${parts.join('、')}`);
  } else {
    flow.push('2. **事前処理 (pre_hook)**: なし');
  }

  const executionType = (proc.execution && proc.execution.type) || 'no_action';
  const executionLabels = {
    http_request: '外部サービスAPIを呼び出す（http_request）',
    http_requests: '複数の外部サービスAPIを順次呼び出す（http_requests）',
    mock: 'モック実行（mock）',
    no_action: '外部API連携なし（no_action）',
  };
  flow.push(`3. **実行 (execution)**: ${executionLabels[executionType] || `\`${executionType}\``}`);

  if (proc.transition && Object.keys(proc.transition).length > 0) {
    flow.push('4. **ステータス遷移 (transition)**: 下記の条件で判定');
  } else {
    flow.push('4. **ステータス遷移 (transition)**: 定義なし（デフォルト遷移）');
  }

  const storeRules = (proc.store && proc.store.application_details_mapping_rules) || [];
  if (storeRules.length > 0) {
    const keys = storeRules.map((rule) => (rule.to === '*' ? '（リクエスト全体）' : `\`${rule.to}\``));
    flow.push(`5. **保存 (store)**: application_details に保存 — ${keys.join('、')}`);
  }

  lines.push('');
  lines.push('**処理フロー**:');
  lines.push('');
  lines.push(...flow);

  if (hasRequiredProcesses(proc)) {
    lines.push('');
    lines.push(`**前提プロセス**: ${requiredProcesses(proc).map((p) => `\`${p}\``).join('、')} の完了が必要`);
    const allowRetry = (proc.dependencies || {}).allow_retry || proc.allow_retry;
    if (allowRetry) {
      lines.push('');
      lines.push('リトライ実行が許可されている（allow_retry: true）。');
    }
  }

  if (kind === 'callback' && config.external_application_id_param) {
    lines.push('');
    lines.push(
      `対象申込みはリクエストボディの \`${config.external_application_id_param}\`（external_application_id）で特定される。`
    );
  }

  if (proc.transition && Object.keys(proc.transition).length > 0) {
    lines.push('');
    lines.push('**ステータス遷移条件**:');
    lines.push('');
    lines.push('```json');
    lines.push(JSON.stringify(proc.transition, null, 2));
    lines.push('```');
  }

  return lines.join('\n');
}

/** 設定の result セクションからタグ説明（verified_claims 等）を組み立てる */
function buildTagDescription(config, source) {
  const lines = [];
  lines.push(`身元確認タイプ \`${config.type}\` のAPI（生成元: ${source}）。`);
  if (config.external_service) {
    lines.push(`外部サービス: \`${config.external_service}\``);
  }
  const result = config.result || {};
  const verifiedClaimsRules = result.verified_claims_mapping_rules || [];
  if (verifiedClaimsRules.length > 0) {
    lines.push('');
    lines.push('承認時に設定される verified_claims:');
    for (const rule of verifiedClaimsRules) {
      const source_ = Object.prototype.hasOwnProperty.call(rule, 'static_value')
        ? `固定値 \`${JSON.stringify(rule.static_value)}\``
        : `\`${rule.from}\``;
      lines.push(`- \`${rule.to}\` ← ${source_}`);
    }
  }
  if (result.user_status) {
    lines.push('');
    lines.push(`承認時のユーザーステータス: \`${result.user_status}\``);
  }
  return lines.join('\n');
}

function buildOperation(config, name, proc, kind, tag) {
  const summaryLabels = {
    initial: `身元確認申込み（${name}）`,
    continuation: `継続プロセス実行（${name}）`,
    callback: `コールバック受信（${name}）`,
  };
  const operation = {
    tags: [tag],
    summary: summaryLabels[kind],
    description: buildDescription(config, name, proc, kind),
    operationId: sanitizeOperationId(`${config.type}-${name}`),
  };

  if (kind === 'callback') {
    operation.security = [{ BasicAuth: [] }];
  } else {
    operation.security = [{ OAuth2: ['identity_verification_application'] }];
  }

  const parameters = [{ $ref: '#/components/parameters/TenantId' }];
  if (kind === 'continuation') {
    parameters.push({ $ref: '#/components/parameters/ApplicationId' });
  }
  operation.parameters = parameters;

  const rawSchema = proc.request && proc.request.schema;
  if (rawSchema) {
    const schema = sanitizeSchema(rawSchema);
    const hasRequiredFields = Array.isArray(schema.required) && schema.required.length > 0;
    operation.requestBody = {
      required: hasRequiredFields,
      content: { 'application/json': { schema } },
    };
  } else {
    operation.requestBody = {
      required: false,
      description: 'この設定ではスキーマが定義されていないため、任意のJSONオブジェクトを受け付ける。',
      content: { 'application/json': { schema: { type: 'object', additionalProperties: true } } },
    };
  }

  operation.responses = {
    200: {
      description: '処理成功',
      content: {
        'application/json': {
          schema: buildResponseSchema(proc, kind === 'initial'),
        },
      },
    },
    ...errorRefs(ERROR_CODES[kind]),
  };
  return operation;
}

function sanitizeOperationId(value) {
  return value.replace(/[^A-Za-z0-9_-]/g, '-');
}

function buildListOperation() {
  return {
    tags: ['共通'],
    summary: '身元確認申込み一覧取得',
    description: '認証ユーザー自身の身元確認申込み一覧を取得する。',
    operationId: 'list-identity-verification-applications',
    security: [{ OAuth2: ['identity_verification_application'] }],
    parameters: [
      { $ref: '#/components/parameters/TenantId' },
      { name: 'id', in: 'query', required: false, schema: { type: 'string', format: 'uuid' }, description: '申込みIDでの絞り込み' },
      { name: 'type', in: 'query', required: false, schema: { type: 'string' }, description: '身元確認タイプでの絞り込み' },
      { name: 'status', in: 'query', required: false, schema: { type: 'string' }, description: 'ステータスでの絞り込み' },
      { name: 'from', in: 'query', required: false, schema: { type: 'string', format: 'date-time' }, description: '絞り込み開始日時（ISO 8601）' },
      { name: 'to', in: 'query', required: false, schema: { type: 'string', format: 'date-time' }, description: '絞り込み終了日時（ISO 8601）' },
      { name: 'limit', in: 'query', required: false, schema: { type: 'integer' }, description: '取得件数上限' },
      { name: 'offset', in: 'query', required: false, schema: { type: 'integer' }, description: '取得開始位置' },
    ],
    responses: {
      200: {
        description: '申込み一覧',
        content: {
          'application/json': {
            schema: {
              type: 'object',
              properties: {
                list: { type: 'array', items: { type: 'object', additionalProperties: true } },
                total_count: { type: 'integer' },
                limit: { type: 'integer' },
                offset: { type: 'integer' },
              },
            },
          },
        },
      },
      ...errorRefs(ERROR_CODES.list),
    },
  };
}

/**
 * registration 型設定（processes を持たず、外部サービスが確認済み結果を直接登録する形式）の
 * オペレーションを生成する。verified_claims は完全上書き（setVerifiedClaims）で保存される。
 */
function buildRegistrationOperation(config, tag) {
  const registration = config.registration;
  const operation = {
    tags: [tag],
    summary: `身元確認結果の直接登録（${config.type}）`,
    description: [
      '**処理タイプ**: 外部サービスからの確認済み結果の直接登録',
      '',
      `外部サービスで完了した身元確認の結果を \`${config.type}\` として登録する。`,
      '承認フローを経由せず、result のマッピングルールに基づいて verified_claims が即時保存される（完全上書き）。',
    ].join('\n'),
    operationId: sanitizeOperationId(`register-${config.type}-result`),
    security: [{ BasicAuth: [] }],
    parameters: [{ $ref: '#/components/parameters/TenantId' }],
  };

  const rawSchema = registration.request_validation_schema;
  if (rawSchema) {
    const schema = sanitizeSchema(rawSchema);
    operation.requestBody = {
      required: true,
      content: { 'application/json': { schema } },
    };
  } else {
    operation.requestBody = {
      required: false,
      content: { 'application/json': { schema: { type: 'object', additionalProperties: true } } },
    };
  }

  operation.responses = {
    200: {
      description: '登録成功',
      content: {
        'application/json': {
          schema: buildResponseSchema({ response: registration.response }, false),
        },
      },
    },
    ...errorRefs(ERROR_CODES.registration),
  };
  return operation;
}

function buildDeleteOperation(config, tag) {
  return {
    tags: [tag],
    summary: `身元確認申込み削除（${config.type}）`,
    description: '指定した身元確認申込みを削除する。',
    operationId: sanitizeOperationId(`delete-${config.type}-application`),
    security: [{ OAuth2: ['identity_verification_application_delete'] }],
    parameters: [
      { $ref: '#/components/parameters/TenantId' },
      { $ref: '#/components/parameters/ApplicationId' },
    ],
    responses: {
      200: {
        description: '削除成功（空のオブジェクトを返す）',
        content: {
          'application/json': { schema: { type: 'object' } },
        },
      },
      ...errorRefs(ERROR_CODES.delete),
    },
  };
}

/**
 * エンドポイント種別ごとのエラーレスポンスコード。
 * エラーレスポンスの定義内容は swagger-resource-owner-ja.yaml に合わせる。
 *
 * 継続プロセスは初回と同じエラー面を持つ（スコープ検証は SecurityConfig で同一、
 * 外部サービスエラーは ApplyingResult.errorResponse がステータスコードを透過）ため、
 * 初回のフルセット + 404（申込みIDを指定するため）とする。
 */
const ERROR_CODES = {
  initial: [400, 401, 403, 408, 409, 413, 415, 422, 429, 500, 502, 503, 504],
  continuation: [400, 401, 403, 404, 408, 409, 413, 415, 422, 429, 500, 502, 503, 504],
  callback: [400, 401, 403, 500],
  registration: [400, 401, 404, 500],
  list: [401, 500],
  delete: [400, 401, 404, 500],
};

const CODE_TO_RESPONSE = {
  400: 'BadRequest',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'NotFound',
  408: 'RequestTimeout',
  409: 'Conflict',
  413: 'PayloadTooLarge',
  415: 'UnsupportedMediaType',
  422: 'UnprocessableEntity',
  429: 'TooManyRequests',
  500: 'InternalServerError',
  502: 'BadGateway',
  503: 'ServiceUnavailable',
  504: 'GatewayTimeout',
};

function errorRefs(codes) {
  const refs = {};
  for (const code of codes) {
    refs[code] = { $ref: `#/components/responses/${CODE_TO_RESPONSE[code]}` };
  }
  return refs;
}

/**
 * エラーレスポンス components を生成する。
 * 内容は documentation/openapi/swagger-resource-owner-ja.yaml の定義と同一。
 */
function buildErrorResponseComponents() {
  const errorMessagesProperty = {
    type: 'array',
    items: { type: 'string' },
    description: 'エラーメッセージ配列。オプショナル。',
  };

  const errorDetailsProperty = (statusCategory, statusCode) => ({
    type: 'object',
    description: '外部サービスエラー時の詳細情報（execution_failed の場合のみ）。オプショナル。',
    required: ['execution_type', 'status_category', 'status_code'],
    properties: {
      execution_type: { type: 'string', example: 'http_request' },
      status_category: { type: 'string', example: statusCategory },
      status_code: { type: 'integer', example: statusCode },
      response_body: { type: 'object', description: '外部サービスのレスポンスボディ（オプショナル）' },
    },
  });

  /** BadRequest / NotFound 以外の共通形式（内部エラー例 + 外部サービスエラー例） */
  const standardErrorResponse = (def) => {
    const internalExample = {};
    if (def.internal.summary) {
      internalExample.summary = def.internal.summary;
      internalExample.description = def.internal.description;
    }
    internalExample.value = def.internal.value;

    const externalExample = {};
    if (def.external.summary) {
      externalExample.summary = def.external.summary;
      externalExample.description = def.external.description;
    }
    externalExample.value = {
      error: 'execution_failed',
      error_description: 'execution failed',
      error_messages: [def.external.message],
      error_details: {
        execution_type: 'http_request',
        status_category: def.statusCategory,
        status_code: def.statusCode,
        response_body: def.external.responseBody,
      },
    };

    return {
      description: def.description,
      content: {
        'application/json': {
          schema: {
            type: 'object',
            required: ['error', 'error_description'],
            properties: {
              error: { type: 'string', example: def.errorCode, description: def.errorCodeDescription },
              error_description: { type: 'string', example: def.errorDescriptionExample },
              error_messages: errorMessagesProperty,
              error_details: errorDetailsProperty(def.statusCategory, def.statusCode),
            },
          },
          examples: {
            [def.internal.name]: internalExample,
            [`external_service_${def.statusCode}`]: externalExample,
          },
        },
      },
    };
  };

  const badRequest = {
    description: 'リクエストパラメータが不正、検証エラー、プロセスシーケンスエラー',
    content: {
      'application/json': {
        schema: {
          type: 'object',
          required: ['error', 'error_description'],
          properties: {
            error: {
              type: 'string',
              example: 'invalid_request',
              description: [
                'エラーコード。以下の値を取りうる:',
                '',
                '| error 値 | 説明 |',
                '|---------|------|',
                '| `invalid_request` | リクエストパラメータの不正、JSON Schema 検証エラー、プロセスシーケンスエラー |',
                '| `pre_hook_validation_failed` | Pre-hook バリデーション失敗（ユーザークレーム検証、重複申込み検知等） |',
                '| `execution_failed` | 外部サービス実行エラー（`error_details` に詳細が含まれる） |',
              ].join('\n'),
            },
            error_description: { type: 'string', example: 'Request validation failed' },
            error_messages: {
              type: 'array',
              items: { type: 'string' },
              description:
                '検証エラーの詳細メッセージ配列（JSON Schema検証エラー、プロセスシーケンスエラー等）。オプショナル。',
            },
            error_details: {
              type: 'object',
              description: '外部サービス実行時のエラー詳細情報（`execution_failed` の場合のみ）。オプショナル。',
              required: ['execution_type', 'status_category', 'status_code'],
              properties: {
                execution_type: { type: 'string', description: '実行タイプ', example: 'http_request' },
                status_category: {
                  type: 'string',
                  description: 'HTTPステータスカテゴリ',
                  enum: ['client_error', 'server_error'],
                },
                status_code: {
                  type: 'integer',
                  description: '外部サービスから返されたHTTPステータスコード',
                  example: 400,
                },
                response_body: { type: 'object', description: '外部サービスのレスポンスボディ（オプショナル）' },
              },
            },
          },
        },
        examples: {
          schema_validation_error: {
            value: {
              error: 'invalid_request',
              error_description:
                'The identity verification request is invalid. Please review your input for missing or incorrect fields.',
              error_messages: ['last_name is missing', 'first_name is missing', 'birthdate is missing'],
            },
          },
          type_mismatch_error: {
            value: {
              error: 'invalid_request',
              error_description:
                'The identity verification request is invalid. Please review your input for missing or incorrect fields.',
              error_messages: ['age is not a integer', 'address is not a object'],
            },
          },
          process_sequence_error: {
            value: {
              error: 'invalid_request',
              error_description: 'Process sequence violation',
              error_messages: ["process 'complete-verification' is not available at current state"],
            },
          },
          pre_hook_validation_failed: {
            value: {
              error: 'pre_hook_validation_failed',
              error_description: 'Pre-hook validation failed for identity verification request',
              error_messages: [
                'User claim verification failed. unmatched: $.request_body.email_address, user:email',
              ],
            },
          },
          external_service_400: {
            value: {
              error: 'execution_failed',
              error_description: 'execution failed',
              error_messages: ['External service returned 400: invalid_request'],
              error_details: {
                execution_type: 'http_request',
                status_category: 'client_error',
                status_code: 400,
                response_body: {
                  error: 'invalid_request',
                  error_description: 'The request is missing required parameters or contains invalid values',
                },
              },
            },
          },
        },
      },
    },
  };

  const notFound = {
    description: 'リソースが存在しない',
    content: {
      'application/json': {
        schema: {
          type: 'object',
          required: ['error', 'error_description'],
          properties: {
            error: {
              type: 'string',
              example: 'invalid_request',
              description: 'エラーコード（実装では `invalid_request` を使用）',
            },
            error_description: { type: 'string', example: 'The requested resource was not found' },
            error_messages: errorMessagesProperty,
          },
        },
        examples: {
          application_not_found: {
            value: {
              error: 'invalid_request',
              error_description: 'identity verification application not found',
              error_messages: ['identity verification application not found'],
            },
          },
        },
      },
    },
  };

  return {
    BadRequest: badRequest,
    Unauthorized: standardErrorResponse({
      description: '認証エラー。アクセストークンが無効または期限切れ、または外部サービスから401エラーが返された場合',
      errorCode: 'invalid_token',
      errorCodeDescription:
        'エラーコード。`invalid_token`（アクセストークンが無効または期限切れ）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The access token is invalid or expired',
      statusCategory: 'client_error',
      statusCode: 401,
      internal: {
        name: 'invalid_token',
        value: { error: 'invalid_token', error_description: 'The access token is invalid or expired' },
      },
      external: {
        message: 'External service returned 401: invalid_token',
        responseBody: {
          error: 'invalid_token',
          error_description: 'The access token provided is invalid or expired',
        },
      },
    }),
    Forbidden: standardErrorResponse({
      description: '権限エラー。スコープ不足、または外部サービスから403エラーが返された場合',
      errorCode: 'insufficient_scope',
      errorCodeDescription:
        'エラーコード。`insufficient_scope`（スコープ不足）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The request requires higher privileges than provided by the access token',
      statusCategory: 'client_error',
      statusCode: 403,
      internal: {
        name: 'insufficient_scope',
        value: {
          error: 'insufficient_scope',
          error_description: 'The request requires higher privileges than provided by the access token',
        },
      },
      external: {
        message: 'External service returned 403: insufficient_scope',
        responseBody: {
          error: 'insufficient_scope',
          error_description: 'The request requires specific permissions',
          scope: 'identity_verification_application_delete',
        },
      },
    }),
    NotFound: notFound,
    RequestTimeout: standardErrorResponse({
      description: 'リクエストタイムアウト、または外部サービスから408エラーが返された場合',
      errorCode: 'request_timeout',
      errorCodeDescription:
        'エラーコード。`request_timeout`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The request timed out',
      statusCategory: 'client_error',
      statusCode: 408,
      internal: {
        name: 'request_timeout',
        value: { error: 'request_timeout', error_description: 'The request timed out' },
      },
      external: {
        message: 'External service returned 408: request_timeout',
        responseBody: {
          error: 'request_timeout',
          error_description: 'The server timed out waiting for the request',
          timeout_seconds: 30,
        },
      },
    }),
    Conflict: standardErrorResponse({
      description: 'リソースの状態が競合している、または外部サービスから409エラーが返された場合',
      errorCode: 'conflict',
      errorCodeDescription: 'エラーコード。`conflict`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'A verification application is already in progress',
      statusCategory: 'client_error',
      statusCode: 409,
      internal: {
        name: 'conflict',
        summary: '内部エラー - 競合',
        description: 'idp-server内部で申込みの競合が検出された場合',
        value: {
          error: 'conflict',
          error_description: 'A verification application is already in progress',
          error_messages: ['Cannot create new application while previous application is still active'],
        },
      },
      external: {
        summary: '外部サービスエラー - 状態遷移エラー',
        description:
          '外部サービスから409が返された場合。response_bodyには状態遷移情報（current_state, requested_state等）を含む可能性がある。',
        message: 'External service returned 409: invalid_request',
        responseBody: {
          error: 'invalid_request',
          error_description: "Cannot transition from 'approved' to 'applying'. Invalid state transition.",
          current_state: 'approved',
          requested_state: 'applying',
        },
      },
    }),
    PayloadTooLarge: standardErrorResponse({
      description: 'ペイロードが大きすぎる、または外部サービスから413エラーが返された場合',
      errorCode: 'payload_too_large',
      errorCodeDescription:
        'エラーコード。`payload_too_large`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The request payload is too large',
      statusCategory: 'client_error',
      statusCode: 413,
      internal: {
        name: 'payload_too_large',
        summary: '内部エラー - ペイロード超過',
        description: 'idp-server内部でペイロードサイズ制限に達した場合',
        value: { error: 'payload_too_large', error_description: 'The request payload is too large' },
      },
      external: {
        summary: '外部サービスエラー - ペイロード超過',
        description:
          '外部サービスから413が返された場合。response_bodyにはペイロードサイズ情報（max_size_bytes, received_size_bytes等）を含む可能性がある。',
        message: 'External service returned 413: payload_too_large',
        responseBody: {
          error: 'payload_too_large',
          error_description: 'The request payload exceeds the maximum allowed size',
          max_size_bytes: 5242880,
          received_size_bytes: 10485760,
        },
      },
    }),
    UnsupportedMediaType: standardErrorResponse({
      description: 'サポートされていないメディアタイプ、または外部サービスから415エラーが返された場合',
      errorCode: 'unsupported_media_type',
      errorCodeDescription:
        'エラーコード。`unsupported_media_type`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The media type is not supported',
      statusCategory: 'client_error',
      statusCode: 415,
      internal: {
        name: 'unsupported_media_type',
        summary: '内部エラー - 非サポートメディアタイプ',
        description: 'idp-server内部でメディアタイプが拒否された場合',
        value: { error: 'unsupported_media_type', error_description: 'The media type is not supported' },
      },
      external: {
        summary: '外部サービスエラー - 非サポートメディアタイプ',
        description:
          '外部サービスから415が返された場合。response_bodyにはサポートされるメディアタイプ情報（supported_types, received_type等）を含む可能性がある。',
        message: 'External service returned 415: unsupported_media_type',
        responseBody: {
          error: 'unsupported_media_type',
          error_description: 'The media type is not supported',
          supported_types: ['image/png', 'image/jpeg'],
          received_type: 'image/gif',
        },
      },
    }),
    UnprocessableEntity: standardErrorResponse({
      description: 'バリデーションエラー、または外部サービスから422エラーが返された場合',
      errorCode: 'validation_error',
      errorCodeDescription:
        'エラーコード。`validation_error`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The request data failed validation',
      statusCategory: 'client_error',
      statusCode: 422,
      internal: {
        name: 'validation_error',
        summary: '内部エラー - バリデーションエラー',
        description: 'idp-server内部でバリデーションエラーが発生した場合',
        value: { error: 'validation_error', error_description: 'The request data failed validation' },
      },
      external: {
        summary: '外部サービスエラー - バリデーションエラー',
        description:
          '外部サービスから422が返された場合。response_bodyにはバリデーションエラー詳細（validation_errors等）を含む可能性がある。',
        message: 'External service returned 422: validation_error',
        responseBody: {
          error: 'validation_error',
          error_description: 'The request data failed validation',
          validation_errors: [
            { field: 'birthdate', message: 'Invalid date format. Expected YYYY-MM-DD' },
            { field: 'document_number', message: 'Document number contains invalid characters' },
          ],
        },
      },
    }),
    TooManyRequests: standardErrorResponse({
      description: 'レート制限超過、または外部サービスから429エラーが返された場合',
      errorCode: 'too_many_requests',
      errorCodeDescription:
        'エラーコード。`too_many_requests`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'Too many requests. Please try again later.',
      statusCategory: 'client_error',
      statusCode: 429,
      internal: {
        name: 'too_many_requests',
        summary: '内部エラー - レート制限超過',
        description: 'idp-server内部でレート制限に達した場合',
        value: { error: 'too_many_requests', error_description: 'Too many requests. Please try again later.' },
      },
      external: {
        summary: '外部サービスエラー - レート制限超過',
        description:
          '外部サービスから429が返された場合。response_bodyにはレート制限情報（retry_after等）を含む可能性がある。',
        message: 'External service returned 429: too_many_requests',
        responseBody: {
          error: 'too_many_requests',
          error_description: 'Rate limit exceeded. Please try again later.',
          retry_after: 60,
        },
      },
    }),
    InternalServerError: standardErrorResponse({
      description: 'サーバー内部エラー、または外部サービスから500エラーが返された場合',
      errorCode: 'server_error',
      errorCodeDescription:
        'エラーコード。`server_error`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'An internal server error occurred',
      statusCategory: 'server_error',
      statusCode: 500,
      internal: {
        name: 'internal_error',
        value: { error: 'server_error', error_description: 'An internal server error occurred' },
      },
      external: {
        message: 'External service returned 500: server_error',
        responseBody: {
          error: 'server_error',
          error_description: 'An unexpected error occurred on the external service',
          error_id: 'err_abc123xyz',
        },
      },
    }),
    BadGateway: standardErrorResponse({
      description: 'ゲートウェイエラー、または外部サービスから502エラーが返された場合',
      errorCode: 'bad_gateway',
      errorCodeDescription:
        'エラーコード。`bad_gateway`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The upstream server returned an invalid response',
      statusCategory: 'server_error',
      statusCode: 502,
      internal: {
        name: 'bad_gateway',
        value: { error: 'bad_gateway', error_description: 'The upstream server returned an invalid response' },
      },
      external: {
        message: 'External service returned 502: bad_gateway',
        responseBody: {
          error: 'bad_gateway',
          error_description: 'The upstream server returned an invalid response',
          upstream_error: 'Connection refused',
        },
      },
    }),
    ServiceUnavailable: standardErrorResponse({
      description: 'サービス一時利用不可、または外部サービスから503エラーが返された場合',
      errorCode: 'service_unavailable',
      errorCodeDescription:
        'エラーコード。`service_unavailable`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The service is temporarily unavailable. Please try again later.',
      statusCategory: 'server_error',
      statusCode: 503,
      internal: {
        name: 'service_unavailable',
        value: {
          error: 'service_unavailable',
          error_description: 'The service is temporarily unavailable. Please try again later.',
        },
      },
      external: {
        message: 'External service returned 503: service_unavailable',
        responseBody: {
          error: 'service_unavailable',
          error_description: 'The external service is temporarily unavailable. Please retry after some time.',
          retryable: true,
          retry_after: 60,
        },
      },
    }),
    GatewayTimeout: standardErrorResponse({
      description: '外部サービスタイムアウト、または外部サービスから504エラーが返された場合',
      errorCode: 'gateway_timeout',
      errorCodeDescription:
        'エラーコード。`gateway_timeout`（内部エラー）または `execution_failed`（外部サービスエラー）',
      errorDescriptionExample: 'The external service request timed out',
      statusCategory: 'server_error',
      statusCode: 504,
      internal: {
        name: 'gateway_timeout',
        value: { error: 'gateway_timeout', error_description: 'The external service request timed out' },
      },
      external: {
        message: 'External service returned 504: gateway_timeout',
        responseBody: {
          error: 'gateway_timeout',
          error_description: 'The gateway did not receive a timely response from the upstream server',
          timeout_seconds: 30,
        },
      },
    }),
  };
}

function buildComponents(serverUrl) {
  return {
    securitySchemes: {
      OAuth2: {
        type: 'oauth2',
        flows: {
          authorizationCode: {
            authorizationUrl: `${serverUrl}/{tenant-id}/v1/authorizations`,
            tokenUrl: `${serverUrl}/{tenant-id}/v1/tokens`,
            scopes: {
              identity_verification_application: '身元確認の申込みAPIに必要なスコープ',
              identity_verification_application_delete: '身元確認の申込み削除に必要なスコープ',
            },
          },
        },
      },
      BasicAuth: {
        type: 'http',
        scheme: 'basic',
        description: 'コールバック用。設定の request.basic_auth に定義した資格情報。',
      },
    },
    parameters: {
      TenantId: {
        name: 'tenant-id',
        in: 'path',
        required: true,
        schema: { type: 'string', format: 'uuid' },
        description: 'テナントID',
      },
      ApplicationId: {
        name: 'id',
        in: 'path',
        required: true,
        schema: { type: 'string', format: 'uuid' },
        description: '身元確認申込みID（初回申込みレスポンスの id）',
      },
    },
    responses: buildErrorResponseComponents(),
  };
}

function generateOpenApi(configEntries, options) {
  const types = configEntries.map((entry) => entry.config.type);
  const sources = [...new Set(configEntries.map((entry) => entry.source))];

  const doc = {
    openapi: '3.0.3',
    info: {
      title: options.title || `身元確認申込みAPI（${types.join(', ')}）`,
      description: [
        '身元確認設定JSONから自動生成されたAPI仕様書。',
        '',
        `生成元: ${sources.join(', ')}`,
        '',
        '再生成するには: `node config/scripts/generate-identity-verification-openapi.js`（手動編集しないこと）',
      ].join('\n'),
      version: '1.0.0',
    },
    servers: [{ url: options.server }],
    tags: [],
    paths: {},
    components: buildComponents(options.server),
  };

  for (const { source, config } of configEntries) {
    const tag = config.type;
    doc.tags.push({ name: tag, description: buildTagDescription(config, source) });

    if (config.registration) {
      doc.paths[`${REGISTRATION_BASE}/${config.type}/registration`] = {
        post: buildRegistrationOperation(config, tag),
      };
    }
    if (!config.processes) {
      continue;
    }

    for (const { name, proc, kind } of classifyProcesses(config)) {
      let pathKey;
      if (kind === 'callback') {
        pathKey = `${CALLBACK_BASE}/${config.type}/${name}`;
      } else if (kind === 'initial') {
        pathKey = `${ME_BASE}/${config.type}/${name}`;
      } else {
        pathKey = `${ME_BASE}/${config.type}/{id}/${name}`;
      }
      if (doc.paths[pathKey]) {
        console.error(`警告: パスが重複しています（スキップ）: ${pathKey}`);
        continue;
      }
      doc.paths[pathKey] = { post: buildOperation(config, name, proc, kind, tag) };
    }

    if (options.crud) {
      doc.paths[`${ME_BASE}/${config.type}/{id}`] = {
        delete: buildDeleteOperation(config, tag),
      };
    }
  }

  if (options.crud) {
    doc.tags.push({ name: '共通', description: '身元確認タイプに依存しない共通API' });
    doc.paths[ME_BASE] = { get: buildListOperation() };
  }

  return doc;
}

// ---------------------------------------------------------------------------
// YAML シリアライザ（依存パッケージなしで動かすための最小実装）
// ---------------------------------------------------------------------------

const YAML_PLAIN_KEY = /^[A-Za-z0-9_][A-Za-z0-9_./{}-]*$/;
const YAML_UNSAFE_SCALAR =
  /^(true|false|yes|no|on|off|null|~|y|n)$|^[-?:,[\]{}#&*!|>'"%@`]|[:#]\s|\s$|^\s|^$|^[\d.+-]/i;

function yamlKey(key) {
  // 数値キー（HTTPステータス等）は OpenAPI 仕様に合わせて文字列として出力する
  if (/^\d+$/.test(key)) {
    return JSON.stringify(key);
  }
  return YAML_PLAIN_KEY.test(key) ? key : JSON.stringify(key);
}

function yamlScalar(value, indent) {
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'boolean' || typeof value === 'number') return String(value);
  const str = String(value);
  if (str.includes('\n')) {
    const pad = ' '.repeat(indent + 2);
    const body = str
      .split('\n')
      .map((line) => (line.length > 0 ? pad + line : ''))
      .join('\n');
    return `|-\n${body}`;
  }
  if (YAML_UNSAFE_SCALAR.test(str)) {
    return JSON.stringify(str);
  }
  return str;
}

function toYaml(value, indent = 0) {
  const pad = ' '.repeat(indent);
  if (Array.isArray(value)) {
    if (value.length === 0) return `${pad}[]`;
    return value
      .map((item) => {
        if (item !== null && typeof item === 'object') {
          const nested = toYaml(item, indent + 2);
          return `${pad}-${nested.slice(pad.length + 1)}`;
        }
        return `${pad}- ${yamlScalar(item, indent)}`;
      })
      .join('\n');
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value);
    if (entries.length === 0) return `${pad}{}`;
    return entries
      .map(([key, val]) => {
        if (val !== null && typeof val === 'object') {
          const isEmpty = Array.isArray(val) ? val.length === 0 : Object.keys(val).length === 0;
          if (isEmpty) {
            return `${pad}${yamlKey(key)}: ${Array.isArray(val) ? '[]' : '{}'}`;
          }
          return `${pad}${yamlKey(key)}:\n${toYaml(val, indent + 2)}`;
        }
        return `${pad}${yamlKey(key)}: ${yamlScalar(val, indent)}`;
      })
      .join('\n');
  }
  return `${pad}${yamlScalar(value, indent)}`;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

function main() {
  const options = parseArgs(process.argv.slice(2));
  const configEntries = loadConfigs(options.inputs);
  const doc = generateOpenApi(configEntries, options);

  const output =
    options.format === 'json' ? JSON.stringify(doc, null, 2) + '\n' : toYaml(doc) + '\n';

  if (options.output) {
    fs.mkdirSync(path.dirname(options.output), { recursive: true });
    fs.writeFileSync(options.output, output, 'utf8');
    console.error(`生成完了: ${options.output}（${Object.keys(doc.paths).length} paths）`);
  } else {
    process.stdout.write(output);
  }
}

try {
  main();
} catch (error) {
  console.error(`エラー: ${error.message}`);
  process.exit(1);
}
