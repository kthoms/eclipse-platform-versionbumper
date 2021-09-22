# eclipse-platform-versionbumper

A tool to provide version bump changes to Eclipse Gerrit for Eclipse Platform changes.

## Context

The Eclipse Platform Project follows a strict semantic versioning concept for all bundles contributed to its core projects.

With each simultaneous release the Eclipse Platform sets a new baseline based on its last release. 
Whenever a change is build it is first checked if affected bundles have the same version as the baseline bundle.
When this is the case, the build fails and requires bumping the affected bundle.

Sometimes changes contain the required version updates. 
However, it is preferred to deliver the version bump via a separate change and rebase the change upon the version bump change.

This is a quite tedious work for platform developers as it requires for each change to

- check if it is required to bump a bundle version for a change
- create a Gerrit change for the bundle version bump
- rebase the change upon the version bump change

