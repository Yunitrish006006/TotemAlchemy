# Description evidence for TotemAlchemy 0.1.49

Published JAR audit: Minecraft ~26.2, Fabric Loader >=0.19.3, Java >=25, Fabric API, and totem-core >=0.7.18 <0.8.0. Release source is commit 4fbbaf2; subsequent commits before this rewrite recorded publication or documentation.

| Claim | Source |
| --- | --- |
| Success rates, multiple effects, mushroom starter | README.md brewing sections; mixture/AlchemyMixtureBrewing.java; mixin/BrewingStandBlockEntityMixin.java |
| Book at Brewing Stand | manual/AlchemyManual.java; Core api/v1/manual/TotemManualPlayerHelper.java accepts Items.BOOK |
| Persistent research and time estimates | discovery/AlchemyDiscoverySavedData.java; AlchemyDiscoveryService.java; AlchemyProcessingTimeEstimate.java |
| Reaction timing and stable finished bottles | mixture/AlchemyMixtureState.java: lockHeatIfFinished and completed-stage timing |
| Gravel returns bowl; sulfur collection | recipe/FlintFromBowlRecipe.java; item/StoneBowlItem.java |
| Material loops, drinks and recipe progression | README.md; data/totem/alchemy/cauldron_recipes/{saltpeter,hot_cocoa,cherry_brew}.json; data/totem/recipe/alchemy/; data/totem/advancement/alchemy/recipes/ |
| Data pack customization | data/totem/alchemy/{cauldron_recipes,brewing_material_settings,brewing_outcome_weights}/ |

Java paths are relative to src/main/java/dev/totem/alchemy/ unless marked Core; data paths are relative to src/main/resources/. Public dependency branding remains TotemLibrary at /mod/totemlibrary; the Fabric dependency ID remains totem-core. No runtime or artwork changes are part of this rewrite.
