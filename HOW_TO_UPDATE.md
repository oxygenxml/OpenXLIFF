# How to Update oxygen-patched-openxliff from Upstream

**Upstream repository:** [https://github.com/rmraya/OpenXLIFF](https://github.com/rmraya/OpenXLIFF)

## Why We Maintain This Patch

This patched fork exists for three main reasons:

1. **Java version compatibility** — The upstream project requires JDK 21 or newer for building; we keep our repositories on **Java 17**.
2. **Build and compliance** — Upstream uses **Gradle**; it does not use Maven. This patch adds the required **Maven** configuration for our security and compliance pipeline, with the add-on superpom as parent and our versioning scheme.
3. **Stability and improvements** — We apply fixes and improvements to the codebase where needed.

This project depends on **oxygen-patched-xmljava** and **oxygen-patched-bcp47j**. Ensure those dependencies are updated or compatible when updating OpenXLIFF.

---

## Update Procedure

### Step 1: Pull changes from upstream

Pull the latest changes from the upstream fork:

- **Use a merge-based pull**, not rebase. Rebase tends to complicate the history and conflict resolution; merge is simpler for this workflow.

### Step 2: Resolve merge conflicts

After pulling, resolve any merge conflicts as follows:

| Conflict location | Action |
|-------------------|--------|
| **`module-info.java`** | Remove this file. We do not use it in our build. |
| **Other Java files** | Resolve manually; keep or adapt code as appropriate for our patch. |
| **All other files** | Prefer the **upstream version** (theirs). Review briefly to ensure nothing critical for our setup is broken. |
| **`Constants.java`** | Always keep the **upstream version** of this file. |

### Step 3: Update `pom.xml`

#### 3.1 Patched artifact properties

Set the following in `<properties>` (get version and build ID from `Constants.java` in `src/com/maxprograms/converters/`):

```xml
<properties>
  <!-- other properties -->
  <patched.groupId>com.maxprograms</patched.groupId>
  <patched.artifactId>openxliff</patched.artifactId>
  <patched.version>{version}</patched.version>
  <patched.buildId>{buildId}</patched.buildId>
</properties>
```

- **`patched.version`** — Use the version from `Constants.java` (or bump the minor version if this is an intermediate release).
- **`patched.buildId`** — Use the build ID from `Constants.java`.

#### 3.2 Parent and project coordinates

Update the parent and project version to match the current Oxygen and patch lifecycle:

```xml
<parent>
  <groupId>com.oxygenxml</groupId>
  <artifactId>oxygen-patched-superpom</artifactId>
  <version>{superpom-version}</version>
</parent>
<artifactId>oxygen-patched-openxliff</artifactId>
<version>{patch-version}</version>
```

- **Parent `version`** — Use the current Oxygen superpom version (e.g. if Oxygen 28.0 is released, use that superpom version).
- **Project `version`** — Use the upstream version or an incremented minor version for an intermediate release.

#### 3.3 Dependencies on other patched projects

If upstream bumps its dependencies, align the Maven dependency versions in `<dependencies>` with the versions you use in the workspace:

- **oxygen-patched-xmljava**
- **oxygen-patched-bcp47j**

---

## Related patched projects (Fluenta ecosystem)

The following projects in our workspace also maintain patches from upstream and may need similar update procedures:

- **XMLJava**
- **BCP47J**
- **Swordfish**
- **Fluenta**

Apply the same merge and conflict-resolution approach when updating those repositories.
