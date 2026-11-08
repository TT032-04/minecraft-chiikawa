package com.dwinovo.chiikawa.entity.brain.errand.step;

import com.dwinovo.chiikawa.entity.AbstractPet;
import com.dwinovo.chiikawa.entity.anim.PetAction;
import com.dwinovo.chiikawa.entity.brain.errand.run.Handle;
import com.dwinovo.chiikawa.entity.brain.errand.run.Step;
import com.dwinovo.chiikawa.entity.brain.errand.run.StepStatus;
import java.util.UUID;

/**
 * Throws the trident that {@link Heft} took aim with: at the foe, if it is still the pet's to throw at and within
 * reach of a throw, and then the pet holds off its next attack for as long as the cooldown says. What flies, and
 * how hard, is the pet's own affair, see {@link AbstractPet#hurlTrident}: the trident in its hand stays in its hand.
 *
 * <p>An action of one tick, so it is never cut half way: an errand asked to end waits for the next tick at most, and
 * ends either before the throw or after.
 */
public final class Hurl implements Step {
    private final UUID foe;
    private final double range;
    private final int cooldownTicks;

    /**
     * @param foe the creature the fight is about
     * @param range how far away the foe may be for the throw to go
     * @param cooldownTicks how long the pet holds off its next attack
     */
    public Hurl(UUID foe, double range, int cooldownTicks) {
        this.foe = foe;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public void begin(Handle handle) {
    }

    @Override
    public StepStatus tick(Handle handle) {
        LiveHandle live = LiveHandle.of(handle);
        AbstractPet pet = live.pet();
        Skirmish.hunted(live, foe).filter(target -> Heft.canHold(pet, target, range, Heft.JABS_WITHIN))
            .ifPresent(target -> {
                handle.lookAt(target);
                handle.action(PetAction.SLASH);
                handle.swing();
                pet.hurlTrident(target);
            });
        handle.cooldownAttack(cooldownTicks);
        return StepStatus.DONE;
    }
}
