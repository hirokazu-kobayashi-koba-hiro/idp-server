/**
 * FIDO2 Credential Store using SQLite
 *
 * Provides persistent storage for FIDO2 credentials to enable
 * separate registration and authentication test scenarios.
 */

import sql from "k6/x/sql";
import driver from "k6/x/sql/driver/sqlite3";

const DB_PATH = "./performance-test/data/fido2-credentials.db";

let db = null;

/**
 * Initialize database connection and create table if not exists
 */
export function initDb() {
  if (db) return db;

  db = sql.open(driver, DB_PATH);

  db.exec(`
    CREATE TABLE IF NOT EXISTS credentials (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      credential_id TEXT UNIQUE NOT NULL,
      private_key_jwk TEXT NOT NULL,
      email TEXT NOT NULL,
      user_id TEXT,
      sign_count INTEGER DEFAULT 0,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      last_used_at TEXT
    )
  `);

  // Create index for faster lookups
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_credentials_sign_count
    ON credentials(sign_count)
  `);

  return db;
}

/**
 * Save a new credential to the database
 * @param {string} credentialId - Base64URL encoded credential ID
 * @param {Object} privateKeyJwk - JWK format private key object
 * @param {string} email - User email
 * @param {string} userId - Optional user ID
 */
export function saveCredential(credentialId, privateKeyJwk, email, userId = null) {
  const database = initDb();
  const jwkJson = JSON.stringify(privateKeyJwk).replace(/'/g, "''"); // Escape single quotes

  database.exec(`
    INSERT OR REPLACE INTO credentials (credential_id, private_key_jwk, email, user_id, sign_count)
    VALUES ('${credentialId}', '${jwkJson}', '${email}', ${userId ? `'${userId}'` : 'NULL'}, 0)
  `);
}

/**
 * Get a credential by ID
 */
export function getCredential(credentialId) {
  const database = initDb();

  const results = database.query(`
    SELECT credential_id, private_key_jwk, email, user_id, sign_count
    FROM credentials
    WHERE credential_id = '${credentialId}'
  `);

  if (results.length === 0) return null;

  return {
    credentialId: results[0].credential_id,
    privateKeyJwk: JSON.parse(results[0].private_key_jwk),
    email: results[0].email,
    userId: results[0].user_id,
    signCount: results[0].sign_count,
  };
}

/**
 * Get credential by index (for VU-based allocation)
 * Uses OFFSET to get specific credential by index
 */
export function getCredentialByIndex(index) {
  const database = initDb();

  const results = database.query(`
    SELECT credential_id, private_key_jwk, email, user_id, sign_count
    FROM credentials
    ORDER BY id
    LIMIT 1 OFFSET ${index}
  `);

  if (results.length === 0) return null;

  return {
    credentialId: results[0].credential_id,
    privateKeyJwk: JSON.parse(results[0].private_key_jwk),
    email: results[0].email,
    userId: results[0].user_id,
    signCount: results[0].sign_count,
  };
}

/**
 * Update sign count after authentication
 */
export function updateSignCount(credentialId, newSignCount) {
  const database = initDb();

  database.exec(`
    UPDATE credentials
    SET sign_count = ${newSignCount}, last_used_at = CURRENT_TIMESTAMP
    WHERE credential_id = '${credentialId}'
  `);
}

/**
 * Increment sign count and return new value
 */
export function incrementSignCount(credentialId) {
  const database = initDb();

  database.exec(`
    UPDATE credentials
    SET sign_count = sign_count + 1, last_used_at = CURRENT_TIMESTAMP
    WHERE credential_id = '${credentialId}'
  `);

  const results = database.query(`
    SELECT sign_count FROM credentials WHERE credential_id = '${credentialId}'
  `);

  return results.length > 0 ? results[0].sign_count : 0;
}

/**
 * Get total credential count
 */
export function getCredentialCount() {
  const database = initDb();

  const results = database.query(`SELECT COUNT(*) as count FROM credentials`);
  return results[0].count;
}

/**
 * Clear all credentials (for test reset)
 */
export function clearAllCredentials() {
  const database = initDb();
  database.exec(`DELETE FROM credentials`);
}

/**
 * Close database connection
 */
export function closeDb() {
  if (db) {
    db.close();
    db = null;
  }
}
