# Test APK signing

The v2 certificate is pinned in `test-signing.sha256`. Java/JNI namespace and
application ID are separate from this signing identity. The v1 private key was
not retained; the v2 key cannot sign an in-place v1 update.

## Encrypted backup

Set repository Actions secret `TRIG_SIGNING_BACKUP_PASSWORD` through GitHub
Settings → Secrets and variables → Actions. Use a unique single-line password
of at least 20 characters and keep a private recovery copy. Never put its value
or a private keystore in Git, logs, chat, release assets, or workflow inputs.

The Back up retained test signing key workflow authenticates and encrypts the
current pinned key, stores only `Winlator-trig-signing-v2.enc` in draft release
`trig-signing-backup`, and verifies decryption and the certificate on a fresh
runner. Keep this release as a draft so app updaters do not see it as an APK
release. Only the encrypted envelope may be uploaded.

The backup helper uses OpenSSL AES-256-CBC with PBKDF2-HMAC-SHA256 and an
independent domain-separated HMAC key. It authenticates all metadata and
ciphertext before decrypting; passwords use a private pipe, never arguments.
Run `python3 .github/scripts/signing_backup.py self-test` for dummy-data tests.
Decrypted keys are created with mode 0600 and must stay on the temporary runner.

Do not change or delete the password secret without first arranging a verified
backup encrypted with the replacement password. GitHub cannot show a saved
secret's value. Keep the encrypted backup and your recovery password independently
of the expiring Actions cache. The initial plaintext cache can be removed after
the fresh-runner encrypted-backup check and the APK workflow's encrypted restore
have both succeeded.

## Future app releases

Keep the same package ID and certificate. Increase versionCode for every update
(v2 is 34). The build must stop on missing credentials, failed authentication or
a certificate mismatch; it must never silently generate a replacement key.
