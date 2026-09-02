package dev.totem.alchemy.mixture;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import net.minecraft.util.RandomSource;

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
 * Server-authoritative liquid chemistry state shared by Alchemy Cauldrons and portable containers.
 *
 * <p>Effect amount is stored as potency-ticks rather than only a duration. A level II effect therefore
 * carries twice the amount of an equal-duration level I effect. This lets volume dilution, modifiers and
 * multi-effect brewing conserve effect quantity instead of creating power when liquids are mixed.</p>
 */
public final class AlchemyMixtureState {
    public static final int MAX_VOLUME_UNITS = 3;
    public static final int DEFAULT_REACTION_TICKS = 20 * 20;
    public static final int MIN_PERFECT_WINDOW_TICKS = 20 * 5;
    public static final int MAX_PERFECT_WINDOW_TICKS = 20 * 15;
    public static final int STABILITY_MAX = 100;
    private static final String PRESERVE_INDEPENDENT_OUTCOMES = "state:independent_outcome_set";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private int volumeUnits;
    private int stability;
    private int overcookTicks;
    private int perfectWindowTicks = perfectWindowTicksForProcessing(DEFAULT_REACTION_TICKS);
    private boolean baseActivated;
    private boolean heatLockedAfterBottling;
    private DeliveryForm deliveryForm = DeliveryForm.DRINKABLE;
    private String canonicalPotionId;
    private final Map<String, EffectDose> effects = new LinkedHashMap<>();
    private final Map<String, Reaction> reactions = new LinkedHashMap<>();
    private final Map<String, CompletedStage> completedStages = new LinkedHashMap<>();
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
        copy.overcookTicks = overcookTicks;
        copy.perfectWindowTicks = perfectWindowTicks;
        copy.baseActivated = baseActivated;
        copy.heatLockedAfterBottling = heatLockedAfterBottling;
        copy.deliveryForm = deliveryForm;
        copy.canonicalPotionId = canonicalPotionId;
        copy.effects.putAll(effects);
        copy.reactions.putAll(reactions);
        copy.completedStages.putAll(completedStages);
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

    public int overcookTicks() {
        return overcookTicks;
    }

    public int perfectWindowTicks() {
        return perfectWindowTicks;
    }

    /**
     * Gives every completed reaction a practical extraction window. Faster stages always receive at least five
     * seconds, while very long data-pack stages are capped at fifteen seconds so timing still matters.
     */
    public static int perfectWindowTicksForProcessing(int processingTicks) {
        int proportionalWindow = Math.max(1, processingTicks) / 4;
        return Math.max(MIN_PERFECT_WINDOW_TICKS, Math.min(MAX_PERFECT_WINDOW_TICKS, proportionalWindow));
    }

    public boolean baseActivated() {
        return baseActivated;
    }

    public void setBaseActivated(boolean baseActivated) {
        this.baseActivated = baseActivated;
    }

    public boolean isHeatLockedAfterBottling() {
        return heatLockedAfterBottling;
    }

    /** A finished portable potion is stable when returned to heat until another ingredient starts a reaction. */
    public void lockHeatIfFinished() {
        if (!isEmpty() && !hasPendingReactions()) {
            heatLockedAfterBottling = true;
        }
    }

    public DeliveryForm deliveryForm() {
        return deliveryForm;
    }

