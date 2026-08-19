package dev.totem.alchemy.mixture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative liquid chemistry state shared by Alchemy Cauldrons and bottled mixtures.
 *
 * <p>Effect amount is stored as potency-ticks rather than only a duration. A level II effect therefore
 * carries twice the amount of an equal-duration level I effect. This lets volume dilution, redstone and
 * glowstone conserve effect quantity instead of creating power when liquids are mixed.</p>
 */
public final class AlchemyMixtureState {
    public static final int MAX_VOLUME_UNITS = 3;
    public static final int DEFAULT_REACTION_TICKS = 20 * 20;
    public static final int STABILITY_MAX = 100;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private int volumeUnits;
    private int stability;
    private String canonicalPotionId;
    private final Map<String, EffectDose> effects = new LinkedHashMap<>();
    private final Map<String, Reaction> reactions = new LinkedHashMap<>();
    private final Set<String> provenance = new LinkedHashSet<>();

    public AlchemyMixtureState(int volumeUnits) {
        this.volumeUnits = clampVolume(volumeUnits);
        this.stability = STABILITY_MAX;
    }

    public static AlchemyMixtureState empty() {
        return new AlchemyMixtureState(0);
    }

    public AlchemyMixtureState copy() {
        AlchemyMixtureState copy = new AlchemyMixtureState(volumeUnits);
        copy.stability = stability;
        copy.canonicalPotionId = canonicalPotionId;
        copy.effects.putAll(effects);
        copy.reactions.putAll(reactions);
        copy.provenance.addAll(provenance);
        return copy;
    }

    public int volumeUnits() {
        return volumeUnits;
    }

    public int stability() {
        return stability;
    }

    public void setStability(int stability) {
        this.stability = Math.max(0, Math.min(STABILITY_MAX, stability));
    }

    public String canonicalPotionId() {
        return canonicalPotionId;
    }

    public void setCanonicalPotionId(String canonicalPotionId) {
        this.canonicalPotionId = blankToNull(canonicalPotionId);
    }

    public Map<String, EffectDose> effects() {
        return Map.copyOf(effects);
    }

    public Collection<Reaction> reactions() {
        return List.copyOf(reactions.values());
    }

    public Set<String> provenance() {
        return Set.copyOf(provenance);
    }

    public boolean isEmpty() {
        return volumeUnits <= 0;
    }

    public boolean hasPendingReactions() {
        return !reactions.isEmpty();
    }

    public void addProvenance(String value) {
        if (value != null && !value.isBlank()) {
            provenance.add(value);
        }
    }

    public void putEffect(String effectId, double potencyTicks, int amplifierCap) {
        if (effectId == null || effectId.isBlank() || potencyTicks <= 0.0001D) {
            return;
        }
        effects.merge(effectId, new EffectDose(potencyTicks, Math.max(0, amplifierCap)), EffectDose::merge);
        neutralizeOpposites();
    }

    public void replaceEffects(Map<String, EffectDose> replacement) {
        effects.clear();
        if (replacement != null) {
            replacement.forEach((id, dose) -> {
                if (id != null && !id.isBlank() && dose != null && dose.potencyTicks() > 0.0001D) {
                    effects.put(id, dose);
                }
            });
        }
        neutralizeOpposites();
    }

    public void addReaction(Reaction reaction) {
        if (reaction == null || reaction.id().isBlank()) {
            return;
        }
        reactions.merge(reaction.id(), reaction, Reaction::mergeSameReaction);
        canonicalPotionId = null;
        stability = Math.max(0, stability - 5);
    }

