#! /bin/bash -ex

adb reverse --remove-all

re-natal use-android-device avd
re-natal use-figwheel
react-native run-android
