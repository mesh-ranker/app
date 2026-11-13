# MESH Ranker for Linux

Native Linux desktop build based on Compose for Desktop. It reuses the existing MЭШ API client and provides session login plus рейтинг, оценки, домашние задания, расписание, предметы and profile data.

## Build

Requires JDK 17 and Linux packaging tools (`fakeroot`, `dpkg`, `libfuse2`):

```bash
./gradlew :linuxApp:packageReleaseDeb :linuxApp:packageReleaseAppImage
```

Packages are written under `linuxApp/build/compose/binaries/main-release/`. CI publishes both formats as a GitHub prerelease from branch `linux-app`.

For now, sign in by pasting a MЭШ token from the browser session. The app stores its session under `~/.config/mesh-ranker/session.json` with owner-only file permissions.
