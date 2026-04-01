# OTBridge

`OTBridge` contains both:

- the Android source for `ee.nekoko.nbridge`
- the Magisk/KernelSU module project that installs it as a privileged app

The app exposes OMAPI and TMAPI through:

- `content://ee.nekoko.nbridge.provider`

## Android App

Package:

- `ee.nekoko.nbridge`

Provider methods:

- `listSlots`
- `listActiveConnections`
- `connectLogicalChannel`
- `transmitLogical`
- `transmitBasic`
- `closeLogicalChannel`

Slot display names:

- OMAPI: `O-SIM1`, `O-SIM2`
- TMAPI: `T-SIM1`, `T-SIM2`
- multi-port TMAPI: `T-SIM1p1`, `T-SIM2p1`

## Build The App

Debug APK:

```bash
./gradlew :app:assembleDebug
```

Release APK:

```bash
./gradlew :app:assembleRelease
```

Default release output:

```text
app/build/outputs/apk/release/app-release.apk
```

## Build The Module

Build from the local release APK:

```bash
./scripts/build.sh
```

Build from an explicit APK:

```bash
./scripts/build.sh --apk /absolute/path/to/NBridge.apk
```

Optional version overrides:

```bash
./scripts/build.sh --version v1.0.0 --version-code 1
```

Output:

```text
build/otbridge-magisk-kernelsu.zip
```

The module mounts `ee.nekoko.nbridge` into `/system/priv-app/ee.nekoko.nbridge/NBridge.apk` and installs the privileged permission XML.

## Source Layout

- `app/`: Android bridge app source
- `module_template/`: Magisk/KernelSU module template
- `scripts/build.sh`: module packaging script

## License

MIT