    /** Tick every independent reaction. Completed reactions replace only their captured contribution. */
    public boolean tickReactions(int ticks) {
        if (ticks <= 0 || reactions.isEmpty()) {
            return false;
        }
        boolean changed = false;
        List<Reaction> completed = new ArrayList<>();
        for (Map.Entry<String, Reaction> entry : new ArrayList<>(reactions.entrySet())) {
            Reaction advanced = entry.getValue().advance(ticks);
            reactions.put(entry.getKey(), advanced);
            changed = true;
            if (advanced.complete()) {
                completed.add(advanced);
            }
        }
        for (Reaction reaction : completed) {
            applyReaction(reaction);
            reactions.remove(reaction.id());
        }
        if (!completed.isEmpty()) {
            stability = Math.min(STABILITY_MAX, stability + completed.size() * 5);
        }
        return changed;
    }

    private void applyReaction(Reaction reaction) {
        subtractEffects(reaction.sourceEffects());
        addEffects(reaction.targetEffects());
        neutralizeOpposites();
        if (reactions.size() == 1 && reaction.targetPotionId() != null && volumeUnits == reaction.volumeUnits()) {
            canonicalPotionId = reaction.targetPotionId();
        } else {
            canonicalPotionId = null;
        }
        addProvenance("reaction:" + reaction.ingredientId());
    }

    /** Redstone conserves effect amount while favouring duration. */
    public void applyRedstoneModifier() {
        if (effects.isEmpty()) {
            return;
        }
        Map<String, EffectDose> updated = new LinkedHashMap<>();
        effects.forEach((id, dose) -> updated.put(id,
                new EffectDose(dose.potencyTicks(), Math.max(0, dose.amplifierCap() - 1))));
        replaceEffects(updated);
        canonicalPotionId = null;
        stability = Math.max(0, stability - 3);
        addProvenance("modifier:minecraft:redstone");
    }

    /** Glowstone conserves effect amount while favouring potency over duration. */
    public void applyGlowstoneModifier() {
        if (effects.isEmpty()) {
            return;
        }
        Map<String, EffectDose> updated = new LinkedHashMap<>();
        effects.forEach((id, dose) -> updated.put(id,
                new EffectDose(dose.potencyTicks(), Math.min(4, dose.amplifierCap() + 1))));
        replaceEffects(updated);
        canonicalPotionId = null;
        stability = Math.max(0, stability - 6);
        addProvenance("modifier:minecraft:glowstone_dust");
    }

    /** Merge another liquid into this state. Volume and all captured effect quantities are conserved. */
    public boolean mergeFrom(AlchemyMixtureState other) {
        if (other == null || other.isEmpty() || volumeUnits + other.volumeUnits > MAX_VOLUME_UNITS) {
            return false;
        }
        int oldVolume = volumeUnits;
        int incomingVolume = other.volumeUnits;
        int mergedVolume = oldVolume + incomingVolume;
        addEffects(other.effects);
        mergeReactions(other, oldVolume, incomingVolume);
        provenance.addAll(other.provenance);
        stability = mergedVolume == 0 ? STABILITY_MAX
                : Math.max(0, Math.min(STABILITY_MAX,
                (stability * oldVolume + other.stability * incomingVolume) / mergedVolume - 2));
        if (canonicalPotionId == null || other.canonicalPotionId == null
                || !canonicalPotionId.equals(other.canonicalPotionId)) {
            canonicalPotionId = null;
        }
        volumeUnits = mergedVolume;
        neutralizeOpposites();
        return true;
    }

    private void mergeReactions(AlchemyMixtureState other, int oldVolume, int incomingVolume) {
        for (Reaction incoming : other.reactions.values()) {
            Reaction existing = reactions.get(incoming.id());
            if (existing == null) {
                reactions.put(incoming.id(), incoming);
            } else {
                reactions.put(incoming.id(), existing.mergeWeighted(incoming, oldVolume, incomingVolume));
            }
        }
    }

