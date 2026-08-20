# TotemAlchemy

**Survival alchemy with uncertain outcomes, persistent research, and heat-controlled potion mixtures.**

TotemAlchemy extends Minecraft 26.2 brewing without replacing its familiar materials and containers. Ingredients can reveal several effects, experiments build a personal research journal, and Alchemy Cauldrons let players mix, interrupt, bottle, resume, and overcook reactions.

## Overview

- Uses normal Brewing Stands, potion containers, books, ingredients, and vanilla-style interaction feedback.
- Adds stone bowls, pig manure, wood ash, saltpeter, hot cocoa, Cherry Swiftness, Firefly Strength, large flasks, and Alchemy Cauldrons.
- Stores discoveries and research samples per player in the world.
- Works on both the client and server and requires TotemCore.

## Brewing

- Each successful effect ingredient rolls every candidate effect independently, so one batch can produce several effects at once.
- Every compatible bottle in one Brewing Stand batch receives the same selected effect set.
- If every independent roll misses, one weighted fallback effect is guaranteed.
- Redstone, glowstone dust, gunpowder, and dragon's breath remain duration, potency, splash, and lingering modifiers.
- Nether Wart and data-pack-defined starter materials activate a potion base before normal effect work begins.

## Research

- Only effects actually observed by the player are revealed in the Totem Manual.
- Simultaneous effects are all recorded while still counting as one material sample for that batch.
- Repeated experiments narrow the estimated processing-time range and increase its displayed accuracy.
- Low-confidence research never sends the exact observed average time to the client.

## Cauldron

- Different potions can share one Alchemy Cauldron while preserving effect quantity and liquid volume.
- Reactions require heat, continue independently, and can be bottled early and resumed later.
- The native crosshair panel shows capacity and qualitative timing such as “Almost there”, “Perfect”, or “A little overdone”.
- The panel is always available for Alchemy Cauldrons and does not depend on Jade or WTHIT.
- Continued heating after completion reduces stability and can eventually mutate the mixture.

## Data Packs

Server data packs can configure material processing ticks, choose which materials activate a base, and adjust hidden effect weights. Relative effect weights become independent appearance chances; the exact distribution remains something players learn through experiments.

Built-in examples are included under `data/totem/totem_alchemy/` in the JAR, and the full JSON paths and formats are documented in the project README.

## Quick Start

1. Install TotemCore, Fabric API, and TotemAlchemy on both the client and server.
2. Hold a Book or Totem Manual and use it on a Brewing Stand to obtain the Alchemy chapter.
3. Brew ingredients repeatedly to reveal their real outcomes and improve the journal's time estimate.
4. Use an Alchemy Cauldron over a lit Campfire for slower reactions that can be watched, bottled, and resumed.

## Requirements

- Minecraft `26.2`
- Fabric Loader `0.19.3` or newer
- Fabric API `0.154.2+26.2`
- TotemCore `0.7.x`
- Java `25`

## Installation

Place matching TotemCore and TotemAlchemy JARs in `mods/` on both the client and server. When using a DeadRecall bundle that already contains TotemAlchemy, do not install the standalone module beside it.

Source, issue tracking, data-pack examples, and detailed gameplay notes are available in the [GitHub repository](https://github.com/Yunitrish006006/TotemAlchemy).
