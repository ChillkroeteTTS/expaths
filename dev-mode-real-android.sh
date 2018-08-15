#! /bin/bash -ex

adb reverse tcp:8081 tcp:8081
adb reverse tcp:3449 tcp:3449

re-natal use-android-device real
re-natal use-figwheel
react-native run-android
