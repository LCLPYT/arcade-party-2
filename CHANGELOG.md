# Changelog

## [0.7.0](https://github.com/LCLPYT/arcade-party-2/compare/v0.6.0...v0.7.0) (2026-06-28)


### Features

* add 5 second cooldown for using the elevators on the office map in hot potato ([7e2e5e2](https://github.com/LCLPYT/arcade-party-2/commit/7e2e5e29fb6423b8925169a9b518212ccd3a2de6))
* add mining battle stats ([a93c316](https://github.com/LCLPYT/arcade-party-2/commit/a93c316c100fb78ee2b9e26df98ca420182f5992))
* add pagination, search and sorting to minigame voting screen ([#286](https://github.com/LCLPYT/arcade-party-2/issues/286)) ([f9a343c](https://github.com/LCLPYT/arcade-party-2/commit/f9a343c2040fdfe24a661bf0f87997b2581ddee6))
* add permanent glowing on the office map in hot potato ([71d29b0](https://github.com/LCLPYT/arcade-party-2/commit/71d29b06f63124ea86cf8db2ed5a6b0d769d201e))
* add quick sg stats ([71ba887](https://github.com/LCLPYT/arcade-party-2/commit/71ba8875ac8863ff8cb3c1117bf297e76176cec8))
* add stats to team gathering ([38299ea](https://github.com/LCLPYT/arcade-party-2/commit/38299ea556c03ee6a59fc342f8a48792f9388be3))
* add team gathering minigame ([#284](https://github.com/LCLPYT/arcade-party-2/issues/284)) ([20aeff9](https://github.com/LCLPYT/arcade-party-2/commit/20aeff9c32575d7da7f1bda4aa8ddfcbc80b2962))
* added regeneration after killing players in quick sg ([135e850](https://github.com/LCLPYT/arcade-party-2/commit/135e850e1ad09c5f9cf2b7a796b034f9086db21e))
* auto-balance teams according to current player ranking ([8b96424](https://github.com/LCLPYT/arcade-party-2/commit/8b96424788a28e03aef4f5b0c526dbf4d5cb0d34))
* defer first damage tick in pvp tournament death match so that the players aren't damaged instantly when announced ([70a0f19](https://github.com/LCLPYT/arcade-party-2/commit/70a0f192193355e675a1af7b2a0cbe0bb1a14ae2))
* give haste and remove item in inventory requirement for the solo player in team gathering ([86426ca](https://github.com/LCLPYT/arcade-party-2/commit/86426caf7ee87a88a425fd97f945b62c3a687894))
* let players instantly destroy blocks in turf wars ([57b1858](https://github.com/LCLPYT/arcade-party-2/commit/57b18585e486024aba56eba56f742eb2e6aa0cb0))
* localize the remaining health display decimal number ([8dd7096](https://github.com/LCLPYT/arcade-party-2/commit/8dd7096f58561bc9eed4f5f29c1d25cd0807b11f))
* made glowing bomb a little quicker for big player counts ([56dbb2a](https://github.com/LCLPYT/arcade-party-2/commit/56dbb2a795ba673156f95afec1506a18a4f6b0cc))
* make arrows one hit with the assassin kit in turf wars, but reduce the arrow gain duration to 1.5x ([e3502df](https://github.com/LCLPYT/arcade-party-2/commit/e3502dfbdb05dca3ed8815bbfc60d5e13524bd3e))
* reduced guess it rounds from 10 to 8 ([db309d3](https://github.com/LCLPYT/arcade-party-2/commit/db309d34345d89d5c86153e6b3ae29ceab717cd7))
* remove armor enchantments in quick sg ([7ca716a](https://github.com/LCLPYT/arcade-party-2/commit/7ca716af87593f7aeba1bf1961db85330bf13372))
* remove red marker from make weapon holder in weapon swap ([432e312](https://github.com/LCLPYT/arcade-party-2/commit/432e312707365a55bc0e53172c1791992102567b))
* replace stone blocks with barriers after mining battle is done ([229c73b](https://github.com/LCLPYT/arcade-party-2/commit/229c73b9ca5080150de407c20fcfd6ca353c0181))
* set the round count in guess it to always 10 rounds ([8660e85](https://github.com/LCLPYT/arcade-party-2/commit/8660e85ee110d165182a602bdf4f96feefbfd49e))
* show block distribution count in area challenge in guess it ([8e42ed8](https://github.com/LCLPYT/arcade-party-2/commit/8e42ed8fb04200e7d641889fa160385acf296de0))
* show remaining health of killer in one in the chamber and quick sg ([737470a](https://github.com/LCLPYT/arcade-party-2/commit/737470a6c67cc0ed1d80df5cce8f818afe582cf7))
* show winning team items at the end of team gathering ([1f80c67](https://github.com/LCLPYT/arcade-party-2/commit/1f80c6730ddb72cc037cbab6ce86c45ee0e0aacd))
* teleport players back to the spawn when falling during preparation time in block dissolve ([6698047](https://github.com/LCLPYT/arcade-party-2/commit/66980475abb5eacd03984e1340cef4a8f3571961))


### Bug Fixes

* change aim master average advance time stat so lower is better ([4d171ef](https://github.com/LCLPYT/arcade-party-2/commit/4d171ef8421ec6733c1efcc1b7bdeebf2a559756))
* docker image failing to start because of wrong versions ([0e1c26d](https://github.com/LCLPYT/arcade-party-2/commit/0e1c26d096d25070393b6be52bdcb44498526c32))
* fix crash when starting team games ([d5c4acc](https://github.com/LCLPYT/arcade-party-2/commit/d5c4acc698a4ddf2a2449532f1677272cc3fecd7))
* fix the warden behavior in maze scape by removing conflicting activities ([d096234](https://github.com/LCLPYT/arcade-party-2/commit/d0962342e15ed6f9a8dceb3c8fb3e767dee7b36f))
* fixed a bug where players would get eliminated for being close to walls in spleef ([1143a9e](https://github.com/LCLPYT/arcade-party-2/commit/1143a9ee3994df4ed5ca7ccb3793523408045174))
* freeze the placement scoreboard in pig race once someone reached the goal ([2498aa1](https://github.com/LCLPYT/arcade-party-2/commit/2498aa1a7bf4ae5f6433df588308de2c256a0b5a))
* paintball teams being assigned twice causing paintball not to work ([ccaa0a5](https://github.com/LCLPYT/arcade-party-2/commit/ccaa0a5c1e62e533f307895dfb4b249dfdab5460))
* rank players the same when they have the same statistic value in the statistics screen ([85e44fe](https://github.com/LCLPYT/arcade-party-2/commit/85e44fe54922fd026659941b7b179b32aa306eee))
* show completed rounds score detail again when quick mode is active in speed builders ([863c4e6](https://github.com/LCLPYT/arcade-party-2/commit/863c4e6a7ca9fd3f8ed97dcd74e9be731f657120))

## [0.6.0](https://github.com/LCLPYT/arcade-party-2/compare/v0.5.0...v0.6.0) (2026-06-19)


### Features

* add Assassins minigame ([#281](https://github.com/LCLPYT/arcade-party-2/issues/281)) ([1b1b467](https://github.com/LCLPYT/arcade-party-2/commit/1b1b46740ae929625f84453465adf9746c815292))
* add c2me and scalablelux to vastly speed up world gen ([ed537cd](https://github.com/LCLPYT/arcade-party-2/commit/ed537cdfe9d394766dbb4c0bf48f19c1f056e0de))
* upgrade to Minecraft 26.2 ([43b78fc](https://github.com/LCLPYT/arcade-party-2/commit/43b78fc2e152a8a06b886ea70e97a5c2fa4f239e))

## [0.5.0](https://github.com/LCLPYT/arcade-party-2/compare/v0.4.0...v0.5.0) (2026-06-15)


### ⚠ BREAKING CHANGES

* add WinManager as required property of MiniGameInstance
* introduce win manager composables
* use kotlin duration instead of explicit unit primitives
* add extension functions for timers, tasks and announcer

* add extension functions for timers, tasks and announcer ([2c1ce9f](https://github.com/LCLPYT/arcade-party-2/commit/2c1ce9f3044f9f3a0111de6edd21d3337a06ab7c))
* add WinManager as required property of MiniGameInstance ([f535b2d](https://github.com/LCLPYT/arcade-party-2/commit/f535b2d23fcb7724c2e4dadd522ddb3ae0ef1f96))
* introduce win manager composables ([f67b874](https://github.com/LCLPYT/arcade-party-2/commit/f67b87417f0a6abf7fcd83e6e56d412d56eb6abc))
* use kotlin duration instead of explicit unit primitives ([e5efd5d](https://github.com/LCLPYT/arcade-party-2/commit/e5efd5d47c4732799eb577d118a2485ebd019768))


### Features

* add mirror hop stats ([697bd0a](https://github.com/LCLPYT/arcade-party-2/commit/697bd0ac636214db1b7cd0d5e3aeb53748563766))
* add mirror hop stats ([0925a72](https://github.com/LCLPYT/arcade-party-2/commit/0925a7261f46145bf3408906c4314d5a9c87dafe))
* add rapid runner minigame ([4ad57cb](https://github.com/LCLPYT/arcade-party-2/commit/4ad57cb045cd61cc308f5437d3b8aabf6078e603))
* add RapidRunner setup ([91f0e06](https://github.com/LCLPYT/arcade-party-2/commit/91f0e060672a0a36c5a9e453c7f24c375d81c463))
* add seed display for generated maps ([37961d0](https://github.com/LCLPYT/arcade-party-2/commit/37961d0f9123beefedae85d7360ec1b51bc50089))
* add snowball fight stats ([3babc15](https://github.com/LCLPYT/arcade-party-2/commit/3babc15f41bc34d13584491d73b6f7006175dd43))
* add snowball fight stats ([da07940](https://github.com/LCLPYT/arcade-party-2/commit/da079406eaf2288d2186adeb4d97fe8cf85faeae))
* implement scoreboard data sync for fractional numbers ([c27cad6](https://github.com/LCLPYT/arcade-party-2/commit/c27cad6d0a87ab490fa441fbe599f4af8c78fd88))
* implement spawn finder for random levels ([562b11b](https://github.com/LCLPYT/arcade-party-2/commit/562b11b4632b0eeb8475409bf8217317ad97164f))
* make the weapon holder in weapon swap a bit faster than other players ([0615710](https://github.com/LCLPYT/arcade-party-2/commit/0615710e8bf9267a71f793d432025376a6e316a6))
* make the weapon holder in weapon swap a bit faster than other players ([469ef3e](https://github.com/LCLPYT/arcade-party-2/commit/469ef3eabe203fa52b234fd32ec754f452c6f93a))
* mark the weapon holder red in weapon swap ([01c2fa6](https://github.com/LCLPYT/arcade-party-2/commit/01c2fa6206205144b89da50f41d0bccadf8c50ec))
* mark the weapon holder red in weapon swap ([93c93ed](https://github.com/LCLPYT/arcade-party-2/commit/93c93ed202ad0a08a8a970b3f7e352b526f52f2e))
* merge data result into ranking section in stats display ([e706fdc](https://github.com/LCLPYT/arcade-party-2/commit/e706fdc96e4820492721de174396c22dfdc77b32))
* temporary overworld level generation ([9a8030c](https://github.com/LCLPYT/arcade-party-2/commit/9a8030c33d716bffc342380381d49b09c74404ea))


### Bug Fixes

* always apply visibility of vehicle entities affected by the visibility handler ([6581371](https://github.com/LCLPYT/arcade-party-2/commit/6581371ef37cc392cae98f6b7e367763a340ba3d))
* correct mimicry stats ordering and remove button click type distinction ([810470e](https://github.com/LCLPYT/arcade-party-2/commit/810470e705b449b8cd8de9edcb339c0fd0842ee1))
* correct mimicry stats ordering and remove button click type distinction ([ce4322d](https://github.com/LCLPYT/arcade-party-2/commit/ce4322dd48d98d355eed1a449d1e3f647869020f))
* dispatch initial score events for scoreboard stat sync again ([80ec65e](https://github.com/LCLPYT/arcade-party-2/commit/80ec65e75519685ead013bc1ec9af28023d22c09))
* game end in weapon swap now lets the actually remaining players win ([42d7b83](https://github.com/LCLPYT/arcade-party-2/commit/42d7b8328e992674f215de7ddb0167d475baccbe))
* game end in weapon swap now lets the actually remaining players win ([7ad87be](https://github.com/LCLPYT/arcade-party-2/commit/7ad87be50fa6da54279b2340e6b2dcf997e6c6e7))
* locator bar not being disabled ([5205313](https://github.com/LCLPYT/arcade-party-2/commit/5205313d4a7ab657daff4ec6293dc2c959354af4))
* paintball factory setup ([12f23a1](https://github.com/LCLPYT/arcade-party-2/commit/12f23a1947f6c1d6505997c0c3a6453dc7f61ade))
* player stuck in loading screen when respawning immediately ([6e27509](https://github.com/LCLPYT/arcade-party-2/commit/6e27509688a887b5c21dd53f610d1f893d03ae21))
* pvp tournament match start ([6daae45](https://github.com/LCLPYT/arcade-party-2/commit/6daae450d8584d9cadada5c376f1be7388245d36))
* rapid runner translation keys ([27faea2](https://github.com/LCLPYT/arcade-party-2/commit/27faea29168430233ac2d36523675a74fb456f02))
* visibility toggle sometimes still showing players ([c69c298](https://github.com/LCLPYT/arcade-party-2/commit/c69c2989b0e58f6acb000aead2d268e254efd16e))
* win manager data container init order ([0a6cabf](https://github.com/LCLPYT/arcade-party-2/commit/0a6cabfa105b5e25950daa41cd765d5f70ca3f04))

## [0.4.0](https://github.com/LCLPYT/arcade-party-2/compare/v0.3.0...v0.4.0) (2026-06-07)


### Features

* add additional stats to glowing bomb minigame ([67c0877](https://github.com/LCLPYT/arcade-party-2/commit/67c0877cdbf46ba7320562da93416e7ca05c5e7c))
* add additional stats to red light green light ([079e5ae](https://github.com/LCLPYT/arcade-party-2/commit/079e5aeb4906706f43818c40f1c512a1a535a6d6))
* add aim eggventure stats and simplify stats manager creation ([099562d](https://github.com/LCLPYT/arcade-party-2/commit/099562d9dda2795f27ca155ff4c088379fea11a7))
* add aim master stats and extract FFAStatsManager ([635b52d](https://github.com/LCLPYT/arcade-party-2/commit/635b52dbd53556eb74e018e6c91d964726bcc615))
* add announcement for sudden death in pvp tournament ([000b7c5](https://github.com/LCLPYT/arcade-party-2/commit/000b7c5a56374e4d1d585160aefba011eec388d1))
* add anti camping feature and speed up over time ([15aa5cd](https://github.com/LCLPYT/arcade-party-2/commit/15aa5cdda2f47ecfea95985db492aeb6311c7776))
* add average advance time to aim master stats ([a146dd2](https://github.com/LCLPYT/arcade-party-2/commit/a146dd2498f47b891551992b3d15a8cb6c92cd37))
* add block dissolve stats ([b4e2632](https://github.com/LCLPYT/arcade-party-2/commit/b4e263257bcbdca50aaef4236025948ed83ce4d7))
* add button master and apocalypse survival stats ([be63bee](https://github.com/LCLPYT/arcade-party-2/commit/be63beec7fcc44d793259811c3045ce3ab0599e6))
* add EntityPushEntityCallback and improve pillar battle kill credit ([f26d220](https://github.com/LCLPYT/arcade-party-2/commit/f26d220edd0510affc3b5b157f0b36ce8b347e4e))
* add FontService to estimate client font width ([b485bad](https://github.com/LCLPYT/arcade-party-2/commit/b485bad8f189f243c019e1542cc8d6a5ff423ad5))
* add fuel remaining stat to cozy campfire and fix death stat ([68d19d7](https://github.com/LCLPYT/arcade-party-2/commit/68d19d73e0edc40fec5acf670bcebb744b5371ad))
* add game summary display to stats dialog ([f4c9a3b](https://github.com/LCLPYT/arcade-party-2/commit/f4c9a3b5821472d7a00886ead3c2bc30bec4d29c))
* add initial delay to turf wars timers ([03dc1bb](https://github.com/LCLPYT/arcade-party-2/commit/03dc1bb0f78938a9ac3922c03a05a16abeceafff))
* add kill credit in pillar battle ([5a8a5ab](https://github.com/LCLPYT/arcade-party-2/commit/5a8a5abcb9d5f4a3d3ad70ed96228dcf260d33c4))
* add KnockbackKillTracker to track eliminations ([413b908](https://github.com/LCLPYT/arcade-party-2/commit/413b90859eb8f75887aab9e17e8d5fdb48ad357f))
* add knockout stats ([cccb8d1](https://github.com/LCLPYT/arcade-party-2/commit/cccb8d1a050a5ed9ce972e82f3b501610754f412))
* add maniac digger pipe grading debugger ([f3bcecb](https://github.com/LCLPYT/arcade-party-2/commit/f3bcecb9c0a6e17864ff1c7abd1146a41472bb7c))
* add MdPipePath to better match pipe progress ([981d1e1](https://github.com/LCLPYT/arcade-party-2/commit/981d1e11a7f34940a7bae77331c08d9061bd1ef5))
* add melodies completed and correct notes stats to fine tuning ([1d879b1](https://github.com/LCLPYT/arcade-party-2/commit/1d879b11fa3d9c0f7ee9962c2a2ff117a23f97d8))
* add message for camping elimination ([0a4ec6f](https://github.com/LCLPYT/arcade-party-2/commit/0a4ec6f8f9b6f249892a36b27c3d1099a8654407))
* add messages for anti camping and speedup ([68a3347](https://github.com/LCLPYT/arcade-party-2/commit/68a334781144e8da6aa74b2a0d864c3cf32d4ba1))
* add random spawn positions in spleef ([f8daf7f](https://github.com/LCLPYT/arcade-party-2/commit/f8daf7f87b4ba8140e9dd93cd4f336512d9b9c0d))
* add ranking to stats screen and ensure default values are set ([7dd299c](https://github.com/LCLPYT/arcade-party-2/commit/7dd299c2cca6168ac5d941b782a9efe10a09bec3))
* add separator between team and player stats ([78f469f](https://github.com/LCLPYT/arcade-party-2/commit/78f469f79895693091caae2747c37ce51c099b2c))
* add stats for fine tuning ([4c0da03](https://github.com/LCLPYT/arcade-party-2/commit/4c0da03bb2de50a544ac015e02372689e87cf606))
* add stats for glowing bomb ([3ccb167](https://github.com/LCLPYT/arcade-party-2/commit/3ccb1673e1824b569ea3353a98adc2a3b49852b3))
* add stats for maniac digger ([7b7706a](https://github.com/LCLPYT/arcade-party-2/commit/7b7706aa5fe5b593e9328b817c8b2ae71f1312c4))
* add stats for mimicry ([c926e36](https://github.com/LCLPYT/arcade-party-2/commit/c926e360b6d027e7d501fd7fdb3f70fd91072ef1))
* add stats for paintball ([be27874](https://github.com/LCLPYT/arcade-party-2/commit/be2787485b1f64c51aa857bd1281c00945bf9c2e))
* add stats to anvil fall ([4410995](https://github.com/LCLPYT/arcade-party-2/commit/4410995fc9f234009b409dd800123cc342bc84e9))
* add stats to cozy campfire ([d2ba67e](https://github.com/LCLPYT/arcade-party-2/commit/d2ba67ed798e6ab56922a0a26ee50200c5ba20ca))
* add stats to minefield ([0d1aaa3](https://github.com/LCLPYT/arcade-party-2/commit/0d1aaa39fc742da9fdf5472347027be4c21fc776))
* add stats to pillar battle ([5b7ded4](https://github.com/LCLPYT/arcade-party-2/commit/5b7ded409c71c466d134f3a816a4df814d1f5be0))
* add stats to red light green light ([3029b30](https://github.com/LCLPYT/arcade-party-2/commit/3029b304236582acbf65cf6edc49031bb0249e6c))
* add team balancing ([d7649bc](https://github.com/LCLPYT/arcade-party-2/commit/d7649bc77384807075b7875c8082720e2a8e132b))
* add title for build phase ([3b27e19](https://github.com/LCLPYT/arcade-party-2/commit/3b27e190cc2a9ef95e2020c449746170fc11af91))
* add units to stats ([20624b0](https://github.com/LCLPYT/arcade-party-2/commit/20624b08ec1c1f3a25478f9493d13045e050aedf))
* begin turf wars minigame ([08c0c58](https://github.com/LCLPYT/arcade-party-2/commit/08c0c58610af3c31aa577114dc0c09f799b77bdc))
* change default player visibility to partial in minefield and make some vars lateinit ([932aa62](https://github.com/LCLPYT/arcade-party-2/commit/932aa62d890bda17748f569e8c41a4014524dea8))
* change minigames random durations to fixed durations for better comparability ([8f3d865](https://github.com/LCLPYT/arcade-party-2/commit/8f3d865b65441f4fd40591f61caa326d451c17d1))
* eliminate both teams at the same time when both didn't enter the turf ([c25515b](https://github.com/LCLPYT/arcade-party-2/commit/c25515b81951bf99ac809be64e3c9f2c2f2febd1))
* fixed score goal for aim master ([8b3334e](https://github.com/LCLPYT/arcade-party-2/commit/8b3334ee67c6a9f38bdf515823cb8aec87215da0))
* implement bow spleef stats and generalize FallKitTracker ([40fabf2](https://github.com/LCLPYT/arcade-party-2/commit/40fabf28711e9589ca95518680383db171105a12))
* implement enemy turf repel ([0a50f72](https://github.com/LCLPYT/arcade-party-2/commit/0a50f72b83b92d58fb2d80160bd45a4c3439eba0))
* implement maze scape world border shrinking ([9926ace](https://github.com/LCLPYT/arcade-party-2/commit/9926ace286d734812a887fd2a4b77e4dde105b62))
* implement SpleefKillTracker ([31e4776](https://github.com/LCLPYT/arcade-party-2/commit/31e477617c2fd03b2a5112b13b7e4ccc0c50c8ac))
* implement stat-major stat display ([c1b2842](https://github.com/LCLPYT/arcade-party-2/commit/c1b2842dbd96a583d6851678a7fb4a9ec8d4e637))
* implement team stats display and implement turf wars stats ([18ede0b](https://github.com/LCLPYT/arcade-party-2/commit/18ede0b99c2cb86b29962fcdb8ce26e2be1d8f8b))
* implement TeamStatsManager ([04da554](https://github.com/LCLPYT/arcade-party-2/commit/04da5544dc4fab5646306a4cbee5b1691981c9b5))
* improve kill message credit in pillar battle ([28e3d5c](https://github.com/LCLPYT/arcade-party-2/commit/28e3d5c5ece283f72bd9b984fd7fb638765c5976))
* introduce DyeBlockManager and implement turf growing / shrinking ([906d260](https://github.com/LCLPYT/arcade-party-2/commit/906d260bf84d54b8d74458c4ddde3b8146493dfa))
* introduce game summary ([b197ace](https://github.com/LCLPYT/arcade-party-2/commit/b197ace0d3d3bc502936a9032ccdcf96b4c63655))
* introduce TeamColorUtil and implement basic turf wars hooks ([7f67430](https://github.com/LCLPYT/arcade-party-2/commit/7f67430a278f96eb8637c4d8dafe92c0d415d0c9))
* make eggventure shorter ([c67cc87](https://github.com/LCLPYT/arcade-party-2/commit/c67cc87c6b29b885a575a127b7e45ad94956ed5e))
* make the secondary separator as wide as the primary separator in ResultAnnouncement ([1de8181](https://github.com/LCLPYT/arcade-party-2/commit/1de8181017d925380c8d2a0727107088fec968e8))
* override time limit in aim master ([f83edc8](https://github.com/LCLPYT/arcade-party-2/commit/f83edc85675bf68b7367b00aefd604918ced2ba4))
* properly format float stats ([34560b7](https://github.com/LCLPYT/arcade-party-2/commit/34560b7dd2a5c115da1370ee37f5eedca4e54d82))
* refactor mixins and add CanShootProjectileCallback ([6db7637](https://github.com/LCLPYT/arcade-party-2/commit/6db7637a86bb14b8c13f1bca8bde6d3359c2c4d8))
* remove per stat ranking ([870594a](https://github.com/LCLPYT/arcade-party-2/commit/870594aa366078ca55afbadcb3efdd51e7611a1b))
* rename getWorld() to getLevel() to match new mappings ([b1eb4ca](https://github.com/LCLPYT/arcade-party-2/commit/b1eb4caebed5ed7c2ee50bccb064bb47b3bfd5fe))
* rewrite kit system in kotlin and add turf wars kits ([ddaa7d9](https://github.com/LCLPYT/arcade-party-2/commit/ddaa7d9e6f058d79b56c547955bf986c47a9c89d))
* slightly buff sniper kit in paintball ([eee82d6](https://github.com/LCLPYT/arcade-party-2/commit/eee82d6e0a06197bb478e260e6e79decb1bc5acc))
* support additional breakable blocks and freezing damage in spleef ([8fa9618](https://github.com/LCLPYT/arcade-party-2/commit/8fa961846871a599d35557643ca50f19e556bbaa))
* support non-box like chicken spawn boxes and add random player spawns in chicken shooter ([35590ba](https://github.com/LCLPYT/arcade-party-2/commit/35590ba15e57f228f539256def7e2e7ce429fdd2))
* unified kill credit and improve pillar battle kill credit ([0090a4a](https://github.com/LCLPYT/arcade-party-2/commit/0090a4a7b6ada90ad68c614bdb7ec13a60e65824))
* verify players are always on breakable blocks in spleef ([83bed80](https://github.com/LCLPYT/arcade-party-2/commit/83bed80bf3a7b8a4857f1f0646d63699f3a5aab5))


### Bug Fixes

* apocalypse survival access widener loading ([afbac8a](https://github.com/LCLPYT/arcade-party-2/commit/afbac8a2a88cbc5b1a31c038f59d19af7a1341b6))
* arrow task ([6fd7456](https://github.com/LCLPYT/arcade-party-2/commit/6fd7456eaef041f99e4f20d2a484161d61a3d273))
* damage tracking for cozy campfire and weapon swap ([bdf3716](https://github.com/LCLPYT/arcade-party-2/commit/bdf37167ae91addb49a6c894ca7a4641f6a72d97))
* ignore armor and sword blocking damage reduction ([0e10410](https://github.com/LCLPYT/arcade-party-2/commit/0e1041063bedb529081f66e615d916ed80745a82))
* only count residing in own base as camping ([9c5a61d](https://github.com/LCLPYT/arcade-party-2/commit/9c5a61d74a471c8a9cdba7c3eb1e693f949867a0))
* path positioning for grading in maniac digger ([7f8bab7](https://github.com/LCLPYT/arcade-party-2/commit/7f8bab753622e7823fb3fb6c07f1dd3138b9b9ba))
* register missing fine tuning stats ([a0714e6](https://github.com/LCLPYT/arcade-party-2/commit/a0714e680afb3f16d1aaf52b2d8866057dbcd612))
* remove units from stat labels ([a117b32](https://github.com/LCLPYT/arcade-party-2/commit/a117b32aff964865f63c03170df6317c3f8a5721))
* repel hitbox detection ([5dbf485](https://github.com/LCLPYT/arcade-party-2/commit/5dbf485aa8f07e3d13bf9e4c58f88a8e8ed8de36))
* turf wars kit item equipment ([c9d00bb](https://github.com/LCLPYT/arcade-party-2/commit/c9d00bbe4a19a0394674026f047891aae4900c57))
* unstuck player from repel after 20 ticks ([a44ea9b](https://github.com/LCLPYT/arcade-party-2/commit/a44ea9b2eac603db5af67e84c8a09cd3ee80fe5f))
* use initial game participants for game summary ([c5a615e](https://github.com/LCLPYT/arcade-party-2/commit/c5a615e99afed6adabff2adebc108afc56222150))
