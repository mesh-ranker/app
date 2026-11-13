# MESH Ranker for Linux

Native Linux desktop build based on Compose for Desktop. It reuses the existing MЭШ API client and provides session login plus рейтинг, оценки, домашние задания, расписание, предметы and profile data.

## Build

Requires JDK 17 and Linux packaging tools (`fakeroot`, `dpkg`):

```bash
./gradlew :linuxApp:packageReleaseDeb
```

The `.deb` and a self-contained app directory are written under `linuxApp/build/compose/binaries/main-release/`. CI publishes the `.deb` and a portable `.tar.gz` archive as a GitHub prerelease from branch `linux-app`.

Sign in by pasting a МЭШ token from the browser session. The app stores the session under `~/.config/mesh-ranker/session.json` with owner-only file permissions. The token is not encrypted at rest.