    public void setDeliveryForm(DeliveryForm deliveryForm) {
        this.deliveryForm = deliveryForm == null ? DeliveryForm.DRINKABLE : deliveryForm;
        canonicalPotionId = null;
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

    public Collection<CompletedStage> completedStages() {
        return List.copyOf(completedStages.values());
    }

    public Set<String> provenance() {
        return Set.copyOf(provenance);
    }

    public boolean hasProvenance(String value) {
        return value != null && provenance.contains(value);
    }

    public boolean preservesIndependentOutcomes() {
        return provenance.contains(PRESERVE_INDEPENDENT_OUTCOMES);
    }

    public boolean isEmpty() {
        return volumeUnits <= 0;
    }

    public boolean hasPendingReactions() {
        return !reactions.isEmpty();
    }

    public boolean hasCompletedStages() {
        return !completedStages.isEmpty();
    }

    public boolean hasPendingReactionForIngredient(String ingredientId) {
        return ingredientId != null && reactions.values().stream()
                .anyMatch(reaction -> ingredientId.equals(reaction.ingredientId()));
    }

    public boolean canOvercook() {
        return !heatLockedAfterBottling && !isEmpty() && !hasPendingReactions() && !hasCompletedStages()
                && (baseActivated || !effects.isEmpty());
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
        provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
        effects.merge(effectId, new EffectDose(potencyTicks, Math.max(0, amplifierCap)), EffectDose::merge);
        neutralizeOpposites();
    }

    public void addEffects(Map<String, EffectDose> additions) {
        if (additions == null) {
            return;
        }
        provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
        additions.forEach((id, dose) -> {
            if (id != null && !id.isBlank() && dose != null && dose.potencyTicks() > 0.0001D) {
                effects.merge(id, dose, EffectDose::merge);
            }
        });
        neutralizeOpposites();
    }

    /** Adds one independently rolled outcome set, then resolves chemically opposing effects. */
    public void addIndependentOutcomeEffects(Map<String, EffectDose> additions) {
        addEffects(additions);
    }

    public void replaceEffects(Map<String, EffectDose> replacement) {
        effects.clear();
        provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
        if (replacement != null) {
            replacement.forEach((id, dose) -> {
                if (id != null && !id.isBlank() && dose != null && dose.potencyTicks() > 0.0001D) {
                    effects.put(id, dose);
                }
            });
        }
        neutralizeOpposites();
    }

    private void replaceIndependentOutcomeEffects(Map<String, EffectDose> replacement) {
        effects.clear();
        addIndependentOutcomeEffects(replacement);
    }

    public void addReaction(Reaction reaction) {
        if (reaction == null || reaction.id().isBlank()) {
            return;
        }
        if (!reactions.isEmpty()) {
            for (Reaction existing : reactions.values()) {
                provenance.add("concurrent:" + existing.id());
            }
            provenance.add("concurrent:" + reaction.id());
        }
        reactions.merge(reaction.id(), reaction, Reaction::mergeSameReaction);
        heatLockedAfterBottling = false;
        completedStages.remove(reaction.id());
        overcookTicks = 0;
        perfectWindowTicks = 0;
        if (stability > 0) {
            stability = Math.max(0, stability - 5);
        }
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
            completedStages.put(reaction.id(), new CompletedStage(
                    reaction.id(),
                    reaction.ingredientId(),
                    0,
                    perfectWindowTicksForProcessing(reaction.requiredTicks())
            ));
        }
        if (!completed.isEmpty() && stability > 0) {
            stability = Math.min(STABILITY_MAX, stability + completed.size() * 5);
        }
        return changed;
    }

    /** Advance every already-finished material stage independently while later materials continue reacting. */
    public boolean tickCompletedStages(RandomSource random, int ticks) {
        if (heatLockedAfterBottling || ticks <= 0 || completedStages.isEmpty()) {
            return false;
        }

        boolean timingChanged = false;
        int totalDecay = 0;
        for (Map.Entry<String, CompletedStage> entry : new ArrayList<>(completedStages.entrySet())) {
            CompletedStage stage = entry.getValue();
            CompletedStage advanced = stage.advance(ticks);
            completedStages.put(entry.getKey(), advanced);

            int oldElapsedSecond = stage.overcookTicks() / 20;
            int newElapsedSecond = advanced.overcookTicks() / 20;
            boolean crossedPerfectWindow = stage.overcookTicks() <= stage.perfectWindowTicks()
                    && advanced.overcookTicks() > advanced.perfectWindowTicks();
            timingChanged |= newElapsedSecond > oldElapsedSecond || crossedPerfectWindow;
            totalDecay += Math.max(0, advanced.damagingTicks() / 20 - stage.damagingTicks() / 20);
        }

        boolean stabilityChanged = damageStability(random, totalDecay);
        return timingChanged || stabilityChanged;
    }

