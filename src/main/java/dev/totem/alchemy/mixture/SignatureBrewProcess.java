package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A committed signature-brew group whose members retain their existing material timers.
 *
 * <p>Completed members must NOT also apply their ordinary reaction output. A group
 * is ready when every reserved reaction has finished; the caller may claim its
 * result once, and only after updating/persisting the owning mixture state.</p>
 */
public record SignatureBrewProcess(
        Identifier signatureId,
        SignatureBrewDefinition.Result result,
        List<String> memberReactionIds,
        Set<String> completedReactionIds
) {
    public SignatureBrewProcess {
        Objects.requireNonNull(signatureId, "signatureId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(memberReactionIds, "memberReactionIds");
        Objects.requireNonNull(completedReactionIds, "completedReactionIds");

        if (memberReactionIds.isEmpty()
                || memberReactionIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(memberReactionIds).size() != memberReactionIds.size()) {
            throw new IllegalArgumentException("Signature process requires unique, nonblank reaction members");
        }
        if (!memberReactionIds.containsAll(completedReactionIds)) {
            throw new IllegalArgumentException("Completed signature reactions must belong to the group");
        }
        memberReactionIds = memberReactionIds.stream().sorted().toList();
        completedReactionIds = Set.copyOf(completedReactionIds);
    }

    public static SignatureBrewProcess begin(
            SignatureBrewResolver.ReactionGroup group,
            SignatureBrewDefinition.Result result
    ) {
        Objects.requireNonNull(group, "group");
        return new SignatureBrewProcess(group.signatureId(), result, group.memberReactionIds(), Set.of());
    }

    public boolean owns(String reactionId) {
        return memberReactionIds.contains(reactionId);
    }

    public boolean ready() {
        return completedReactionIds.size() == memberReactionIds.size();
    }

    public SignatureBrewProcess completeMember(String reactionId) {
        if (!owns(reactionId) || completedReactionIds.contains(reactionId)) {
            throw new IllegalArgumentException("Not an unfinished member of " + signatureId + ": " + reactionId);
        }
        Set<String> completed = new HashSet<>(completedReactionIds);
        completed.add(reactionId);
        return new SignatureBrewProcess(signatureId, result, memberReactionIds, completed);
    }
}
