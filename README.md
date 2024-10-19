
## What is Armored Attire?

_Armored Attire_ is a simple, server-side solution to cosmetic overrides for Spigot.

## How does Armored Attire work?

**High-level**

_Armored Attire_ applies an armor, tool, or weapon as the cosmetic override of another by encoding the source item as NBT data on the target item. When present, the cosmetic (source) item is shown to other players instead of the original (target) item. The display of cosmetic overrides is restricted to players and armor stands.

Additionally, _Vanilla Tweaks_' _Armored Elytra_ has first-class support. The elytra's chestplate functions as a cosmetic override and will be shown whenever the player is not flying. A cosmetic chestplate added by _Armored Attire_ will override the elytra's chestplate as expected, but it must be applied to the elytra, not the elytra's chestplate. Cosmetic overrides cannot be nested.

**Low-level**

_Armored Attire_ intercepts and re-writes entity equipment packets broadcast by the server with items reconstituted from `cosmetic_override` NBT data when appropriate criteria is met. In the case of _Armored Elytra_, movement is tracked to trigger updates in the first place.

## How do I use Armored Attire?

**Server**

Drop the JAR and its dependencies, NBTAPI and ProtocolLib, in the `plugins` directory and restart the server. You may already have these if you're running a modded server. Regardless, ensure they're up-to-date (newer is probably fine) by cross-referencing the versions you have with the versions in `build.gradle`.

**Client**

- `/attire clear`
  - Remove the cosmetic override from the item in the main hand.
- `/attire hide <head|body|legs|feet>`
  - Add a dummy "none" cosmetic override to the item in the slot to prevent it from showing at all.
- `/attire preview`
  - Temporarily show the player their cosmetic overrides. This will last until the player receives another equipment update from the server. The player is not normally shown their overrides because it causes _a lot_ of problems. **WARNING: Entering creative mode while a preview is shown will likely cause the overrides to replace the original items!**
- `/attire set`
  - Set the item in the main hand to be the cosmetic override of the corresponding armor slot, or tool or weapon in the off hand.

## How do I build Armored Attire?

**Windows**

With Java 21, run `gradlew.bat build` and check `build/libs` for the JAR. A build environment may need to be prepared first with other Gradle tasks. Refer to Spigot development documentation for further information.

## Legal stuff

_Armored Attire_ is licenced under [LGPL 3.0](LICENCE.md) unless where otherwise stated. Markdown-formatted licences are provided by [IQAndreas/markdown-licenses](https://github.com/IQAndreas/markdown-licenses).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG.