    private void applyReaction(Reaction reaction) {
        boolean independentOutcomeSet = reaction.id().startsWith("brewset:");
        subtractEffects(reaction.sourceEffects());
        if (independentOutcomeSet) {
            addIndependentOutcomeEffects(reaction.targetEffects());
        } else {
            addEffects(reaction.targetEffects());
        }

        if (BrewingMaterialSettings.isStarter(reaction.ingredientId())) {
            baseActivated = true;
        }
        if ("minecraft:gunpowder".equals(reaction.ingredientId())) {
            deliveryForm = DeliveryForm.SPLASH;
        } else if ("minecraft:dragon_breath".equals(reaction.ingredientId())) {
            deliveryForm = DeliveryForm.LINGERING;
        }

        if (reactions.size() == 1
                && !hasProvenance("concurrent:" + reaction.id())
                && reaction.targetPotionId() != null
                && volumeUnits == reaction.volumeUnits()) {
            canonicalPotionId = reaction.targetPotionId();
        } else {
            canonicalPotionId = null;
        }
        overcookTicks = 0;
        perfectWindowTicks = Math.max(perfectWindowTicks,
                perfectWindowTicksForProcessing(reaction.requiredTicks()));
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
        if (preservesIndependentOutcomes()) {
            replaceIndependentOutcomeEffects(updated);
        } else {
            replaceEffects(updated);
        }
        canonicalPotionId = null;
        if (stability > 0) {
            stability = Math.max(0, stability - 3);
        }
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
        if (preservesIndependentOutcomes()) {
            replaceIndependentOutcomeEffects(updated);
        } else {
            replaceEffects(updated);
        }
        canonicalPotionId = null;
        if (stability > 0) {
            stability = Math.max(0, stability - 6);
        }
        addProvenance("modifier:minecraft:glowstone_dust");
    }

    /**
     * Continued heating after the configured reaction time slowly damages stability. Each mutation threshold
     * is applied at most once and is persisted through provenance markers.
     */
    public boolean tickOvercook(RandomSource random, int ticks) {
        if (ticks <= 0 || !canOvercook()) {
            return false;
        }
        if (stability <= 0 && hasProvenance("mutation:0")) {
            return false;
        }

        int oldElapsedSecond = overcookTicks / 20;
        int oldDamageTicks = Math.max(0, overcookTicks - perfectWindowTicks);
        boolean wasInPerfectWindow = overcookTicks <= perfectWindowTicks;
        overcookTicks += ticks;
        int newElapsedSecond = overcookTicks / 20;
        int newDamageTicks = Math.max(0, overcookTicks - perfectWindowTicks);
        int decay = Math.max(0, newDamageTicks / 20 - oldDamageTicks / 20);
        boolean crossedPerfectWindow = wasInPerfectWindow && overcookTicks > perfectWindowTicks;
        boolean elapsedSecondChanged = newElapsedSecond > oldElapsedSecond;

        boolean stabilityChanged = damageStability(random, decay);
        return elapsedSecondChanged || crossedPerfectWindow || stabilityChanged;
    }

    private boolean damageStability(RandomSource random, int decay) {
        if (decay <= 0) {
            return false;
        }

        int before = stability;
        stability = Math.max(0, stability - decay);
        boolean mutated = false;
        if (stability <= 35 && !hasProvenance("mutation:35")) {
            mutateMild(random);
            provenance.add("mutation:35");
            mutated = true;
        }
        if (stability <= 15 && !hasProvenance("mutation:15")) {
            mutateSevere(random);
            provenance.add("mutation:15");
            mutated = true;
        }
        if (stability <= 0 && !hasProvenance("mutation:0")) {
            mutateCollapse(random);
            provenance.add("mutation:0");
            mutated = true;
        }
        if (mutated) {
            canonicalPotionId = null;
        }
        return before != stability || mutated;
    }

    private void mutateMild(RandomSource random) {
        if (!invertRandomEffect(random)) {
            removeRandomEffect(random);
        }
    }

