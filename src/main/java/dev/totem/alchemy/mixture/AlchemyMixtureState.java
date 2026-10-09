package dev.totem.alchemy.mixture;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.liquid.LiquidPropertyResolver;
import dev.totem.alchemy.liquid.LiquidStabilityPolicy;
import dev.totem.alchemy.migration.LegacyAlchemyIds;
import net.minecraft.resources.Identifier;
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
 * <p>Effect amount is stored as a canonical {@link EffectDose#quantity()} measured in level-I-equivalent
 * effect ticks: {@code durationTicks * (amplifier + 1)}. A level II effect therefore carries twice the
 * quantity of an equal-duration level I effect. This lets volume dilution, modifiers and multi-effect brewing
 * conserve effect quantity instead of creating power when liquids are mixed. The legacy
 * {@link EffectDose#potencyTicks()} accessor remains as a compatibility alias for the same quantity.</p>
 */
public final class AlchemyMixtureState {
    public static final int MAX_VOLUME_UNITS = 3;
    public static final int MAX_FLASK_VOLUME_UNITS = 8;
    private final int capacity;
    public static final int DEFAULT_REACTION_TICKS = 20 * 20;
    public static final int MIN_PERFECT_WINDOW_TICKS = 20 * 5;
    public static final int MAX_PERFECT_WINDOW_TICKS = 20 * 15;
    public static final int STABILITY_MAX = 100;
    public static final double DEFAULT_SUSTAINED_EFFECT_BIAS = 0.5D;
    private static final String PRESERVE_INDEPENDENT_OUTCOMES = "state:independent_outcome_set";
    private static final Identifier WATER_LIQUID_ID =
            Identifier.fromNamespaceAndPath("minecraft", "water");
    private static final Identifier LEGACY_ACTIVATED_BASE_ID =
            Identifier.fromNamespaceAndPath("totem", "alchemy/legacy_activated_base");

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private int volumeUnits;
    private LiquidComposition liquidComposition = LiquidComposition.empty();
    private ActivatedBaseComposition activatedBaseComposition = ActivatedBaseComposition.empty();
    private int stability;
    private double stabilityDamageCarry;
    private int overcookTicks;
    private int perfectWindowTicks = perfectWindowTicksForProcessing(DEFAULT_REACTION_TICKS);
    private boolean heatLockedAfterBottling;
    private DeliveryForm deliveryForm = DeliveryForm.DRINKABLE;
    private String canonicalPotionId;
    private final Map<String, EffectDose> effects = new LinkedHashMap<>();
    private final Map<String, Reaction> reactions = new LinkedHashMap<>();
    /** M11: reserved reaction ownership only; signature outcomes are not active yet. */
    private final Map<Identifier, SignatureBrewResolver.ReactionGroup> signatureGroups = new LinkedHashMap<>();
    /** Committed groups intercept ordinary completion; outputs are claimable only when ready. */
    private final Map<Identifier, SignatureBrewProcess> signatureProcesses = new LinkedHashMap<>();
    private final Map<String, CompletedStage> completedStages = new LinkedHashMap<>();
    private final Set<String> provenance = new LinkedHashSet<>();

    public AlchemyMixtureState(int volumeUnits) {
        this(volumeUnits, MAX_VOLUME_UNITS);
    }

    public AlchemyMixtureState(int volumeUnits, int capacity) {
        this.capacity = Math.max(MAX_VOLUME_UNITS, Math.min(MAX_FLASK_VOLUME_UNITS, capacity));
        this.volumeUnits = Math.max(0, Math.min(this.capacity, volumeUnits));
        this.stability = STABILITY_MAX;
    }

    public static AlchemyMixtureState empty() {
        return new AlchemyMixtureState(0);
    }

    public AlchemyMixtureState copy() {
        return copy(capacity);
    }

    public AlchemyMixtureState copy(int capacity) {
        int bounded = Math.max(MAX_VOLUME_UNITS, Math.min(MAX_FLASK_VOLUME_UNITS, capacity));
        if (volumeUnits > bounded) throw new IllegalArgumentException("Mixture exceeds destination capacity");
        AlchemyMixtureState copy = new AlchemyMixtureState(volumeUnits, bounded);
        copy.liquidComposition = liquidComposition;
        copy.activatedBaseComposition = activatedBaseComposition;
        copy.stability = stability;
        copy.stabilityDamageCarry = stabilityDamageCarry;
        copy.overcookTicks = overcookTicks;
        copy.perfectWindowTicks = perfectWindowTicks;
        copy.heatLockedAfterBottling = heatLockedAfterBottling;
        copy.deliveryForm = deliveryForm;
        copy.canonicalPotionId = canonicalPotionId;
        copy.effects.putAll(effects);
        copy.reactions.putAll(reactions);
        copy.signatureGroups.putAll(signatureGroups);
        copy.signatureProcesses.putAll(signatureProcesses);
        copy.completedStages.putAll(completedStages);
        copy.provenance.addAll(provenance);
        return copy;
    }

    public int volumeUnits() {
        return volumeUnits;
    }

    public LiquidComposition liquidComposition() {
        return liquidComposition;
    }

    public void setLiquidComposition(LiquidComposition liquidComposition) {
        this.liquidComposition = liquidComposition == null
                ? LiquidComposition.empty()
                : liquidComposition.normalized();
    }

    public ActivatedBaseComposition activatedBaseComposition() {
        return activatedBaseComposition;
    }

    public void setActivatedBaseComposition(ActivatedBaseComposition activatedBaseComposition) {
        this.activatedBaseComposition = activatedBaseComposition == null
                ? ActivatedBaseComposition.empty()
                : activatedBaseComposition;
    }

    public double activatedBaseUnits() {
        return activatedBaseComposition.totalUnits();
    }

    public double unactivatedUnits() {
        return Math.max(0.0D, volumeUnits - activatedBaseUnits());
    }

    /**
     * Remaining base-reaction capacity. Base reactions consume this capacity by transforming
     * unactivated liquid units into explicit activated-base units; volume is conserved.
     */
    public double baseReactionCapacityUnits() {
        return unactivatedUnits();
    }

    /**
     * Transform unactivated liquid capacity into one named activated base.
     *
     * @return the actual number of units transformed, capped by remaining unactivated capacity
     */
    public double activateBaseUnits(Identifier baseId, double requestedUnits) {
        if (baseId == null || !Double.isFinite(requestedUnits) || requestedUnits <= 0.0D) {
            return 0.0D;
        }
        double transformed = Math.min(requestedUnits, baseReactionCapacityUnits());
        if (transformed <= 0.0D) {
            return 0.0D;
        }

        Map<Identifier, Double> updated = new LinkedHashMap<>(activatedBaseComposition.components());
        updated.merge(baseId, transformed, Double::sum);
        activatedBaseComposition = ActivatedBaseComposition.of(updated);
        return transformed;
    }

    public double baseConcentration() {
        return volumeUnits <= 0 ? 0.0D : activatedBaseUnits() / volumeUnits;
    }

    public int stability() {
        return stability;
    }

    public double stabilityDamageCarry() {
        return stabilityDamageCarry;
    }

    public void setStability(int stability) {
        this.stability = Math.max(0, Math.min(STABILITY_MAX, stability));
        this.stabilityDamageCarry = 0.0D;
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
        return !activatedBaseComposition.isEmpty();
    }

    /**
     * Compatibility facade for callers that still use the legacy activation flag.
     * Enabling an empty nonzero-volume mixture creates only the anonymous legacy fallback;
     * disabling clears explicit activated-base units. Existing explicit composition is preserved.
     */
    public void setBaseActivated(boolean baseActivated) {
        if (!baseActivated) {
            activatedBaseComposition = ActivatedBaseComposition.empty();
        } else if (activatedBaseComposition.isEmpty() && volumeUnits > 0) {
            activatedBaseComposition =
                    ActivatedBaseComposition.single(LEGACY_ACTIVATED_BASE_ID, volumeUnits);
        }
    }

    public boolean isHeatLockedAfterBottling() {
        return heatLockedAfterBottling;
    }

    /**
     * Seals a finished portable potion: retain its final chemistry, but discard completed ingredient and
     * cooking history that can no longer affect it. A newly added ingredient unlocks heating again.
     */
    public void lockHeatIfFinished() {
        if (!isEmpty() && !hasPendingReactions()) {
            heatLockedAfterBottling = true;
            completedStages.clear();
            signatureGroups.clear();
            // Committed signature results must survive until claimed, including finished groups.
            overcookTicks = 0;
            perfectWindowTicks = 0;
            provenance.removeIf(AlchemyMixtureState::isFinishedCookingHistory);
        }
    }

    private static boolean isFinishedCookingHistory(String marker) {
        return marker.startsWith("reaction:")
                || marker.startsWith("concurrent:")
                || marker.startsWith("modifier:")
                || marker.startsWith("potion:")
                || marker.startsWith("cauldron:")
                || marker.startsWith("compound:input:")
                || marker.startsWith("compound:base:");
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

    public Collection<SignatureBrewProcess> signatureProcesses() {
        return List.copyOf(signatureProcesses.values());
    }

    /**
     * Commit one previously reserved group before its first member finishes.
     * This is the only transition that authorizes suppression of ordinary outputs.
     */
    public boolean commitSignatureGroup(Identifier signatureId, SignatureBrewDefinition.Result result) {
        if (signatureId == null || result == null || signatureProcesses.containsKey(signatureId)) {
            return false;
        }
        SignatureBrewResolver.ReactionGroup planned = signatureGroups.get(signatureId);
        if (planned == null) {
            return false;
        }
        for (String id : planned.memberReactionIds()) {
            Reaction pending = reactions.get(id);
            if (pending == null || pending.complete() || signatureProcesses.values().stream()
                    .anyMatch(group -> group.owns(id))) {
                return false;
            }
        }
        signatureProcesses.put(signatureId, SignatureBrewProcess.begin(planned, result));
        signatureGroups.remove(signatureId);
        return true;
    }

    /**
     * Claim a ready group's output at most once. The caller must persist the
     * updated mixture and grant the corresponding output as one server-side transaction.
     * No item is emitted from this method.
     */
    public java.util.Optional<SignatureBrewDefinition.Result> claimSignatureResult(Identifier signatureId) {
        SignatureBrewProcess process = signatureProcesses.get(signatureId);
        if (process == null || !process.ready()) {
            return java.util.Optional.empty();
        }
        signatureProcesses.remove(signatureId);
        return java.util.Optional.of(process.result());
    }

    public Collection<SignatureBrewResolver.ReactionGroup> signatureGroups() {
        return List.copyOf(signatureGroups.values());
    }

    /**
     * Atomically replace in-flight group reservations without changing individual reaction timers.
     *
     * <p>Each owned reaction must still be pending, and no reaction may be owned twice.
     * A reservation is a planning marker, not permission to settle signature outcomes:
     * ordinary completion remains authoritative until M11 group settlement is implemented.</p>
     */
    public boolean replaceSignatureGroups(Collection<SignatureBrewResolver.ReactionGroup> proposedGroups) {
        if (proposedGroups == null || isEmpty() || !signatureProcesses.isEmpty()) {
            return false;
        }
        for (SignatureBrewResolver.ReactionGroup previous : signatureGroups.values()) {
            if (previous.memberReactionIds().stream().anyMatch(id -> {
                Reaction reaction = reactions.get(id);
                return reaction == null || reaction.complete();
            })) {
                return false;
            }
        }
        Map<Identifier, SignatureBrewResolver.ReactionGroup> proposed = new LinkedHashMap<>();
        Set<String> reserved = new LinkedHashSet<>();
        for (SignatureBrewResolver.ReactionGroup group : proposedGroups) {
            if (group == null || proposed.putIfAbsent(group.signatureId(), group) != null) {
                return false;
            }
            for (String id : group.memberReactionIds()) {
                Reaction reaction = reactions.get(id);
                if (reaction == null || reaction.complete() || !reserved.add(id)) {
                    return false;
                }
            }
        }
        signatureGroups.clear();
        signatureGroups.putAll(proposed);
        return true;
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

    public Reaction pendingReactionForIngredient(String ingredientId) {
        if (ingredientId == null) {
            return null;
        }
        return reactions.values().stream()
                .filter(reaction -> ingredientId.equals(reaction.ingredientId()))
                .min(Comparator.comparing(Reaction::id))
                .orElse(null);
    }

    public Reaction incrementPendingReactionDoseForIngredient(String ingredientId, int maxDose) {
        Reaction existing = pendingReactionForIngredient(ingredientId);
        if (existing == null || maxDose < 1 || existing.dose() >= maxDose) {
            return null;
        }
        Reaction updated = existing.withAdditionalDose(1);
        if (updated.dose() > maxDose) {
            return null;
        }
        reactions.put(updated.id(), updated);
        return updated;
    }

    public boolean canOvercook() {
        return !heatLockedAfterBottling && !isEmpty() && !hasPendingReactions() && !hasCompletedStages()
                && (baseActivated() || !effects.isEmpty());
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
            applyStabilityLoss(5);
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
        // M11 staging: invalidate reservations touching a completed member until
        // atomic group settlement exists. The legacy individual outcome remains unchanged.
        if (!completed.isEmpty()) {
            Set<String> finishedIds = new LinkedHashSet<>();
            completed.forEach(reaction -> finishedIds.add(reaction.id()));
            signatureGroups.values().removeIf(group ->
                    group.memberReactionIds().stream().anyMatch(finishedIds::contains));
        }
        for (Reaction reaction : completed) {
            SignatureBrewProcess committed = signatureProcesses.values().stream()
                    .filter(group -> group.owns(reaction.id()))
                    .findFirst()
                    .orElse(null);
            if (committed == null) {
                applyReaction(reaction);
                completedStages.put(reaction.id(), new CompletedStage(
                        reaction.id(),
                        reaction.ingredientId(),
                        0,
                        perfectWindowTicksForProcessing(reaction.requiredTicks())
                ));
            } else {
                // An owned reaction advances on its original timer, but never
                // pays out its ordinary result or its ordinary completed stage.
                signatureProcesses.put(committed.signatureId(), committed.completeMember(reaction.id()));
            }
            reactions.remove(reaction.id());
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

        double producedScale = AlchemyMixtureBrewing.pendingReactionEffectQuantityScale(this, reaction);
        Map<String, EffectDose> producedEffects =
                producedScale >= 0.999999D
                        ? reaction.targetEffects()
                        : scaleEffectDoses(reaction.targetEffects(), producedScale);
        if (independentOutcomeSet) {
            addIndependentOutcomeEffects(producedEffects);
        } else {
            addEffects(producedEffects);
        }

        boolean appliedRegisteredBase =
                AlchemyMixtureBrewing.applyCompletedBaseReaction(this, reaction);
        if (!appliedRegisteredBase && BrewingMaterialSettings.isStarter(reaction.ingredientId())) {
            setBaseActivated(true);
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
            applyStabilityLoss(3);
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
            applyStabilityLoss(6);
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
        double beforeCarry = stabilityDamageCarry;
        int appliedDamage = scaledStabilityDamage(decay);
        stability = Math.max(0, stability - appliedDamage);
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
        return before != stability || Double.compare(beforeCarry, stabilityDamageCarry) != 0 || mutated;
    }

    private void applyStabilityLoss(int rawDamage) {
        if (rawDamage <= 0) {
            return;
        }
        int appliedDamage = scaledStabilityDamage(rawDamage);
        stability = Math.max(0, stability - appliedDamage);
    }

    private int scaledStabilityDamage(int rawDamage) {
        LiquidStabilityPolicy.DamageStep step = LiquidStabilityPolicy.scaleDamage(
                rawDamage,
                stabilityDamageCarry,
                LiquidPropertyResolver.resolve(liquidComposition)
        );
        stabilityDamageCarry = step.carry();
        return step.wholeDamage();
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
            case "minecraft:strength", "totem:alchemy/firefly_strength" -> "minecraft:weakness";
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
        if (other == null || other.isEmpty() || volumeUnits + other.volumeUnits > capacity) {
            return false;
        }
        // Until replan-after-merge is implemented, never silently lose group ownership.
        if (!signatureGroups.isEmpty() || !other.signatureGroups.isEmpty()
                || !signatureProcesses.isEmpty() || !other.signatureProcesses.isEmpty()) {
            return false;
        }
        boolean activeHeat = canAdvanceUnderHeat() || other.canAdvanceUnderHeat();
        int oldVolume = volumeUnits;
        int incomingVolume = other.volumeUnits;
        int mergedVolume = oldVolume + incomingVolume;
        LiquidComposition mergedLiquidComposition = mergeLiquidComposition(
                liquidComposition,
                oldVolume,
                other.liquidComposition,
                incomingVolume
        );
        ActivatedBaseComposition mergedActivatedBaseComposition =
                mergeActivatedBaseComposition(activatedBaseComposition, other.activatedBaseComposition);
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
        heatLockedAfterBottling = !activeHeat
                && (heatLockedAfterBottling || other.heatLockedAfterBottling);
        deliveryForm = deliveryForm == other.deliveryForm ? deliveryForm : DeliveryForm.DRINKABLE;
        overcookTicks = 0;
        perfectWindowTicks = Math.max(perfectWindowTicks, other.perfectWindowTicks);
        int mergedStability = mergedVolume == 0 ? STABILITY_MAX
                : Math.max(0, Math.min(STABILITY_MAX,
                (stability * oldVolume + other.stability * incomingVolume) / mergedVolume));
        double mergedStabilityDamageCarry = mergedVolume == 0 ? 0.0D
                : (stabilityDamageCarry * oldVolume
                + other.stabilityDamageCarry * incomingVolume) / mergedVolume;
        if (canonicalPotionId == null || other.canonicalPotionId == null
                || !canonicalPotionId.equals(other.canonicalPotionId)) {
            canonicalPotionId = null;
        }
        volumeUnits = mergedVolume;
        liquidComposition = mergedLiquidComposition;
        activatedBaseComposition = mergedActivatedBaseComposition;
        stability = mergedStability;
        stabilityDamageCarry = mergedStabilityDamageCarry;
        if (stability > 0) {
            applyStabilityLoss(2);
        }
        if (!preserveOutcomeSet) {
            neutralizeOpposites();
        }
        return true;
    }

    private static ActivatedBaseComposition mergeActivatedBaseComposition(
            ActivatedBaseComposition current,
            ActivatedBaseComposition incoming
    ) {
        if (current == null || current.isEmpty()) {
            return incoming == null ? ActivatedBaseComposition.empty() : incoming;
        }
        if (incoming == null || incoming.isEmpty()) {
            return current;
        }

        Map<Identifier, Double> merged = new LinkedHashMap<>(current.components());
        incoming.components().forEach((baseId, units) ->
                merged.merge(baseId, units, Double::sum));
        return ActivatedBaseComposition.of(merged);
    }

    private static LiquidComposition mergeLiquidComposition(
            LiquidComposition current,
            int currentVolume,
            LiquidComposition incoming,
            int incomingVolume
    ) {
        if (currentVolume <= 0) {
            return incoming == null ? LiquidComposition.empty() : incoming;
        }
        if (incomingVolume <= 0) {
            return current == null ? LiquidComposition.empty() : current;
        }
        if (current == null || incoming == null || current.isEmpty() || incoming.isEmpty()) {
            return LiquidComposition.empty();
        }

        int totalVolume = currentVolume + incomingVolume;
        Map<Identifier, Double> weighted = new LinkedHashMap<>();
        current.components().forEach((liquidId, fraction) ->
                weighted.merge(liquidId, fraction * currentVolume, Double::sum));
        incoming.components().forEach((liquidId, fraction) ->
                weighted.merge(liquidId, fraction * incomingVolume, Double::sum));
        weighted.replaceAll((liquidId, amount) -> amount / totalVolume);
        return LiquidComposition.of(weighted).normalized();
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
        // A committed group owns one physical batch: splitting the batch
        // before its result is claimed could duplicate the final output.
        if (!signatureProcesses.isEmpty() && requestedUnits < volumeUnits) {
            return empty();
        }
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
        liquidComposition = LiquidComposition.empty();
        activatedBaseComposition = ActivatedBaseComposition.empty();
        effects.clear();
        reactions.clear();
        signatureGroups.clear();
        signatureProcesses.clear();
        completedStages.clear();
        provenance.clear();
        canonicalPotionId = null;
        stability = STABILITY_MAX;
        stabilityDamageCarry = 0.0D;
        overcookTicks = 0;
        perfectWindowTicks = perfectWindowTicksForProcessing(DEFAULT_REACTION_TICKS);
        heatLockedAfterBottling = false;
        deliveryForm = DeliveryForm.DRINKABLE;
    }

    private AlchemyMixtureState scaledCopy(double factor, int newVolume) {
        AlchemyMixtureState result = new AlchemyMixtureState(newVolume, Math.max(MAX_VOLUME_UNITS, newVolume));
        result.liquidComposition = liquidComposition;
        result.activatedBaseComposition = scaleActivatedBaseComposition(activatedBaseComposition, factor);
        result.stability = stability;
        result.stabilityDamageCarry = stabilityDamageCarry;
        result.overcookTicks = overcookTicks;
        result.perfectWindowTicks = perfectWindowTicks;
        result.heatLockedAfterBottling = heatLockedAfterBottling;
        result.deliveryForm = deliveryForm;
        result.canonicalPotionId = canonicalPotionId;
        effects.forEach((id, dose) -> result.effects.put(id, dose.scale(factor)));
        reactions.forEach((id, reaction) -> result.reactions.put(id, reaction.scale(factor, newVolume)));
        result.signatureGroups.putAll(signatureGroups);
        result.signatureProcesses.putAll(signatureProcesses);
        result.completedStages.putAll(completedStages);
        result.provenance.addAll(provenance);
        return result;
    }

    private void scaleInPlace(double factor, int newVolume) {
        activatedBaseComposition = scaleActivatedBaseComposition(activatedBaseComposition, factor);
        effects.replaceAll((id, dose) -> dose.scale(factor));
        reactions.replaceAll((id, reaction) -> reaction.scale(factor, Math.max(1, newVolume)));
    }

    private static ActivatedBaseComposition scaleActivatedBaseComposition(
            ActivatedBaseComposition composition,
            double factor
    ) {
        if (composition == null || composition.isEmpty()) {
            return ActivatedBaseComposition.empty();
        }

        Map<Identifier, Double> scaled = new LinkedHashMap<>();
        composition.components().forEach((baseId, units) ->
                scaled.put(baseId, units * factor));
        return ActivatedBaseComposition.of(scaled);
    }

    private static Map<String, EffectDose> scaleEffectDoses(
            Map<String, EffectDose> values,
            double factor
    ) {
        if (values == null || values.isEmpty() || factor <= 0.0D) {
            return Map.of();
        }
        if (factor >= 0.999999D) {
            return values;
        }
        Map<String, EffectDose> scaled = new LinkedHashMap<>();
        values.forEach((id, dose) -> {
            if (dose != null) {
                scaled.put(id, dose.scale(factor));
            }
        });
        return scaled;
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
        List<String> positives = List.of("minecraft:strength", "totem:alchemy/firefly_strength");
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
        if (liquidComposition.isEmpty()) {
            out.append("L|").append('\n');
        } else {
            liquidComposition.components().forEach((liquidId, fraction) ->
                    out.append("L|").append(enc(liquidId.toString())).append('|')
                            .append(fraction).append('\n'));
        }
        if (activatedBaseComposition.isEmpty()) {
            out.append("A|").append('\n');
        } else {
            activatedBaseComposition.components().forEach((baseId, units) ->
                    out.append("A|").append(enc(baseId.toString())).append('|')
                            .append(units).append('\n'));
        }
        out.append("S|").append(stability).append('\n');
        out.append("D|").append(stabilityDamageCarry).append('\n');
        out.append("B|").append(baseActivated() ? 1 : 0).append('\n');
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
                        .append(enc(encodeEffects(reaction.targetEffects()))).append('|')
                        .append(reaction.dose()).append('\n'));
        signatureGroups.values().stream()
                .sorted(Comparator.comparing(group -> group.signatureId().toString()))
                .forEach(group -> {
                    out.append("G|").append(enc(group.signatureId().toString()));
                    group.memberReactionIds().forEach(id -> out.append('|').append(enc(id)));
                    out.append('\n');
                });
        signatureProcesses.values().stream()
                .sorted(Comparator.comparing(process -> process.signatureId().toString()))
                .forEach(process -> {
                    SignatureBrewDefinition.Result result = process.result();
                    out.append("Q|").append(enc(process.signatureId().toString())).append('|')
                            .append(result.type().name()).append('|')
                            .append(enc(result.itemId().toString())).append('|')
                            .append(result.count()).append('|')
                            .append(enc(result.containerItemId() == null ? "" : result.containerItemId().toString())).append('|')
                            .append(enc(result.potionId() == null ? "" : result.potionId().toString())).append('|')
                            .append(encodeMemberIds(process.memberReactionIds())).append('|')
                            .append(encodeMemberIds(process.completedReactionIds().stream().sorted().toList()))
                            .append('\n');
                });
        completedStages.values().stream().sorted(Comparator.comparing(CompletedStage::id)).forEach(stage ->
                out.append("T|").append(enc(stage.id())).append('|')
                        .append(enc(stage.ingredientId())).append('|')
                        .append(stage.overcookTicks()).append('|')
                        .append(stage.perfectWindowTicks()).append('\n'));
        provenance.stream().sorted().forEach(value -> out.append("P|").append(enc(value)).append('\n'));
        return out.toString();
    }

    private static String encodeMemberIds(Collection<String> ids) {
        return String.join(",", ids.stream().map(AlchemyMixtureState::enc).toList());
    }

    private static List<String> decodeMemberIds(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : encoded.split(",", -1)) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Malformed signature member list");
            }
            result.add(dec(part));
        }
        return List.copyOf(result);
    }

    public static AlchemyMixtureState decode(String encoded) {
        return decode(encoded, MAX_VOLUME_UNITS);
    }

    public static AlchemyMixtureState decode(String encoded, int capacity) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }
        AlchemyMixtureState state = new AlchemyMixtureState(0, capacity);
        boolean sawBaseMarker = false;
        boolean decodedBaseActivated = false;
        boolean sawLiquidMarker = false;
        boolean sawActivatedBaseMarker = false;
        Map<Identifier, Double> decodedLiquids = new LinkedHashMap<>();
        Map<Identifier, Double> decodedActivatedBases = new LinkedHashMap<>();
        for (String line : encoded.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] part = line.split("\\|", -1);
            try {
                switch (part[0]) {
                    case "V" -> state.volumeUnits = Math.max(0, Math.min(state.capacity, Integer.parseInt(part[1])));
                    case "L" -> {
                        sawLiquidMarker = true;
                        if (part.length >= 3 && !part[1].isBlank()) {
                            Identifier liquidId = Identifier.tryParse(dec(part[1]));
                            if (liquidId == null) {
                                throw new IllegalArgumentException("Invalid liquid identifier");
                            }
                            double fraction = Double.parseDouble(part[2]);
                            if (!Double.isFinite(fraction) || fraction < 0.0D) {
                                throw new IllegalArgumentException("Invalid liquid fraction");
                            }
                            decodedLiquids.merge(liquidId, fraction, Double::sum);
                        }
                    }
                    case "A" -> {
                        sawActivatedBaseMarker = true;
                        if (part.length >= 3 && !part[1].isBlank()) {
                            Identifier baseId = Identifier.tryParse(dec(part[1]));
                            if (baseId == null) {
                                throw new IllegalArgumentException("Invalid activated-base identifier");
                            }
                            double units = Double.parseDouble(part[2]);
                            if (!Double.isFinite(units) || units < 0.0D) {
                                throw new IllegalArgumentException("Invalid activated-base units");
                            }
                            decodedActivatedBases.merge(baseId, units, Double::sum);
                        }
                    }
                    case "S" -> state.stability = Math.max(0, Math.min(STABILITY_MAX, Integer.parseInt(part[1])));
                    case "D" -> {
                        double carry = Double.parseDouble(part[1]);
                        if (!Double.isFinite(carry) || carry < 0.0D || carry >= 1.0D) {
                            throw new IllegalArgumentException("Invalid stability damage carry");
                        }
                        state.stabilityDamageCarry = carry;
                    }
                    case "B" -> {
                        decodedBaseActivated = Integer.parseInt(part[1]) != 0;
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
                            Integer.parseInt(part[5]), part.length >= 11 ? Integer.parseInt(part[10]) : 1,
                            blankToNull(dec(part[6])), blankToNull(dec(part[7])),
                            decodeEffects(dec(part[8])), decodeEffects(dec(part[9]))));
                    case "G" -> {
                        if (part.length < 3) throw new IllegalArgumentException("Incomplete signature group");
                        Identifier id = Identifier.tryParse(dec(part[1]));
                        if (id == null) throw new IllegalArgumentException("Invalid signature group ID");
                        List<String> members = new ArrayList<>();
                        for (int i = 2; i < part.length; i++) {
                            members.add(dec(part[i]));
                        }
                        SignatureBrewResolver.ReactionGroup group =
                                new SignatureBrewResolver.ReactionGroup(id, members);
                        state.signatureGroups.putIfAbsent(id, group);
                    }
                    case "Q" -> {
                        if (part.length != 9) {
                            throw new IllegalArgumentException("Malformed committed signature record");
                        }
                        Identifier id = Identifier.parse(dec(part[1]));
                        SignatureBrewDefinition.Type type =
                                SignatureBrewDefinition.Type.valueOf(part[2]);
                        Identifier itemId = Identifier.parse(dec(part[3]));
                        int count = Integer.parseInt(part[4]);
                        String containerId = dec(part[5]);
                        String potionId = dec(part[6]);
                        SignatureBrewDefinition.Result result = new SignatureBrewDefinition.Result(
                                type, itemId, count,
                                containerId.isEmpty() ? null : Identifier.parse(containerId),
                                potionId.isEmpty() ? null : Identifier.parse(potionId));
                        SignatureBrewProcess process = new SignatureBrewProcess(
                                id, result, decodeMemberIds(part[7]),
                                new LinkedHashSet<>(decodeMemberIds(part[8])));
                        state.signatureProcesses.putIfAbsent(id, process);
                    }
                    case "T" -> state.completedStages.put(dec(part[1]), new CompletedStage(
                            dec(part[1]), dec(part[2]), Integer.parseInt(part[3]), Integer.parseInt(part[4])));
                    case "P" -> state.provenance.add(dec(part[1]));
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // Corrupt individual entries are ignored so one bad field cannot brick a world or item stack.
            }
        }
        if (!decodedLiquids.isEmpty()) {
            state.liquidComposition = LiquidComposition.of(decodedLiquids).normalized();
        } else if (!sawLiquidMarker && state.volumeUnits > 0) {
            state.liquidComposition = LiquidComposition.single(WATER_LIQUID_ID, 1.0D);
        } else {
            state.liquidComposition = LiquidComposition.empty();
        }
        if (!decodedActivatedBases.isEmpty()) {
            state.activatedBaseComposition = ActivatedBaseComposition.of(decodedActivatedBases);
        } else {
            state.activatedBaseComposition = ActivatedBaseComposition.empty();
        }
        if (!sawBaseMarker) {
            decodedBaseActivated = inferLegacyBase(state);
        }
        if (!sawActivatedBaseMarker && decodedBaseActivated && state.volumeUnits > 0) {
            state.activatedBaseComposition =
                    ActivatedBaseComposition.single(LEGACY_ACTIVATED_BASE_ID, state.volumeUnits);
        }
        rewriteLegacyIds(state);
        // Validate committed groups before uncommitted reservations so active
        // ownership wins in any malformed/contradictory input.
        Set<String> committedMembers = new LinkedHashSet<>();
        state.signatureProcesses.values().removeIf(process -> {
            boolean invalid = process.memberReactionIds().stream().anyMatch(id -> {
                Reaction reaction = state.reactions.get(id);
                if (committedMembers.contains(id)) return true;
                return process.completedReactionIds().contains(id)
                        ? reaction != null
                        : reaction == null || reaction.complete();
            });
            if (!invalid) {
                committedMembers.addAll(process.memberReactionIds());
            }
            return invalid;
        });
        // Corrupt, overlapping or no-longer-pending reservations must not survive reload.
        Set<String> decodedReserved = new LinkedHashSet<>(committedMembers);
        state.signatureGroups.values().removeIf(group -> {
            boolean invalid = group.memberReactionIds().stream().anyMatch(id -> {
                Reaction reaction = state.reactions.get(id);
                return reaction == null || reaction.complete() || decodedReserved.contains(id);
            });
            if (!invalid) {
                decodedReserved.addAll(group.memberReactionIds());
            }
            return invalid;
        });
        // Migrate mixtures created by builds that intentionally preserved opposing rolled outcomes.
        state.provenance.remove(PRESERVE_INDEPENDENT_OUTCOMES);
        state.neutralizeOpposites();
        return state;
    }

    /** Rewrites Alchemy-owned identifiers while decoding an old stored mixture. */
    private static void rewriteLegacyIds(AlchemyMixtureState state) {
        state.canonicalPotionId = LegacyAlchemyIds.canonicalize(state.canonicalPotionId);
        rewriteEffects(state.effects);

        Map<String, Reaction> rewrittenReactions = new LinkedHashMap<>();
        state.reactions.values().forEach(reaction -> {
            Reaction rewritten = new Reaction(
                    LegacyAlchemyIds.canonicalizeEmbedded(reaction.id()),
                    LegacyAlchemyIds.canonicalize(reaction.ingredientId()),
                    reaction.elapsedTicks(),
                    reaction.requiredTicks(),
                    reaction.volumeUnits(),
                    reaction.dose(),
                    blankToNull(LegacyAlchemyIds.canonicalize(reaction.sourcePotionId())),
                    blankToNull(LegacyAlchemyIds.canonicalize(reaction.targetPotionId())),
                    rewriteEffectsCopy(reaction.sourceEffects()),
                    rewriteEffectsCopy(reaction.targetEffects())
            );
            rewrittenReactions.merge(rewritten.id(), rewritten, Reaction::mergeSameReaction);
        });
        state.reactions.clear();
        state.reactions.putAll(rewrittenReactions);

        Map<String, CompletedStage> rewrittenStages = new LinkedHashMap<>();
        state.completedStages.values().forEach(stage -> {
            CompletedStage rewritten = new CompletedStage(
                    LegacyAlchemyIds.canonicalizeEmbedded(stage.id()),
                    LegacyAlchemyIds.canonicalize(stage.ingredientId()),
                    stage.overcookTicks(),
                    stage.perfectWindowTicks()
            );
            rewrittenStages.merge(rewritten.id(), rewritten, CompletedStage::mergeSameStage);
        });
        state.completedStages.clear();
        state.completedStages.putAll(rewrittenStages);

        Set<String> rewrittenProvenance = new LinkedHashSet<>();
        state.provenance.forEach(value -> rewrittenProvenance.add(LegacyAlchemyIds.canonicalizeEmbedded(value)));
        state.provenance.clear();
        state.provenance.addAll(rewrittenProvenance);
    }

    private static Map<String, EffectDose> rewriteEffectsCopy(Map<String, EffectDose> effects) {
        Map<String, EffectDose> rewritten = new LinkedHashMap<>(effects);
        rewriteEffects(rewritten);
        return rewritten;
    }

    private static void rewriteEffects(Map<String, EffectDose> effects) {
        Map<String, EffectDose> rewritten = new LinkedHashMap<>();
        effects.forEach((id, dose) -> rewritten.merge(
                LegacyAlchemyIds.canonicalize(id), dose, EffectDose::merge
        ));
        effects.clear();
        effects.putAll(rewritten);
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

    /**
     * Conserved effect quantity plus the highest intended amplifier.
     *
     * <p>{@code quantity} is the canonical amount unit for sustained effects and is measured in
     * level-I-equivalent effect ticks: {@code durationTicks * (amplifier + 1)}. Quantity is independent of
     * container volume; concentration and the final duration presentation are derived later from this value.</p>
     */
    public record EffectDose(double quantity, int amplifierCap) {
        public EffectDose {
            quantity = Math.max(0.0D, quantity);
            amplifierCap = Math.max(0, amplifierCap);
        }

        /** Canonical sustained-effect quantity for one vanilla-style duration/amplifier pair. */
        public static double quantityForDuration(int durationTicks, int amplifier) {
            return (double) Math.max(0, durationTicks) * (Math.max(0, amplifier) + 1);
        }

        public static EffectDose fromDuration(int durationTicks, int amplifier) {
            return new EffectDose(quantityForDuration(durationTicks, amplifier), amplifier);
        }

        /**
         * Compatibility alias retained while existing mixture code migrates from the old potency-ticks name.
         * It is exactly the same conserved value as {@link #quantity()}.
         */
        public double potencyTicks() {
            return quantity;
        }

        public EffectDose merge(EffectDose other) {
            return new EffectDose(quantity + other.quantity, Math.max(amplifierCap, other.amplifierCap));
        }

        public EffectDose scale(double factor) {
            return new EffectDose(quantity * Math.max(0.0D, factor), amplifierCap);
        }

        /**
         * Effect quantity density in level-I-equivalent ticks per bottle-equivalent volume unit.
         *
         * <p>Quantity is conserved chemistry; concentration is the derived density after dilution. A zero or
         * negative volume has no meaningful liquid concentration and therefore resolves to zero.</p>
         */
        public double concentrationForVolume(int volumeUnits) {
            return volumeUnits <= 0 ? 0.0D : quantity / volumeUnits;
        }

        /**
         * Sustained-effect presentation relative to one standard registered potion dose.
         *
         * <p>{@code potencyBias} is clamped to {@code [0, 1]}. A bias of {@code 0} allocates all relative
         * concentration change to duration, {@code 1} allocates it to potency, and the default {@code 0.5}
         * splits it evenly. The potency and duration scales always multiply back to the relative concentration,
         * so the underlying EffectDose concentration remains conserved.</p>
         */
        public SustainedEffectPresentation sustainedPresentation(EffectDose standardDose, int volumeUnits) {
            return sustainedPresentation(standardDose, volumeUnits, DEFAULT_SUSTAINED_EFFECT_BIAS);
        }

        public SustainedEffectPresentation sustainedPresentation(
                EffectDose standardDose,
                int volumeUnits,
                double potencyBias
        ) {
            if (standardDose == null || standardDose.quantity <= 0.0001D || volumeUnits <= 0) {
                return SustainedEffectPresentation.EMPTY;
            }
            double concentration = concentrationForVolume(volumeUnits);
            double standardConcentration = standardDose.concentrationForVolume(1);
            if (concentration <= 0.0001D || standardConcentration <= 0.0001D) {
                return SustainedEffectPresentation.EMPTY;
            }
            double relativeConcentration = concentration / standardConcentration;
            double clampedBias = Math.max(0.0D, Math.min(1.0D, potencyBias));
            double potencyScale = Math.pow(relativeConcentration, clampedBias);
            double durationScale = Math.pow(relativeConcentration, 1.0D - clampedBias);
            double standardPotency = standardDose.amplifierCap + 1.0D;
            double standardDuration = standardDose.quantity / standardPotency;
            return new SustainedEffectPresentation(
                    standardPotency * potencyScale,
                    standardDuration * durationScale
            );
        }

        /**
         * Continuous potency level for an instantaneous effect relative to one registered standard dose.
         *
         * <p>Instant effects have no duration axis, so the entire relative concentration change maps to
         * potency. A value of {@code 1.0} is level I-equivalent, {@code 2.0} is level II-equivalent, and
         * fractional values remain representable for later discretization/application rules.</p>
         */
        public double instantPotencyLevel(EffectDose standardDose, int volumeUnits) {
            if (standardDose == null || standardDose.quantity <= 0.0001D || volumeUnits <= 0) {
                return 0.0D;
            }
            double concentration = concentrationForVolume(volumeUnits);
            double standardConcentration = standardDose.concentrationForVolume(1);
            if (concentration <= 0.0001D || standardConcentration <= 0.0001D) {
                return 0.0D;
            }
            double relativeConcentration = concentration / standardConcentration;
            double standardPotency = standardDose.amplifierCap + 1.0D;
            return standardPotency * relativeConcentration;
        }

        public int durationForVolume(int volume) {
            int safeVolume = Math.max(1, volume);
            return Math.max(1, (int) Math.round(quantity / safeVolume / (amplifierCap + 1.0D)));
        }
    }

    /**
     * Continuous sustained-effect presentation. Potency level uses level-I-equivalent scale:
     * 1.0 = vanilla amplifier 0, 2.0 = amplifier 1, and fractional values remain representable for UI
     * and later discretization rules.
     */
    public record SustainedEffectPresentation(double potencyLevel, double durationTicks) {
        private static final SustainedEffectPresentation EMPTY = new SustainedEffectPresentation(0.0D, 0.0D);

        public SustainedEffectPresentation {
            potencyLevel = Math.max(0.0D, potencyLevel);
            durationTicks = Math.max(0.0D, durationTicks);
        }

        public double concentration() {
            return potencyLevel * durationTicks;
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
            int dose,
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
            volumeUnits = Math.max(1, Math.min(MAX_FLASK_VOLUME_UNITS, volumeUnits));
            dose = Math.max(1, dose);
            sourceEffects = Map.copyOf(sourceEffects == null ? Map.of() : sourceEffects);
            targetEffects = Map.copyOf(targetEffects == null ? Map.of() : targetEffects);
        }

        /** Compatibility constructor for callers that have not started explicit dose accumulation yet. */
        public Reaction(
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
            this(id, ingredientId, elapsedTicks, requiredTicks, volumeUnits, 1,
                    sourcePotionId, targetPotionId, sourceEffects, targetEffects);
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
                    requiredTicks, volumeUnits, dose, sourcePotionId, targetPotionId, sourceEffects, targetEffects);
        }

        public Reaction withAdditionalDose(int additionalDose) {
            if (additionalDose <= 0) {
                return this;
            }
            int increased = (int) Math.min(Integer.MAX_VALUE, (long) dose + additionalDose);
            return new Reaction(id, ingredientId, elapsedTicks, requiredTicks, volumeUnits, increased,
                    sourcePotionId, targetPotionId, sourceEffects, targetEffects);
        }

        public Reaction scale(double factor, int newVolume) {
            Map<String, EffectDose> source = new LinkedHashMap<>();
            sourceEffects.forEach((id, effectDose) -> source.put(id, effectDose.scale(factor)));
            Map<String, EffectDose> target = new LinkedHashMap<>();
            targetEffects.forEach((id, effectDose) -> target.put(id, effectDose.scale(factor)));
            return new Reaction(id, ingredientId, elapsedTicks, requiredTicks, newVolume, dose,
                    sourcePotionId, targetPotionId, source, target);
        }

        private Reaction mergeWeighted(Reaction other, int currentWeight, int otherWeight) {
            int total = Math.max(1, currentWeight + otherWeight);
            int elapsed = (elapsedTicks * currentWeight + other.elapsedTicks * otherWeight) / total;
            Map<String, EffectDose> source = mergeMaps(sourceEffects, other.sourceEffects);
            Map<String, EffectDose> target = mergeMaps(targetEffects, other.targetEffects);
            String sourcePotion = java.util.Objects.equals(sourcePotionId, other.sourcePotionId) ? sourcePotionId : null;
            String targetPotion = java.util.Objects.equals(targetPotionId, other.targetPotionId) ? targetPotionId : null;
            // Dose is per-reaction state, not a conserved liquid quantity. Split/recombine must not duplicate it.
            int mergedDose = Math.max(dose, other.dose);
            return new Reaction(id, ingredientId, elapsed, Math.max(requiredTicks, other.requiredTicks),
                    Math.min(MAX_FLASK_VOLUME_UNITS, volumeUnits + other.volumeUnits), mergedDose,
                    sourcePotion, targetPotion, source, target);
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
