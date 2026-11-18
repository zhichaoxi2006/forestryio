# Offline dependency jars

This build runs with Gradle's offline flag by default (see `../settings.gradle`) because
`maven.neoforged.net` is not reachable from this machine. These three jars are therefore committed
to the repository and put on the classpath directly:

| File | Mod | Version | Source | License |
| --- | --- | --- | --- | --- |
| `laserio-1.9.11.jar` | LaserIO | 1.9.11 (MC 1.21.1, NeoForge) | CurseForge / ATM10 instance `mods/` | MIT |
| `forestry-1.21.1-3.0.0-alpha9.jar` | Forestry: CE | 3.0.0-alpha9 (MC 1.21.1, NeoForge) | Modrinth (`2oORTOi2`) / `Forestry-Maid/libs` | LGPL-3.0-only |
| `Patchouli-1.21.1-93-NEOFORGE.jar` | Patchouli | 1.21.1-93-NEOFORGE | `Forestry-Maid/libs` | CC BY-NC-SA 3.0 |

`build.gradle` uses:

```groovy
compileOnly files("libs/<jar>") // compile against the API
runtimeOnly  files("libs/<jar>") // load it in runClient / runServer
```

so the jars are never shaded into the Forestry IO jar. They are development-only files: do not
redistribute them, and keep their licenses in mind if you publish a modpack or a built jar.

## Updating a dependency

1. Drop the new jar into this folder.
2. Update `laserio_version` / `forestry_version` / `patchouli_version` in `../gradle.properties`.
3. Update the matching `*_version_range` if the new version requires it.
4. Run `gradlew build --offline`.

The `verifyOfflineDependencies` task fails with a readable message if a jar is missing.
