# Repository instructions

## Preserve long-lived branches

For bulk branch cleanup, always preserve `main` and `future/windows-version`. Never include either branch in a bulk deletion command or script. Before deleting branches, verify both refs exist. If `future/windows-version` is missing, recreate it from the current `main` before continuing. Delete only the other branches.