    /**
     * Remove one bottle unit. The returned bottle carries a proportional effect/reaction contribution and
     * exactly the same reaction percentage as the cauldron liquid at extraction time.
     */
    public AlchemyMixtureState extractBottle() {
        if (volumeUnits <= 0) {
            return empty();
        }
        int originalVolume = volumeUnits;
        double fraction = 1.0D / originalVolume;
        AlchemyMixtureState bottle = scaledCopy(fraction, 1);

        if (originalVolume == 1) {
            volumeUnits = 0;
            effects.clear();
            reactions.clear();
            provenance.clear();
            canonicalPotionId = null;
            stability = STABILITY_MAX;
            return bottle;
        }

        scaleInPlace((originalVolume - 1.0D) / originalVolume);
        volumeUnits = originalVolume - 1;
        return bottle;
    }

    private AlchemyMixtureState scaledCopy(double factor, int newVolume) {
        AlchemyMixtureState result = new AlchemyMixtureState(newVolume);
        result.stability = stability;
        result.canonicalPotionId = canonicalPotionId;
        effects.forEach((id, dose) -> result.effects.put(id, dose.scale(factor)));
        reactions.forEach((id, reaction) -> result.reactions.put(id, reaction.scale(factor, newVolume)));
        result.provenance.addAll(provenance);
        return result;
    }

    private void scaleInPlace(double factor) {
        effects.replaceAll((id, dose) -> dose.scale(factor));
        reactions.replaceAll((id, reaction) -> reaction.scale(factor, Math.max(1, volumeUnits - 1)));
    }

    private void addEffects(Map<String, EffectDose> additions) {
        additions.forEach((id, dose) -> effects.merge(id, dose, EffectDose::merge));
    }

    private void subtractEffects(Map<String, EffectDose> removals) {
        removals.forEach((id, dose) -> {
            EffectDose current = effects.get(id);
            if (current == null) {
                return;
            }
            double left = current.potencyTicks() - dose.potencyTicks();
            if (left <= 0.0001D) {
                effects.remove(id);
            } else {
                effects.put(id, new EffectDose(left, current.amplifierCap()));
            }
        });
    }

    private void neutralizeOpposites() {
        neutralizePair("minecraft:speed", "minecraft:slowness");
        neutralizePair("minecraft:instant_health", "minecraft:instant_damage");
        neutralizePowerFamily();
        effects.entrySet().removeIf(entry -> entry.getValue().potencyTicks() <= 0.0001D);
    }

    private void neutralizePair(String positiveId, String negativeId) {
        EffectDose positive = effects.get(positiveId);
        EffectDose negative = effects.get(negativeId);
        if (positive == null || negative == null) {
            return;
        }
        double delta = positive.potencyTicks() - negative.potencyTicks();
        effects.remove(positiveId);
        effects.remove(negativeId);
        if (delta > 0.0001D) {
            effects.put(positiveId, new EffectDose(delta, positive.amplifierCap()));
        } else if (delta < -0.0001D) {
            effects.put(negativeId, new EffectDose(-delta, negative.amplifierCap()));
        }
    }

    private void neutralizePowerFamily() {
        EffectDose weakness = effects.get("minecraft:weakness");
        if (weakness == null) {
            return;
        }
        List<String> positives = List.of("minecraft:strength", "deadrecall:firefly_strength");
        double positiveTotal = positives.stream()
                .map(effects::get)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(EffectDose::potencyTicks)
                .sum();
        if (positiveTotal <= 0.0001D) {
            return;
        }
        double negative = weakness.potencyTicks();
        effects.remove("minecraft:weakness");
        if (negative >= positiveTotal) {
            positives.forEach(effects::remove);
            double left = negative - positiveTotal;
            if (left > 0.0001D) {
                effects.put("minecraft:weakness", new EffectDose(left, weakness.amplifierCap()));
            }
            return;
        }
        double keepRatio = (positiveTotal - negative) / positiveTotal;
        for (String id : positives) {
            EffectDose dose = effects.get(id);
            if (dose != null) {
                effects.put(id, dose.scale(keepRatio));
            }
        }
    }

