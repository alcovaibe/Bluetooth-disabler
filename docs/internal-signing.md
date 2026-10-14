# Installing internal updates

Internal APKs built from `main` use the existing persistent certificate from the
repository's `RELEASE_KEYSTORE_*` / `RELEASE_KEY_*` secrets. The application ID
remains `com.pulse.bluetoothdisable.internal`; release versioning and signing
configuration are unchanged. The secret is restored only for a trusted main
push or manual main build, never passed to PR jobs, uploaded or committed.
The restored file is removed after the build. Missing/incomplete signing
configuration fails the installable internal build instead of silently signing
it with a newly generated debug key.

`versionCode` is `1000000 + git rev-list --count HEAD` with full checkout history.
It increases as main gains commits and stays stable when the same commit is
rebuilt. Replacing an APK with the same version code is supported; lower codes
cannot upgrade newer installations. Force-resetting main to shorter history
requires deliberate version migration. The version name is `1.1.2-internal`.

CI verifies APK signatures with Android's `ApkVerifier` from the SDK's `apksigner.jar`, compares the certificate
to the configured key and verifies the package/version before artifact upload.

Old CI APKs have temporary debug certificates. Without their old private key,
they cannot be upgraded in place to the new persistent certificate. The initial
migration requires removing the old internal application, which removes its
local data. After that, subsequent signed main APKs can be installed over it.
An installation with Device Owner needs the corresponding owner-removal or
reprovisioning procedure; this change does not reset or bypass protection.

PR/local debug APKs still use debug signing and are not update-compatible with
signed main internal APKs. For compatible local builds, set `internalStoreFile`,
`internalStorePassword`, `internalKeyAlias`, `internalKeyPassword` in the ignored
`keystore.properties`, or use the matching `INTERNAL_*` environment variables.
Set `INTERNAL_SIGNING_REQUIRED=true` to prohibit a debug-signing fallback.
Keep the persistent certificate unchanged for future internal updates.
