# Description

This document describes how to update Status APK builds for the [F-Droid](https://f-droid.org/) Android application catalogue.

# Intro

In simplest terms F-Droid requires a YAML file that defines the steps necessary to create a universal unsigned APK build. This is achieved by submitting a new app versions into the `metadata/im.status.ethereum.yml` file in the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository.

The app builds defined this way run on servers that generate the unsigned APKs using the [fdroidserver](https://gitlab.com/fdroid/fdroidserver) software. The [server setup](https://f-droid.org/en/docs/Build_Server_Setup/) is quite involved but is not necessary unless you want to run your own instance. Normally the applications defines in `fdroiddata` are built by servers maintained by [the F-Droid volunteers](https://f-droid.org/en/contribute/).

First release of Status app was merged in [fdroid/fdroiddata#7179](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/7179).

# Adding New Versions

You can find our configuration file at [`metadata/im.status.ethereum.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/im.status.ethereum.yml)

The file defines all the necessary metadata like `SourceCode`, `Website`, or `License`, but the most important key is `Builds`, which looks like this:
```yml
Builds:
  - versionName: 1.4.1
    versionCode: 2020070112
    commit: cfb825a11b61d312af8cb5d36686af540c31f481
    sudo:
      - mkdir -m 0755 /nix
      - chown vagrant /nix
    init: ./nix/scripts/setup.sh
    output: ./result/app-release-unsigned.apk
    scanignore:
      - android/build.gradle
    scandelete:
      - ios
    build: make release-android BUILD_TYPE=release BUILD_NUMBER=2020070112 ANDROID_APK_SIGNED=false
```
It contains a list of objects defining each release of the application. In order to add a new release simply copy a previous release object and adjust the following values:

* `versionName` - String version like `1.4.1`
* `versionCode` - Android `versionCode`. We use a timestamp generated by [a script](../scripts/version/gen_build_no.sh)
* `commit` - Specific commit SHA1 from which the given release was built
* `BUILD_NUMBER` in `build` step - Same value as `versionCode`

The `versionCode` should be the same as the one in build that was uplodade to Play Store. It can be found in the build logs or by using:
```
 > apkanalyzer manifest version-code StatusIm-v1.4.1.apk
2020070112
```

At the bottom of the file you should also update the following keys:

* `CurrentVersion` - Same as the new `versionName` added
* `CurrentVersionCode` - Same as the `versionCode` added

Then submit a merge request to the [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository.

# Checking Builds

The simplest way to test if the app builds via F-Droid is to use the Docker image:
https://gitlab.com/fdroid/docker-executable-fdroidserver

Which is available under `registry.gitlab.com/fdroid/docker-executable-fdroidserver:latest`.

Because we use Nix to build the mobile app we need to slightly modify the image with this `Dockerfile`:
```Dockerfile
FROM registry.gitlab.com/fdroid/docker-executable-fdroidserver:latest
ARG BUILDER_UID=1000
ENV BUILDER_USER=vagrant
RUN useradd -u $BUILDER_UID $BUILDER_USER
RUN mkdir -m 0755 /nix /home/$BUILDER_USER \
 && chown -R $BUILDER_USER /nix /home/$BUILDER_USER
```
Build it using:
```
docker build --build-arg=BUILDER_UID=$UID -t statusteam/docker-executable-fdroidserver:latest .
```
Then clone the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) and [fdroidserver](https://gitlab.com/fdroid/fdroidserver) repos and use it to test the build of a specific Status Android app version: `1.4.1`
```
docker run --rm \
  -u $UID:$GID \
  -v $(pwd)/nix:/nix \
  -v $(pwd)/fdroiddata:/repo \
  -v $(pwd)/fdroidserver:/fdroidserver \
  statusteam/docker-executable-fdroidserver:latest \
  build im.status.ethereum:1.4.1
```
We have to create a user and specify the UID because Nix cannot run as `root` and that is the default user for the F-Droid Docker image. By adding our own user and setting the UID we also make it possible to mount folders like `fdroiddata` and `fdroidserver`.

You can specify a `--vebose` flag for `build` command for additional information.

You should also run `lint im.status.ethereum` to verify the YAML format is correct.

# Details

The original research was done in [#8512](https://github.com/status-im/status-react/issues/8512).

Normally F-Droid server wants to run Gradle itself, but we do not specify the `gradle` key in order to run `make release-android` ourselves in `build` step.