# Podcast Hacker — TODO

A Kotlin Multiplatform podcast app (Android + Desktop first-class, iOS kept compiling
best-effort) built around [tacita](https://github.com/episode6/tacita) for ad-cutting
downloads. Playback is **download-first** (no streaming) — tacita needs the complete
file to diff out injected ads. Design language: Pocket Casts-ish.

**Key decisions:**
- Business logic driven by [redux-store-flow](https://github.com/episode6/redux-store-flow)
  (app-scoped `AppStore` + screen-scoped `StoreFlow`s, side effects for all IO),
  following podcast-puller-2's idioms
- Metro DI · Room KMP · JetBrains navigation-compose · RSSParser (prof18) · Coil 3
- Playback: Media3/MediaSessionService (Android), AVPlayer (iOS), JavaFX Media (Desktop)
- Adding podcasts: iTunes Search API + paste-RSS-URL fallback
- Package: `com.episode6.podcasthacker` · Version source of truth: `self.versions.toml` (v0.0.1)
- Tests: mockk (jvm/android only) + assertk + redux test-support + ktor-client-mock

## ~~Stage 1 — CI + version centralization~~ (done 2026-07-04, PR #1)

- [x] `self.versions.toml` (`name = "0.0.1"`, `code = "1"`) registered as `self` catalog
      in settings.gradle.kts
- [x] androidApp reads versionCode/versionName from `self`
- [x] desktopApp reads packageVersion from `self`; packageName → `PodcastHacker`;
      dmg version mapped `0.x.y → 1.x.y` (jpackage rejects MAJOR==0 for dmg; check msi too)
- [x] iosApp Config.xcconfig MARKETING_VERSION=0.0.1 + `scripts/sync-ios-version.sh`
- [x] Shared Xcode scheme committed (`iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`)
      so CI can run xcodebuild
- [x] `.github/workflows/build-installers.yml`: dmg/msi/deb matrix + debug APK shard +
      dedicated iOS shard (framework link, simulator tests, full `xcodebuild` app build),
      each shard running `check` as a prereq for its own platform; artifacts attached to
      GitHub release on `v*` tags (iOS shard is best-effort and doesn't gate releases)
- [x] Verify: draft PR with all jobs green; installer artifacts carry 0.0.1

## Stage 2 — Core scaffolding (deps, DI, redux, nav, theme)

- [x] Add Stage-2 catalog entries (see dependency table); verify ⚠ Metro↔Kotlin 2.4.0
      compat FIRST (fallback: kotlin-inject) — Metro 1.3.0 tests against Kotlin 2.4.x; no
      fallback needed
- [x] Metro: plugin on `:shared`, `AppGraph` + platform bindings (Android Application
      context, desktop main, iOS MainViewController), graph exposed via CompositionLocal.
      Gotcha: `actual typealias PlatformContext = Context` fails (expect class is final,
      Context is abstract), so Android wraps the Context in an `actual class` instead
- [x] Redux skeleton: `AppState` + actions + `AppState::reduce`; `AppStore` singleton via
      `StoreFlow(scope, initialValue, reducer, listOf(SideEffectMiddleware(sideEffects)))`
      with multibound `Set<SideEffect<AppState>>`; port `sideEffect {}` / `mapActions` /
      `stateOf { slice }` helpers from podcast-puller-2
- [x] Navigation skeleton: JetBrains navigation-compose, `@Serializable` routes
      (Grid, AddPodcast, PodcastDetail, EpisodeDetail, NowPlaying) + placeholder screens
- [x] Root layout: `Box { NavHost; MiniPlayerBar(align BottomCenter) }` — bar stub hidden
      on NowPlaying / when idle
- [x] Pocket-Casts-like dark-forward Material3 theme + typography
- [x] `AppDirs` expect/actual (okio Paths): Android filesDir/cacheDir, desktop
      XDG/AppData/Application Support, iOS Documents
- [x] Swap template tests to assertk; add redux test-support
- [ ] Verify: android + desktop run and navigate all placeholders; CI green (incl. iOS)
      — desktop verified locally (launch + jvm/android-host tests + `check` green);
      android emulator pass + CI still pending

## Stage 3 — Data layer (Room, network, RSS, repositories)

- [x] Room KMP: `PodcastEntity`, `EpisodeEntity` (guid, feedUrl, title, notes, audioUrl,
      pubDate, duration, downloadState, playbackPosition), DAOs, per-platform builders
      (bundled SQLite driver on all platforms; Room 2.8.4 + KSP 2.3.9 work fine against
      the AGP 9 KMP-library plugin). Gotcha: `Dispatchers.IO` isn't referencable from
      commonMain (internal on native) — small `ioDispatcher` expect/actual instead
- [x] HttpClient via Metro (okhttp android/jvm, darwin ios)
- [x] iTunes Search API client (`https://itunes.apple.com/search?media=podcast&term=`)
      with kotlinx-serialization models (decoded from text: apple serves
      `text/javascript`, so no ContentNegotiation needed)
- [x] RSS: RSSParser wrapper → domain models (podcast meta + episodes, iTunes namespace).
      Feed XML is fetched with our own ktor client and handed to `RssParser.parse()`, so
      fetching is mockable; re-syncs preserve per-episode downloadState/playbackPosition
- [x] Repositories (plain injectable classes): Subscription / Feed / Episode
- [x] Side effects for subscription + feed sync (action in → repo call → result actions);
      Room is source of truth feeding AppState via observe side effect. Gotcha (found
      2026-07-04, fixed in stage 4): SideEffectMiddleware relays no actions until EVERY
      side effect subscribes to `context.actions` — an observe-only effect must merge in
      `actions.filter { false }` or it starves all side effects of input
- [x] Tests: fixture-feed parsing, side effects via redux test-support + mockk + assertk +
      ktor-client-mock, in-memory Room. Gotcha: RssParser's android impl needs kxml2 on
      the androidHostTest classpath (the host-test android.jar XmlPull classes are stubs)
- [ ] Verify: `:shared:jvmTest` + `:shared:testAndroidHostTest` green; CI green
      — local tests + `check` + ios klib compile + desktop launch (db created on disk)
      all green; CI pending

## Stage 4 — Subscribe flow + podcast grid

- [x] AddPodcast screen: screen-scoped StoreFlow (hosted in a ViewModel for config-change
      survival); search field → iTunes results (artwork/title/author) + paste-URL row.
      Search is debounced (400ms, min 2 chars) with `transformLatest` cancelling stale
      requests; url-shaped queries show a direct subscribe row instead
- [x] Subscribe action → app-store side effect fetches feed + persists (the Stage-3
      `SubscribeToPodcast` side effect; result rows dispatch it and pop back)
- [x] Grid screen: `LazyVerticalGrid(GridCells.Adaptive(160.dp))` of subscription artwork
      (Coil 3.5.0, `KtorNetworkFetcherFactory` sharing the graph's HttpClient via
      `setSingletonImageLoaderFactory`), add tile, unsubscribe via long-press dropdown,
      thin sync progress bar; leftover template `Platform`/`getPlatform` deleted
- [x] Verify: subscribe to 2–3 real podcasts on desktop + android emulator; artwork renders;
      survives restart; unsubscribe works (verified 2026-07-04 on both platforms after
      fixing the SideEffectMiddleware subscription-contract bug that silently swallowed
      all side-effect input)

## Stage 5 — Podcast detail + episode detail

- [ ] PodcastDetail: header (artwork, title, description) + episode list from DB;
      refresh-on-open + manual refresh via dispatched actions
- [ ] EpisodeDetail: artwork, title, date/duration, scrollable show notes
      (basic HTML → AnnotatedString), Play/Download button (stub until Stages 6–7)
- [ ] Verify: browse real feeds end-to-end on android + desktop

## Stage 6 — Downloads via tacita

- [ ] `DownloadSideEffects` (mirrors podcast-puller-2's `DownloadFilesSideEffects`):
      download actions → collect `tacita.downloadPodcast(...)` → progress actions in
      AppState + status persisted to Room; sequential queue for v0
- [ ] Reference `.adref` files in cache dir, cleaned after `Complete`
- [ ] Episode UI: download button, progress, "cutting ads" state, delete download
- [ ] iOS: add ktor-client-darwin so tacita has an engine
- [ ] File naming by guid hash; handle overwrite/redownload
- [ ] Tests: download side-effect state machine (redux test-support + mockk'd Tacita)
- [ ] Verify: download a real episode on desktop + android; file plays in external player;
      ad-cut pass completes

## Stage 7 — Playback + now-playing

- [ ] `PodcastPlayer` expect/actual: load/play/pause/seekTo/setSpeed +
      `StateFlow<PlayerState>`
- [ ] Android: Media3 ExoPlayer + `MediaSessionService` (notification, lock screen,
      audio focus, headset)
- [ ] Desktop: JavaFX Media, headless `Platform.startup {}` (⚠ verify jpackage bundling
      of JavaFX natives in dmg/msi/deb; fallback: vlcj)
- [ ] iOS: AVPlayer + AVAudioSession(.playback) + MPNowPlayingInfoCenter /
      MPRemoteCommandCenter (compile-verified only)
- [ ] `PlaybackSideEffects`: player commands out of actions, `PlayerState` into AppState
- [ ] NowPlaying screen: artwork, title, seekbar, ±15/30s skips, play/pause, speed
- [ ] MiniPlayerBar wired for real (hidden on NowPlaying; tap → NowPlaying)
- [ ] Persist playback position to Room (periodic + on pause)
- [ ] Verify: full listen flow on android (incl. background + lock screen) and desktop
      (seek + speed); position resumes after restart

## Stage 8 — Platform polish

- [ ] Android background-download resilience: JobScheduler **user-initiated data transfer**
      jobs on API 34+ (`JobInfo.setUserInitiated(true)`, `RUN_USER_INITIATED_JOBS`,
      `setEstimatedNetworkBytes`, notification via `JobService.setNotification`);
      `dataSync` foreground service fallback on API 24–33 (UIDT doesn't exist there;
      WorkManager has no UIDT support) — both behind a small `DownloadScheduler` interface
- [ ] Foldable pass: material3-adaptive / WindowSizeClass paddings; optional list-detail
      pane on expanded widths; resizable-emulator test
- [ ] iOS: Info.plist background-audio mode
- [ ] Desktop: window size/position persistence; media keys if cheap
- [ ] App icons for all platforms
- [ ] Verify: CI green; manual foldable emulator pass

## Stage 9 — Release v0.0.1

- [ ] README rewrite (what/why/how, tacita credit); RELEASE_CHECKLIST.md
      (incl. bump `name` + `code` in self.versions.toml together)
- [ ] Confirm self.versions.toml 0.0.1 / code 1; tag `v0.0.1`
- [ ] Verify: GitHub release auto-created with deb/msi/dmg + APK attached;
      install deb locally; sideload APK

## Dependency table (add per stage; ⚠ = verify version/compat at implementation time)

| Catalog entry | Coordinates | Version |
|---|---|---|
| tacita | `com.episode6.tacita:tacita` | 0.0.1 |
| redux-* | `com.episode6.redux:{store-flow,side-effects,compose,test-support}` | 1.1.6 (confirmed: resolves + tests pass under Kotlin 2.4.0) |
| ktor-* | `io.ktor:ktor-client-{core,okhttp,darwin,content-negotiation,mock}`, `ktor-serialization-kotlinx-json` | 3.5.1 (match tacita) |
| okio | `com.squareup.okio:okio` | 3.17.0 (match tacita) |
| kotlinx-serialization | plugin `org.jetbrains.kotlin.plugin.serialization` + `kotlinx-serialization-json` | 2.4.0 / 1.11.0 |
| kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | 0.8.0 |
| metro | plugin + runtime `dev.zacsweers.metro` | 1.3.0 (tests against Kotlin 2.4.x) |
| ksp | plugin `com.google.devtools.ksp` | 2.3.9 (standalone versioning, no longer Kotlin-prefixed) |
| room | `androidx.room:room-{runtime,compiler}` + plugin `androidx.room` | 2.8.4 |
| sqlite-bundled | `androidx.sqlite:sqlite-bundled` | 2.6.2 |
| navigation | `org.jetbrains.androidx.navigation:navigation-compose` | 2.9.2 (works w/ CMP 1.11.1) |
| coil | `io.coil-kt.coil3:{coil-compose,coil-network-ktor3}` | 3.5.0 |
| rssparser | `com.prof18.rssparser:rssparser` | 6.1.6 (latest; jvm target confirmed) |
| media3 | `androidx.media3:media3-{exoplayer,session}` | ⚠ 1.9.x (androidMain only) |
| adaptive | `org.jetbrains.compose.material3.adaptive:adaptive` | ⚠ 1.2.x |
| javafx-media | `org.openjfx:javafx-{base,graphics,media}` | ⚠ 25.x LTS (desktopApp only) |
| mockk | `io.mockk:mockk` | 1.14.11 (jvm/android test source sets only) |
| assertk | `com.willowtreeapps.assertk:assertk` | 0.28.1 (commonTest) |
| coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 |
| turbine (optional) | `app.cash.turbine:turbine` | ⚠ 1.2.x |

## Risks

1. ~~**Metro + Kotlin 2.4.0** (highest)~~ RESOLVED 2026-07-04: Metro 1.3.0 tests against
   Kotlin 2.4.x; compiles, runs, and works with the configuration cache in Stage 2.
2. ~~**Room/KSP on `com.android.kotlin.multiplatform.library` (AGP 9)**~~ RESOLVED
   2026-07-04: Room 2.8.4 + KSP 2.3.9 wire up cleanly (`kspAndroid` etc.) in Stage 3.
3. **material3 1.11.0-alpha07**: alpha API churn — pin, don't chase upgrades mid-project.
4. **jpackage version constraints**: dmg rejects MAJOR==0 (mapped 0.x.y→1.x.y); verify msi.
5. **JavaFX Media inside jpackage installers**: needs a real pass on all 3 OSes in Stage 7;
   vlcj is the documented fallback.
6. **Configuration cache**: Metro/KSP/Room may lag on config-cache support; be ready to
   disable it (gradle.properties or `--no-configuration-cache` in CI).
7. **rssparser JVM target** and **iosSimulatorArm64Test flakiness on GH runners**: both
   have fallbacks noted in their stages.
8. **tacita on Android**: Android consumes tacita's jvm artifact (bundled okhttp — safe);
   confirm resolution from `:shared` androidMain in Stage 6.