    private void mutateSevere(RandomSource random) {
        if (random.nextFloat() < 0.65F) {
            addPoisonMutation(0.75D);
        } else if (!invertRandomEffect(random)) {
            removeRandomEffect(random);
        }
    }

    private void mutateCollapse(RandomSource random) {
        double dose = referenceDose();
        if (!effects.isEmpty() && random.nextBoolean()) {
            removeRandomEffect(random);
        } else {
            effects.clear();
        }
        putEffect("minecraft:poison", Math.max(dose, 20.0D * 30.0D * Math.max(1, volumeUnits)), 0);
    }

    private void addPoisonMutation(double factor) {
        putEffect("minecraft:poison",
                Math.max(20.0D * 15.0D * Math.max(1, volumeUnits), referenceDose() * factor), 0);
    }

    private boolean invertRandomEffect(RandomSource random) {
        List<String> candidates = effects.keySet().stream().filter(id -> oppositeOf(id) != null).toList();
        if (candidates.isEmpty()) {
            return false;
        }
        String source = candidates.get(random.nextInt(candidates.size()));
        EffectDose dose = effects.remove(source);
        String opposite = oppositeOf(source);
        if (dose != null && opposite != null) {
            effects.merge(opposite, dose, EffectDose::merge);
            neutralizeOpposites();
            return true;
        }
        return false;
    }

    private boolean removeRandomEffect(RandomSource random) {
        if (effects.isEmpty()) {
            return false;
        }
        List<String> ids = List.copyOf(effects.keySet());
        effects.remove(ids.get(random.nextInt(ids.size())));
        return true;
    }

    private static String oppositeOf(String id) {
        return switch (id) {
            case "minecraft:speed" -> "minecraft:slowness";
            case "minecraft:slowness" -> "minecraft:speed";
            case "minecraft:instant_health" -> "minecraft:instant_damage";
            case "minecraft:instant_damage" -> "minecraft:instant_health";
            case "minecraft:strength", "deadrecall:firefly_strength", "totem:alchemy/firefly_strength" -> "minecraft:weakness";
            case "minecraft:weakness" -> "minecraft:strength";
            case "minecraft:regeneration" -> "minecraft:poison";
            case "minecraft:poison" -> "minecraft:regeneration";
            default -> null;
        };
    }

    private double referenceDose() {
        return effects.values().stream().mapToDouble(EffectDose::potencyTicks).average()
                .orElse(20.0D * 30.0D * Math.max(1, volumeUnits));
    }

    /** Merge another liquid into this state. Volume and all captured effect quantities are conserved. */
    public boolean mergeFrom(AlchemyMixtureState other) {
        if (other == null || other.isEmpty() || volumeUnits + other.volumeUnits > MAX_VOLUME_UNITS) {
            return false;
        }
        boolean activeHeat = canAdvanceUnderHeat() || other.canAdvanceUnderHeat();
        int oldVolume = volumeUnits;
        int incomingVolume = other.volumeUnits;
        int mergedVolume = oldVolume + incomingVolume;
        boolean preserveOutcomeSet = other.preservesIndependentOutcomes()
                && (oldVolume == 0 || preservesIndependentOutcomes() && effects.keySet().equals(other.effects.keySet()));
        if (preserveOutcomeSet) {
            addIndependentOutcomeEffects(other.effects);
        } else {
            addEffects(other.effects);
        }
        mergeReactions(other, oldVolume, incomingVolume);
        other.completedStages.forEach((id, stage) ->
                completedStages.merge(id, stage, CompletedStage::mergeSameStage));
        provenance.addAll(other.provenance);
        if (!preserveOutcomeSet) {
            provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
        }
        baseActivated = baseActivated || other.baseActivated;
        heatLockedAfterBottling = !activeHeat
                && (heatLockedAfterBottling || other.heatLockedAfterBottling);
        deliveryForm = deliveryForm == other.deliveryForm ? deliveryForm : DeliveryForm.DRINKABLE;
        overcookTicks = 0;
        perfectWindowTicks = Math.max(perfectWindowTicks, other.perfectWindowTicks);
        stability = mergedVolume == 0 ? STABILITY_MAX
                : Math.max(0, Math.min(STABILITY_MAX,
                (stability * oldVolume + other.stability * incomingVolume) / mergedVolume - 2));
        if (canonicalPotionId == null || other.canonicalPotionId == null
                || !canonicalPotionId.equals(other.canonicalPotionId)) {
            canonicalPotionId = null;
        }
        volumeUnits = mergedVolume;
        if (!preserveOutcomeSet) {
            neutralizeOpposites();
        }
        return true;
    }

