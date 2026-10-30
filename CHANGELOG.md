# Changelog

## [0.3.0](https://github.com/derekwinters/simple-stretch-android/compare/v0.2.0...v0.3.0) (2026-10-30)


### Features

* **home:** open the stretch session from a Today row ([#79](https://github.com/derekwinters/simple-stretch-android/issues/79)) ([b3c501c](https://github.com/derekwinters/simple-stretch-android/commit/b3c501cc5be8a89f04f43b291074a1191621c641)), closes [#64](https://github.com/derekwinters/simple-stretch-android/issues/64)
* rename the launcher label to "Simple Stretch" ([#91](https://github.com/derekwinters/simple-stretch-android/issues/91)) ([513ae23](https://github.com/derekwinters/simple-stretch-android/commit/513ae23185392fe4e9e40d17174d1f5764f52d78)), closes [#84](https://github.com/derekwinters/simple-stretch-android/issues/84)
* **store:** render the Play store icon and feature graphic ([#87](https://github.com/derekwinters/simple-stretch-android/issues/87)) ([5c81ecb](https://github.com/derekwinters/simple-stretch-android/commit/5c81ecb86c2476e52c6f708f4afca8a437fd2dca)), closes [#45](https://github.com/derekwinters/simple-stretch-android/issues/45)


### Fixes

* **home:** show the app name in the Home top bar ([#97](https://github.com/derekwinters/simple-stretch-android/issues/97)) ([2bc1f48](https://github.com/derekwinters/simple-stretch-android/commit/2bc1f48870a6b1dc8fc2c7ac72adf8e351913607)), closes [#96](https://github.com/derekwinters/simple-stretch-android/issues/96)
* **scheduling:** request SCHEDULE_EXACT_ALARM instead of USE_EXACT_ALARM ([#69](https://github.com/derekwinters/simple-stretch-android/issues/69)) ([5371b1c](https://github.com/derekwinters/simple-stretch-android/commit/5371b1cad2f86b0706506753a28c2c29bfba6327)), closes [#28](https://github.com/derekwinters/simple-stretch-android/issues/28)


### Documentation

* **privacy:** say what the app itself does in the policy's opening line ([#89](https://github.com/derekwinters/simple-stretch-android/issues/89)) ([350c228](https://github.com/derekwinters/simple-stretch-android/commit/350c2280439e5ea3e4f049bdf3409212b33636b9)), closes [#86](https://github.com/derekwinters/simple-stretch-android/issues/86)
* publish a privacy policy for the Google Play listing ([#72](https://github.com/derekwinters/simple-stretch-android/issues/72)) ([13104d3](https://github.com/derekwinters/simple-stretch-android/commit/13104d351e89e5f77bd6f55562fb662be01fe793)), closes [#34](https://github.com/derekwinters/simple-stretch-android/issues/34)
* record the Play App Signing decision in ADR 0004 ([#76](https://github.com/derekwinters/simple-stretch-android/issues/76)) ([3ec1ae4](https://github.com/derekwinters/simple-stretch-android/commit/3ec1ae465c4ef0a9b4d5c10d1131c65ceb5251ba)), closes [#43](https://github.com/derekwinters/simple-stretch-android/issues/43)
* **store:** settle the Play listing text, category and tags ([#85](https://github.com/derekwinters/simple-stretch-android/issues/85)) ([3398ed5](https://github.com/derekwinters/simple-stretch-android/commit/3398ed5f3a10891f4e841280903fa5e4b074c748)), closes [#44](https://github.com/derekwinters/simple-stretch-android/issues/44)


### Refactoring

* read the current time from one injected clock ([#78](https://github.com/derekwinters/simple-stretch-android/issues/78)) ([be7903d](https://github.com/derekwinters/simple-stretch-android/commit/be7903df2eda3b7f4c707980bbc06c189d992ab5)), closes [#30](https://github.com/derekwinters/simple-stretch-android/issues/30)

## [0.2.0](https://github.com/derekwinters/simple-stretch-android/compare/v0.1.0...v0.2.0) (2026-10-22)


### Features

* **goals:** set daily goals with a stepper on every stretch ([#52](https://github.com/derekwinters/simple-stretch-android/issues/52)) ([0e7f818](https://github.com/derekwinters/simple-stretch-android/commit/0e7f818a6c1a79e5963686f617e087b2a991eb73)), closes [#20](https://github.com/derekwinters/simple-stretch-android/issues/20)
* **home:** move Skip today from the top card to the Today row ([#57](https://github.com/derekwinters/simple-stretch-android/issues/57)) ([70df852](https://github.com/derekwinters/simple-stretch-android/commit/70df852b7b3e6b7290bb3d365db513bd92710aa6)), closes [#21](https://github.com/derekwinters/simple-stretch-android/issues/21)
* **notifications:** remove the Skip today action from reminders ([#59](https://github.com/derekwinters/simple-stretch-android/issues/59)) ([db41fac](https://github.com/derekwinters/simple-stretch-android/commit/db41facb153d639d95682d422c450d821300a39f)), closes [#25](https://github.com/derekwinters/simple-stretch-android/issues/25)
* **session:** lead with the reminder's stretches when a notification opens the session ([#61](https://github.com/derekwinters/simple-stretch-android/issues/61)) ([20f191c](https://github.com/derekwinters/simple-stretch-android/commit/20f191cf397ff9b788390668edbaf26da15ea067)), closes [#29](https://github.com/derekwinters/simple-stretch-android/issues/29)
* **widget:** segmented ring home screen widget for today's goals ([#55](https://github.com/derekwinters/simple-stretch-android/issues/55)) ([88e2e3e](https://github.com/derekwinters/simple-stretch-android/commit/88e2e3ef7494635d6d2c2fe36e9b3e711e3edc3b)), closes [#24](https://github.com/derekwinters/simple-stretch-android/issues/24)

## 0.1.0 (2026-10-11)


### Features

* **build:** sign release builds with the stable release keystore ([#6](https://github.com/derekwinters/simple-stretch-android/issues/6)) ([210cbb7](https://github.com/derekwinters/simple-stretch-android/commit/210cbb7b61e13c7ee3e7a0277fbccee807468f8b))
* daily stretch goals, repeating reminders and stretch sessions ([#2](https://github.com/derekwinters/simple-stretch-android/issues/2)) ([e3f731b](https://github.com/derekwinters/simple-stretch-android/commit/e3f731b1d3d9e7b34500c8f2773cc452231ddcf2))
* **icon:** side-bend stretch figure on a yoga mat ([#19](https://github.com/derekwinters/simple-stretch-android/issues/19)) ([13ee404](https://github.com/derekwinters/simple-stretch-android/commit/13ee404e5ad034bcf94dda95d73796a1c414457d)), closes [#18](https://github.com/derekwinters/simple-stretch-android/issues/18)
* initial stretch reminder app ([#1](https://github.com/derekwinters/simple-stretch-android/issues/1)) ([54d61ab](https://github.com/derekwinters/simple-stretch-android/commit/54d61abe90bfeac97492e1d84c1b14fb08cb4aa4))
* weekly and monthly stretch trends with daily drill-down ([#3](https://github.com/derekwinters/simple-stretch-android/issues/3)) ([212dc32](https://github.com/derekwinters/simple-stretch-android/commit/212dc32ec147234dc5aa2d6067d50f15051d554b))


### Fixes

* **icon:** match launcher colors to the chores app ([#17](https://github.com/derekwinters/simple-stretch-android/issues/17)) ([99221d7](https://github.com/derekwinters/simple-stretch-android/commit/99221d7487ee729af31cd1a8889c6b5647765ef7)), closes [#16](https://github.com/derekwinters/simple-stretch-android/issues/16)
* **release:** start versioning at 0.1.0 ([#5](https://github.com/derekwinters/simple-stretch-android/issues/5)) ([37336ed](https://github.com/derekwinters/simple-stretch-android/commit/37336ed181f022c05d73f7160e8f0049c50eeda6))
