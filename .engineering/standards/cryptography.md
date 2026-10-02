# Global Standard: Cryptography

## Algorithms & Standards
1. **Symmetric Encryption**: Use AES-256-GCM for encrypting data at rest or application-level payloads. Never use DES, 3DES, or AES-ECB.
2. **Asymmetric Encryption**: Use RSA with at least 2048-bit keys, or ECC (Elliptic Curve Cryptography) with at least 256-bit curves (e.g., secp256r1, ed25519) for digital signatures and key exchange.
3. **Hashing**: Use SHA-256 or higher for general hashing. Never use MD5 or SHA-1.
4. **Password Storage**: Passwords must be hashed using a slow, work-factor-based algorithm: Argon2id (preferred), bcrypt, or scrypt. Never store passwords in plain text or use fast hashes (like SHA-256) for passwords.
5. **Salting**: Always use a unique, cryptographically random salt for each password hash.

## Key Management
6. **Never hardcode keys**: Cryptographic keys must never be hardcoded in source code or committed to version control. Load them from secure environment variables or a Key Management Service (KMS).
7. **Key Rotation**: Implement processes to rotate encryption and signing keys regularly (e.g., every 90 days) without downtime. Applications must support verifying data with previous keys during the transition window.
8. **Separation of Keys**: Use different keys for different purposes (e.g., one key for JWT signing, another for database column encryption).
9. **Envelope Encryption**: For encrypting large amounts of data at rest, use envelope encryption: encrypt the data with a Data Encryption Key (DEK), and encrypt the DEK with a master Key Encryption Key (KEK) stored in a KMS.

## Implementation Rules
10. **Do not roll your own crypto**: Never invent your own cryptographic algorithms or protocols. Use established, audited, and widely-used cryptographic libraries provided by the language or framework.
11. **Random Number Generation**: Always use Cryptographically Secure Pseudo-Random Number Generators (CSPRNG) (e.g., `/dev/urandom`, `crypto.randomBytes()`) for generating keys, IVs, nonces, and tokens.
12. **Initialization Vectors (IV) / Nonces**: IVs and nonces must be unique and randomly generated for every encryption operation. Never reuse an IV with the same key.
13. **Constant-Time Comparison**: When comparing cryptographic hashes, HMACs, or tokens, always use constant-time comparison functions to prevent timing attacks.
