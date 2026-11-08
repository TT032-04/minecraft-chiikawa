package com.dwinovo.chiikawa.entity.brain.errand.step;

import com.dwinovo.chiikawa.entity.AbstractPet;
import com.dwinovo.chiikawa.entity.brain.errand.run.Handle;
import com.dwinovo.chiikawa.entity.brain.errand.run.Step;
import com.dwinovo.chiikawa.entity.brain.errand.run.StepStatus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.projectile.ThrownTrident;

/**
 * Goes to get the trident that was thrown, as a player would. A trident that lands is walked to, and is the pet's again
 * once the pet is by it, see {@link AbstractPet#recallTrident}, which is also what ends the wait for a loyal one that
 * comes back by itself, so that the pet only waits for it. Done once the trident is back in the hand.
 *
 * <p>A pet with a foe come close does not go on looking for it: it has the trident back in its hand at once and
 * fights, since a jab is what a foe at its side needs. The same goes for a trident that is not found in a while, and
 * for a step that is cut short: the pet is never left holding nothing.
 */
public final class Fetch implements Step {
    /** How long the pet looks for the trident before it has it back anyway, in ticks. */
    private static final int GIVES_UP_AFTER = 400;
    private static final float SPEED = 1.2F;
    /** How often the walk is set off again, since a trident that is still falling is not where it was. */
    private static final int REPATH_EVERY = 10;
    /** A foe this near is a foe to jab, and the trident is taken back to do it. */
    private static final double FOE_TOO_NEAR = Heft.JABS_WITHIN + 2.0;

    private final UUID foe;
    private long startedAt;
    private long walkedAt;

    /**
     * @param foe the creature the fight is about, which the pet turns to when it comes near
     */
    public Fetch(UUID foe) {
        this.foe = foe;
    }

    @Override
    public void begin(Handle handle) {
        startedAt = handle.now();
        walkedAt = Long.MIN_VALUE / 2;
    }

    @Override
    public StepStatus tick(Handle handle) {
        LiveHandle live = LiveHandle.of(handle);
        AbstractPet pet = live.pet();
        if (!pet.isTridentAway()) {
            return StepStatus.DONE;
        }
        Optional<ThrownTrident> trident = pet.thrownTrident();
        boolean foeAtHand = Skirmish.hunted(live, foe).filter(target -> pet.distanceTo(target) < FOE_TOO_NEAR)
            .isPresent();
        if (trident.isEmpty() || foeAtHand || handle.now() - startedAt >= GIVES_UP_AFTER) {
            pet.recallTrident();
            return StepStatus.DONE;
        }
        if (!pet.thrownTridentReturns() && handle.now() - walkedAt >= REPATH_EVERY) {
            walkedAt = handle.now();
            live.walkTo(trident.get().blockPosition(), SPEED, 1);
        }
        return StepStatus.RUNNING;
    }

    @Override
    public void release(Handle handle) {
        AbstractPet pet = LiveHandle.of(handle).pet();
        handle.stopWalking();
        if (pet.isTridentAway()) {
            pet.recallTrident();
        }
    }
}
