# Platform CI and required checks

`.github/workflows/ci.yml` is the only automatic platform CI entrypoint. It runs
on every PR to main, push to main, merge-group request, and manual dispatch.
Its weekly schedule runs CodeQL and dependency audits, without emulator/build
test matrices. Individual platform workflows remain manually callable.

`scripts/ci_changes.py` reads the full git diff. PRs use merge-base versus head;
pushes use before versus after; merge groups use base versus head. Renames are
classified as removal plus addition. Unclassified paths and shared CI files
select both platforms. Invalid/missing git history fails detection.

- Android: app, Gradle, Android workflows and their scripts.
- Windows: windows source/config, Windows workflows and NuGet audit script.
- Both: .gitignore, .editorconfig, dispatcher, gate/detector/tests, unknown paths.
- Neither: markdown, docs/site, site-specific workflows/scripts and license text.

Before PR #69 introduces the Windows solution, tracked .gitkeep files are an
explicit scaffold-only state. Windows jobs are skipped with a detector message.
If actual Windows source exists but the solution/SDK/projects are incomplete,
detection fails instead of silently skipping Windows.

## Configure main protection

After the workflow has reported a successful **CI Gate**, configure the main
branch protection/ruleset to require that job from GitHub Actions. Remove old
unconditional requirements for platform-specific jobs: those jobs can now be
correctly skipped on unrelated changes. Retain unrelated required reviews or
checks. Do not require a workflow that is absent because of paths filtering.

This setting is repository administration state; committing this file does not
enable it. The integration used for the CI migration could read an empty ruleset
list but could not read/write classic main protection. The owner must complete
this setting in GitHub Settings → Branches or Rules → Rulesets.

`CI Gate` uses `always()` and explicitly requires success from selected checks,
including CodeQL/dependency audits; skipped is permitted only for unselected
checks. Failure, cancellation, missing flags or an unexpected skip fails the gate.

## Windows dependencies

Windows Debug/Release builds preserve the commands validated in PR #69.
Windows CodeQL uses C# manual build on windows-2025, with signing and installer
creation disabled. This does not install an MSIX or change Bluetooth hardware.

The Windows audit restores the full solution with NuGetAuditMode=all, treating
NU1900–NU1905 as errors. `windows/NuGet.Config` explicitly declares an audit data
source so missing vulnerability metadata cannot silently pass. A second JSON
report includes direct and transitive vulnerabilities at every severity; the
Python checker validates the report and inventories nuspec license metadata.
License-file evidence is copied into the retained artifact. Missing metadata
fails CI; custom/file licenses and legacy URLs are release-review items, not an
automatic declaration of license compatibility. Artifacts are kept for 14 days.
Dependabot monitors /windows NuGet packages monthly.

## Acceptance checks

Run `python3 -m unittest discover -s scripts -p 'ci_tests.py'` and actionlint.
Confirm actual Actions routing for a Windows-only PR, an Android-only PR and a
docs-only PR. Shared .gitignore changes intentionally run both platforms.
Check that a failed/cancelled selected job fails CI Gate. Windows runtime,
MSIX signing/install/update and real device mutation remain separate validation.

PR #69 adds an earlier version of windows.yml. When incorporating this main
commit into that branch, retain the reusable windows.yml from main so there is
one dispatcher and no duplicate Windows runs.
