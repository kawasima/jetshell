# JetShell Project Guidelines

## Branch Strategy

- `main` — production releases only
- `develop` — integration branch; all feature PRs target here
- `feature/*` — short-lived feature branches cut from `develop`

**Never commit directly to `main` or `develop`.** Always use a feature branch and open a PR targeting `develop`.

## Release Process

Releases follow the pattern: remove SNAPSHOT → merge develop into main → tag → build → publish.

### 1. Remove SNAPSHOT from version

On the `develop` branch, edit `pom.xml`:

```
<version>X.Y.Z-SNAPSHOT</version>  →  <version>X.Y.Z</version>
```

Commit:

```bash
git add pom.xml
git commit -m "chore: bump version to X.Y.Z for release"
git push origin develop
```

### 2. Merge develop into main

```bash
git checkout main
git merge --no-ff develop -m "chore: release X.Y.Z"
git push origin main
```

If merge conflicts occur in files that exist only in `develop` (add/add conflicts), accept `develop`'s version:

```bash
git checkout --theirs <conflicting-files>
git add <conflicting-files>
git commit -m "chore: release X.Y.Z"
```

### 3. Create and push the release tag

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

### 4. Build the artifact

```bash
mvn package -DskipTests
```

The artifact is produced at `target/jetshell`.

### 5. Create the GitHub release and attach the artifact

```bash
gh release create vX.Y.Z target/jetshell \
  --title "vX.Y.Z" \
  --notes "Release notes here" \
  --target main
```

### 6. (Optional) Bump develop to next SNAPSHOT

After the release, update `develop` to the next development version:

```bash
git checkout develop
# edit pom.xml: X.Y.Z → X.Y.(Z+1)-SNAPSHOT
git add pom.xml
git commit -m "chore: bump version to X.Y.(Z+1)-SNAPSHOT"
git push origin develop
```