    private boolean canAdvanceUnderHeat() {
        return !heatLockedAfterBottling
                && (hasPendingReactions() || hasCompletedStages() || canOvercook());
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

    public AlchemyMixtureState extractBottle() {
        return extractUnits(1);
    }

    /** Remove up to the requested number of bottle-volume units without changing concentration. */
    public AlchemyMixtureState extractUnits(int requestedUnits) {
        if (volumeUnits <= 0 || requestedUnits <= 0) {
            return empty();
        }
        int originalVolume = volumeUnits;
        int extractedVolume = Math.min(originalVolume, requestedUnits);
        double fraction = extractedVolume / (double) originalVolume;
        AlchemyMixtureState extracted = scaledCopy(fraction, extractedVolume);

        if (extractedVolume == originalVolume) {
            resetEmpty();
            return extracted;
        }

        int remainingVolume = originalVolume - extractedVolume;
        scaleInPlace(remainingVolume / (double) originalVolume, remainingVolume);
        volumeUnits = remainingVolume;
        return extracted;
    }

    private void resetEmpty() {
        volumeUnits = 0;
        effects.clear();
        reactions.clear();
        completedStages.clear();
        provenance.clear();
        canonicalPotionId = null;
        stability = STABILITY_MAX;
        overcookTicks = 0;
        perfectWindowTicks = perfectWindowTicksForProcessing(DEFAULT_REACTION_TICKS);
        baseActivated = false;
        heatLockedAfterBottling = false;
        deliveryForm = DeliveryForm.DRINKABLE;
    }

    private AlchemyMixtureState scaledCopy(double factor, int newVolume) {
        AlchemyMixtureState result = new AlchemyMixtureState(newVolume);
        result.stability = stability;
        result.overcookTicks = overcookTicks;
        result.perfectWindowTicks = perfectWindowTicks;
        result.baseActivated = baseActivated;
        result.heatLockedAfterBottling = heatLockedAfterBottling;
        result.deliveryForm = deliveryForm;
        result.canonicalPotionId = canonicalPotionId;
        effects.forEach((id, dose) -> result.effects.put(id, dose.scale(factor)));
        reactions.forEach((id, reaction) -> result.reactions.put(id, reaction.scale(factor, newVolume)));
        result.completedStages.putAll(completedStages);
        result.provenance.addAll(provenance);
        return result;
    }

    private void scaleInPlace(double factor, int newVolume) {
        effects.replaceAll((id, dose) -> dose.scale(factor));
        reactions.replaceAll((id, reaction) -> reaction.scale(factor, Math.max(1, newVolume)));
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
        neutralizePair("minecraft:regeneration", "minecraft:poison");
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
        List<String> positives = List.of("minecraft:strength", "deadrecall:firefly_strength", "totem:alchemy/firefly_strength");
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
        out.append("B|").append(baseActivated ? 1 : 0).append('\n');
        out.append("H|").append(heatLockedAfterBottling ? 1 : 0).append('\n');
        out.append("F|").append(deliveryForm.name()).append('\n');
        out.append("O|").append(overcookTicks).append('\n');
        out.append("W|").append(perfectWindowTicks).append('\n');
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
        completedStages.values().stream().sorted(Comparator.comparing(CompletedStage::id)).forEach(stage ->
                out.append("T|").append(enc(stage.id())).append('|')
                        .append(enc(stage.ingredientId())).append('|')
                        .append(stage.overcookTicks()).append('|')
                        .append(stage.perfectWindowTicks()).append('\n'));
        provenance.stream().sorted().forEach(value -> out.append("P|").append(enc(value)).append('\n'));
        return out.toString();
    }

    public static AlchemyMixtureState decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }
        AlchemyMixtureState state = empty();
        boolean sawBaseMarker = false;
        for (String line : encoded.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] part = line.split("\\|", -1);
            try {
                switch (part[0]) {
                    case "V" -> state.volumeUnits = clampVolume(Integer.parseInt(part[1]));
                    case "S" -> state.stability = Math.max(0, Math.min(STABILITY_MAX, Integer.parseInt(part[1])));
                    case "B" -> {
                        state.baseActivated = Integer.parseInt(part[1]) != 0;
                        sawBaseMarker = true;
                    }
                    case "H" -> state.heatLockedAfterBottling = Integer.parseInt(part[1]) != 0;
                    case "F" -> state.deliveryForm = DeliveryForm.parse(part[1]);
                    case "O" -> state.overcookTicks = Math.max(0, Integer.parseInt(part[1]));
                    case "W" -> state.perfectWindowTicks = Math.max(0, Integer.parseInt(part[1]));
                    case "C" -> state.canonicalPotionId = blankToNull(dec(part[1]));
                    case "E" -> state.effects.put(dec(part[1]),
                            new EffectDose(Double.parseDouble(part[2]), Integer.parseInt(part[3])));
                    case "R" -> state.reactions.put(dec(part[1]), new Reaction(
                            dec(part[1]), dec(part[2]), Integer.parseInt(part[3]), Integer.parseInt(part[4]),
                            Integer.parseInt(part[5]), blankToNull(dec(part[6])), blankToNull(dec(part[7])),
                            decodeEffects(dec(part[8])), decodeEffects(dec(part[9]))));
                    case "T" -> state.completedStages.put(dec(part[1]), new CompletedStage(
                            dec(part[1]), dec(part[2]), Integer.parseInt(part[3]), Integer.parseInt(part[4])));
                    case "P" -> state.provenance.add(dec(part[1]));
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // Corrupt individual entries are ignored so one bad field cannot brick a world or item stack.
            }
        }
        if (!sawBaseMarker) {
            state.baseActivated = inferLegacyBase(state);
        }
        // Migrate mixtures created by builds that intentionally preserved opposing rolled outcomes.
    state.provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
    state.neutralizeOpposites();
        return state;
    }

    private static boolean inferLegacyBase(AlchemyMixtureState state) {
        if (!state.effects.isEmpty()) {
            return true;
        }
        String id = state.canonicalPotionId;
        return id != null
                && !"minecraft:water".equals(id)
                && !"minecraft:mundane".equals(id)
                && !"minecraft:thick".equals(id);
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

    public enum DeliveryForm {
        DRINKABLE,
        SPLASH,
        LINGERING;

        public static DeliveryForm parse(String value) {
            if (value == null || value.isBlank()) {
                return DRINKABLE;
            }
            try {
                return DeliveryForm.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return DRINKABLE;
            }
        }
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

    public record CompletedStage(
            String id,
            String ingredientId,
            int overcookTicks,
            int perfectWindowTicks
    ) {
        public CompletedStage {
            id = nullToBlank(id);
            ingredientId = nullToBlank(ingredientId);
            overcookTicks = Math.max(0, overcookTicks);
            perfectWindowTicks = Math.max(0, perfectWindowTicks);
        }

        public int damagingTicks() {
            return Math.max(0, overcookTicks - perfectWindowTicks);
        }

        public CompletedStage advance(int ticks) {
            return new CompletedStage(id, ingredientId, overcookTicks + Math.max(0, ticks), perfectWindowTicks);
        }

        private static CompletedStage mergeSameStage(CompletedStage left, CompletedStage right) {
            return new CompletedStage(
                    left.id,
                    left.ingredientId.isBlank() ? right.ingredientId : left.ingredientId,
                    Math.max(left.overcookTicks, right.overcookTicks),
                    Math.max(left.perfectWindowTicks, right.perfectWindowTicks)
            );
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
