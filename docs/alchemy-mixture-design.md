# Alchemy Cauldron Mixture Design Draft

Status: discussion draft; no runtime implementation is implied by this document.

## Goal

Evolve the Alchemy Cauldron from a recipe-specific cooker into a persistent liquid-state alchemy vessel that can hold incomplete brews, completed potion-like mixtures, and blends poured from multiple bottles.

## Core model

The cauldron owns a persistent `MixtureState` rather than only a recipe id plus completion flag. A mixture records:

- liquid volume / bottle-equivalent units
- source fluids and bottled inputs
- ingredient history and remaining unresolved ingredients
- per-ingredient processing progress and elapsed/remaining brew time
- temperature / heat state
- known and unknown effect contributions
- potency, duration and concentration values per effect contribution
- instability / contamination score
- provenance needed for discovery and manual journal updates

The mixture state is server-authoritative and persists with the cauldron block entity.

## Bottling incomplete mixtures

A glass bottle may extract one bottle-equivalent from the cauldron even if the mixture is not complete.

The resulting bottled item must preserve the extracted snapshot, including:

- unresolved ingredients
- processing progress
- elapsed and remaining brew time
- current effect contributions, even if not yet resolved into a final vanilla potion
- instability / contamination
- source mixture identity or provenance where required for deterministic continuation

Tooltip / manual UI should clearly distinguish an incomplete alchemical mixture from a finished potion.

An incomplete bottle may later be poured back into a compatible cauldron and continue processing from its stored state rather than restarting from zero.

## Pouring bottles into cauldrons

Potion-like bottles and Alchemy mixture bottles may be poured into a cauldron. The bottle contents are merged into the cauldron's `MixtureState` instead of replacing it.

Different mixtures may therefore coexist in the same cauldron. Merge rules operate on liquid volume, ingredient state, effect contributions, progress and instability.

The empty glass bottle is returned after a successful pour.

## Effect composition and opposition

Effects are not merged only by potion id. They are represented as contributions in effect families / axes so that opposite effects can interact.

Initial families proposed for discussion:

- movement: Speed <-> Slowness
- damage output: Strength <-> Weakness
- health delta: Healing <-> Harming
- visibility: Night Vision <-> Darkness / Blindness where appropriate
- levitation / gravity-like effects only if an explicit family rule exists

Opposition is resolved quantitatively from effective strength, duration and concentration.

Example principle:

- equal opposing contributions neutralize each other
- the stronger side survives with reduced potency and/or duration
- partial cancellation may create a neutral residue instead of silently deleting all chemistry

Effects without an explicit opposition rule coexist normally.

## Potency and duration

Each effect contribution should track at least:

- effect id / family
- amplifier-equivalent potency
- duration-equivalent amount
- concentration / bottle-volume share

Mixing two bottles should conserve total effect quantity before opposition and dilution are applied. Simply choosing the higher amplifier is not acceptable because it loses information.

## Dilution

Adding water or low-potency liquid increases volume and lowers concentration. This can reduce effective amplifier thresholds while extending the amount of drinkable liquid.

This makes the cauldron behave as a real mixing vessel rather than a recipe selector.

## Brewing progress after mixing

When two mixtures with different processing states are combined, the resulting state must not simply use `min()` or `max()` remaining time.

Proposed rule for discussion:

- completed chemical contributions remain completed
- unresolved ingredient reactions retain their own progress records
- newly introduced interactions may create new reaction tasks with their own required processing time
- heat only advances reactions that are eligible under the current temperature / catalyst conditions
- each completed material stage starts a perfect extraction window equal to one quarter of that stage's
  processing time, clamped to 5–15 seconds; overcook decay begins only after that window expires
- compatible materials may react concurrently, but every material retains its own insertion-relative progress,
  perfect window, and overcook clock; completing early does not wait for the other active reactions

This allows a half-brewed bottle to be poured back without losing its previous progress while still permitting new reactions caused by mixing.

## Finished vanilla potion compatibility

Brewing Stand output remains compatible with vanilla Potion `ItemStack`s.

When a finished vanilla potion is poured into the Alchemy Cauldron, its PotionContents are converted into mixture effect contributions.

When a cauldron mixture is bottled:

- if it exactly maps to a canonical vanilla/custom registered potion with no unresolved chemistry, it may produce the normal potion item with standard PotionContents
- otherwise it produces an Alchemy mixture bottle carrying custom mixture data

This avoids creating an infinite registry of every possible mixed potion combination.

## Discovery / manual journal

Discovery should distinguish:

- known canonical brewing outcomes
- incomplete mixture observations
- experimentally discovered mixed outcomes

A player should not automatically unlock a canonical recipe merely by receiving a bottle from another player unless existing discovery rules intentionally permit it.

The journal may show known effects while leaving unknown ingredients / interactions obscured until discovered.

## Failure and instability

Mixing incompatible chemistry should not be forced into a valid potion. The mixture may accumulate instability and produce outcomes such as:

- potency loss
- shortened duration
- neutralization
- failed / inert mixture
- optional harmful side effects if explicitly designed

Random catastrophic behavior should be bounded and deterministic enough to test; avoid arbitrary item deletion.

## Persistence and networking

`MixtureState` must survive chunk unload, server restart, bottling, pouring and bundle migration.

Any custom bottle data must use a versioned schema so future changes can migrate existing bottles safely.

## Required GameTests before implementation is considered complete

- bottle an incomplete reaction and verify remaining progress / ingredients survive
- pour the incomplete bottle back and continue rather than restarting
- pour two different completed potions into one cauldron
- verify equal opposing effects neutralize
- verify unequal opposing effects leave the correct residual contribution
- verify unrelated effects coexist
- verify dilution changes concentration without losing total effect accounting
- verify completed and incomplete reaction components coexist after mixing
- verify cauldron and bottle state survive save/reload
- verify canonical mixtures bottle as normal PotionContents where possible
- verify non-canonical mixtures bottle as custom mixture bottles
- verify discovery records only the intended observable / completed outcomes

## Open design decisions

1. Should incomplete mixture bottles be drinkable? If yes, should unresolved chemistry apply only currently resolved effects or also carry an instability risk?
2. Should pouring a finished potion into a cauldron require a heated Alchemy Cauldron, or should any water/alchemy cauldron accept it?
3. What exact conservation model should be used for potency and duration when combining unequal volumes?
4. Should neutralized opposing effects leave an inert residue that can affect later chemistry, or disappear after cancellation?
5. Which effect opposition families are official? Do not infer opposites automatically from names.
6. Should redstone/glowstone modifiers act on the whole mixture, on compatible effect families only, or as reaction catalysts with finite capacity?
7. How many bottle-equivalent units may one cauldron hold, and how are vanilla cauldron levels mapped to those units?
8. Should a non-canonical mixed bottle use the normal potion bottle model/tint or a distinct Alchemy bottle identity/model?
