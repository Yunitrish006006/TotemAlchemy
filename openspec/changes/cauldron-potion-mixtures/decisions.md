# Confirmed design decisions

The project owner approved the complete proposal in `design.md` on 2026-08-19.

Implementation decisions:

- Incomplete mixtures are drinkable. Only effects that have already formed are applied; unfinished chemistry remains metadata and an unstable mixture may carry a mild negative instability effect.
- Opposed effects neutralize directly. Version 1 does not create hidden neutral residue chemistry.
- Redstone is a duration-oriented mixture modifier; glowstone is a potency/concentration-oriented modifier. They only transform compatible effects.
- A full cauldron remains three bottle units for the initial implementation.
- Vanilla potion bottles can be poured into an Alchemy Cauldron and become mixture contributions.
- Bottling a mixture preserves unfinished ingredients, independent reaction progress, remaining time, effect contributions, concentration, stability and provenance.
- Pouring a stored mixture back into a cauldron restores its progress rather than restarting it.
- Mixing is conservative: volume and effect quantity are conserved, opposing effect families neutralize, then the result is diluted by total volume.
- Canonical results may use normal Minecraft potion identity where practical; non-canonical or unfinished results remain potion ItemStacks carrying Alchemy mixture metadata.
- Brewing Stand chemistry remains the fast/risky route: it keeps the existing ingredient success chances and multi-outcome rolls.
- Alchemy Cauldron chemistry is the slow/controllable route: reactions require heat and time, may be interrupted/bottled/resumed, and do not roll a separate hidden failure chance after the player has committed a long reaction. Multi-outcome ingredients select one shared independently rolled outcome set when the reaction is scheduled, but unfinished bottle metadata does not reveal that hidden set before completion.