    /** Stable deterministic text representation used by block entities and CUSTOM_DATA. */
    public String encode() {
        StringBuilder out = new StringBuilder();
        out.append("V|").append(volumeUnits).append('\n');
        out.append("S|").append(stability).append('\n');
        if (canonicalPotionId != null) {
            out.append("C|").append(enc(canonicalPotionId)).append('\n');
        }
        effects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                out.append("E|").append(enc(entry.getKey())).append('|')
                        .append(entry.getValue().potencyTicks()).append('|')
                        .append(entry.getValue().amplifierCap()).append('\n'));
        reactions.values().stream().sorted(Comparator.comparing(Reaction::id)).forEach(reaction ->
                out.append("R|").append(enc(reaction.id())).append('|')
                        .append(enc(reaction.ingredientId())).append('|')
                        .append(reaction.elapsedTicks()).append('|').append(reaction.requiredTicks()).append('|')
                        .append(reaction.volumeUnits()).append('|')
                        .append(enc(nullToBlank(reaction.sourcePotionId()))).append('|')
                        .append(enc(nullToBlank(reaction.targetPotionId()))).append('|')
                        .append(enc(encodeEffects(reaction.sourceEffects()))).append('|')
                        .append(enc(encodeEffects(reaction.targetEffects()))).append('\n'));
        provenance.stream().sorted().forEach(value -> out.append("P|").append(enc(value)).append('\n'));
        return out.toString();
    }

    public static AlchemyMixtureState decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }
        AlchemyMixtureState state = empty();
        for (String line : encoded.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] part = line.split("\\|", -1);
            try {
                switch (part[0]) {
                    case "V" -> state.volumeUnits = clampVolume(Integer.parseInt(part[1]));
                    case "S" -> state.stability = Math.max(0, Math.min(STABILITY_MAX, Integer.parseInt(part[1])));
                    case "C" -> state.canonicalPotionId = blankToNull(dec(part[1]));
                    case "E" -> state.effects.put(dec(part[1]),
                            new EffectDose(Double.parseDouble(part[2]), Integer.parseInt(part[3])));
                    case "R" -> state.reactions.put(dec(part[1]), new Reaction(
                            dec(part[1]), dec(part[2]), Integer.parseInt(part[3]), Integer.parseInt(part[4]),
                            Integer.parseInt(part[5]), blankToNull(dec(part[6])), blankToNull(dec(part[7])),
                            decodeEffects(dec(part[8])), decodeEffects(dec(part[9]))));
                    case "P" -> state.provenance.add(dec(part[1]));
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // Corrupt individual entries are ignored so one bad field cannot brick a world or item stack.
            }
        }
        state.neutralizeOpposites();
        return state;
    }

    private static String encodeEffects(Map<String, EffectDose> values) {
        StringBuilder out = new StringBuilder();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!out.isEmpty()) {
                out.append(';');
            }
            out.append(enc(entry.getKey())).append(',')
                    .append(entry.getValue().potencyTicks()).append(',')
                    .append(entry.getValue().amplifierCap());
        });
        return out.toString();
    }

    private static Map<String, EffectDose> decodeEffects(String raw) {
        Map<String, EffectDose> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String value : raw.split(";")) {
            String[] part = value.split(",", -1);
            if (part.length != 3) {
                continue;
            }
            try {
                result.put(dec(part[0]), new EffectDose(Double.parseDouble(part[1]), Integer.parseInt(part[2])));
            } catch (RuntimeException ignored) {
                // Ignore corrupt effect rows.
            }
        }
        return result;
    }

    private static String enc(String value) {
        return B64.encodeToString(nullToBlank(value).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value) {
        return new String(B64D.decode(value), StandardCharsets.UTF_8);
    }

    private static int clampVolume(int volume) {
        return Math.max(0, Math.min(MAX_VOLUME_UNITS, volume));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record EffectDose(double potencyTicks, int amplifierCap) {
        public EffectDose {
            potencyTicks = Math.max(0.0D, potencyTicks);
            amplifierCap = Math.max(0, amplifierCap);
        }

        public static EffectDose fromDuration(int durationTicks, int amplifier) {
            return new EffectDose((double) Math.max(0, durationTicks) * (Math.max(0, amplifier) + 1), amplifier);
        }

        public EffectDose merge(EffectDose other) {
            return new EffectDose(potencyTicks + other.potencyTicks, Math.max(amplifierCap, other.amplifierCap));
        }

        public EffectDose scale(double factor) {
            return new EffectDose(potencyTicks * Math.max(0.0D, factor), amplifierCap);
        }

        public int durationForVolume(int volume) {
            int safeVolume = Math.max(1, volume);
            return Math.max(1, (int) Math.round(potencyTicks / safeVolume / (amplifierCap + 1.0D)));
        }
    }

    public record Reaction(
            String id,
            String ingredientId,
            int elapsedTicks,
            int requiredTicks,
            int volumeUnits,
            String sourcePotionId,
            String targetPotionId,
            Map<String, EffectDose> sourceEffects,
            Map<String, EffectDose> targetEffects
    ) {
        public Reaction {
            id = nullToBlank(id);
            ingredientId = nullToBlank(ingredientId);
            elapsedTicks = Math.max(0, elapsedTicks);
            requiredTicks = Math.max(1, requiredTicks);
            volumeUnits = Math.max(1, Math.min(MAX_VOLUME_UNITS, volumeUnits));
            sourceEffects = Map.copyOf(sourceEffects == null ? Map.of() : sourceEffects);
            targetEffects = Map.copyOf(targetEffects == null ? Map.of() : targetEffects);
        }

        public boolean complete() {
            return elapsedTicks >= requiredTicks;
        }

        public int remainingTicks() {
            return Math.max(0, requiredTicks - elapsedTicks);
        }

        public double progress() {
            return Math.min(1.0D, (double) elapsedTicks / requiredTicks);
        }

        public Reaction advance(int ticks) {
            return new Reaction(id, ingredientId, Math.min(requiredTicks, elapsedTicks + Math.max(0, ticks)),
                    requiredTicks, volumeUnits, sourcePotionId, targetPotionId, sourceEffects, targetEffects);
        }

        public Reaction scale(double factor, int newVolume) {
            Map<String, EffectDose> source = new LinkedHashMap<>();
            sourceEffects.forEach((id, dose) -> source.put(id, dose.scale(factor)));
            Map<String, EffectDose> target = new LinkedHashMap<>();
            targetEffects.forEach((id, dose) -> target.put(id, dose.scale(factor)));
            return new Reaction(id, ingredientId, elapsedTicks, requiredTicks, newVolume,
                    sourcePotionId, targetPotionId, source, target);
        }

        private Reaction mergeWeighted(Reaction other, int currentWeight, int otherWeight) {
            int total = Math.max(1, currentWeight + otherWeight);
            int elapsed = (elapsedTicks * currentWeight + other.elapsedTicks * otherWeight) / total;
            Map<String, EffectDose> source = mergeMaps(sourceEffects, other.sourceEffects);
            Map<String, EffectDose> target = mergeMaps(targetEffects, other.targetEffects);
            String sourcePotion = java.util.Objects.equals(sourcePotionId, other.sourcePotionId) ? sourcePotionId : null;
            String targetPotion = java.util.Objects.equals(targetPotionId, other.targetPotionId) ? targetPotionId : null;
            return new Reaction(id, ingredientId, elapsed, Math.max(requiredTicks, other.requiredTicks),
                    Math.min(MAX_VOLUME_UNITS, volumeUnits + other.volumeUnits), sourcePotion, targetPotion, source, target);
        }

        private static Reaction mergeSameReaction(Reaction left, Reaction right) {
            return left.mergeWeighted(right, left.volumeUnits, right.volumeUnits);
        }

        private static Map<String, EffectDose> mergeMaps(Map<String, EffectDose> first, Map<String, EffectDose> second) {
            Map<String, EffectDose> result = new LinkedHashMap<>(first);
            second.forEach((id, dose) -> result.merge(id, dose, EffectDose::merge));
            return result;
        }
    }
}
