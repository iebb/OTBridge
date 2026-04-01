# OTBridge

Systemless Magisk/KernelSU module project for installing `ee.nekoko.nbridge` as a privileged app.

## What it does

- mounts `ee.nekoko.nbridge` into `/system/priv-app/ee.nekoko.nbridge/NBridge.apk`
- installs the required privileged permission XML
- works as a source project for generating a flashable Magisk/KernelSU zip

This repository does not ship a prebuilt APK in source control. You provide the `NBridge.apk` when building the module zip.

## Build

Requirements:

- `bash`
- `zip`
- a built `ee.nekoko.nbridge` APK

Build a module zip from an APK:

```bash
./scripts/build.sh --apk /absolute/path/to/NBridge.apk
```

Optional version overrides:

```bash
./scripts/build.sh \
  --apk /absolute/path/to/NBridge.apk \
  --version v1.0.0 \
  --version-code 1
```

The output zip is written to:

```text
build/otbridge-magisk-kernelsu.zip
```

## Install

1. Build the zip.
2. Install it in Magisk or KernelSU.
3. Reboot.

After reboot, `ee.nekoko.nbridge` should be mounted as a priv-app and receive the privileges declared in:

- `module_template/system/etc/permissions/privapp-permissions-ee.nekoko.nbridge.xml`

## Source Layout

- `module_template/`: module files copied into the final zip
- `scripts/build.sh`: module packaging script

## License

MIT
