package com.dwinovo.chiikawa.entity.brain.errand.step;

import com.dwinovo.chiikawa.entity.AbstractPet;
import com.dwinovo.chiikawa.entity.brain.PetTargeting;
import com.dwinovo.chiikawa.entity.brain.errand.run.Handle;
import com.dwinovo.chiikawa.entity.brain.errand.run.Step;
import com.dwinovo.chiikawa.entity.brain.errand.run.StepStatus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Weighs the trident in the hand and takes aim for a while, standing where it is and facing the foe: a lancer does not
 * back away from a foe to throw at it, it throws from where it stands and jabs the foe that comes near. Done when the
 * aim is taken, or when it cannot be held any longer, because the foe is gone or out of range or has come so close that
 * a jab is the better answer; {@link Hurl} throws it.
 *
 * <p>Always interruptible, and then nothing is thrown: a lancer told to sit while it aims does not throw.
 */
public final class Heft implements Step {
    /** How far off a foe has to be, centre to centre, before an errand to throw at it is offered at all. */
    public static final double THROWS_FROM = 6.0;
    /** How far off a foe has to be for the lancer to start taking aim at it. */
    public static final double AIMS_FROM = 5.0;
    /**
     * How near a foe may come, in the middle of a fight, before the lancer stops throwing and jabs it instead: the
     * foe is a little further off when the fight begins, so that a foe at the edge does not flip the pet back and
     * forth between the two.
     */
    public static final double JABS_WITHIN = 5.0;

    private final UUID foe;
    private final double range;
    private final int aimTicks;
    private long startedAt;

    /**
     * @param foe the creature the fight is about
     * @param range how far away the foe may be for a throw to be worth it
     * @param aimTicks how long the aim is taken before the tick the trident is thrown in
     */
    public Heft(UUID foe, double range, int aimTicks) {
        this.foe = foe;
        this.range = range;
        this.aimTicks = aimTicks;
    }

    /** Whether a throw at this foe can be started now: off cooldown, and the foe is far enough and within range. */
    static boolean canHeft(AbstractPet pet, LivingEntity target, double range) {
        return !pet.isTridentAway() && !pet.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
            && canHold(pet, target, range, AIMS_FROM);
    }

    /** Whether the aim can be held on this foe: it is the pet's to fight, in plain sight, and not too near or far. */
    static boolean canHold(AbstractPet pet, LivingEntity target, double range, double closest) {
        double distance = pet.distanceTo(target);
        return target.isAlive() && distance <= range && distance >= closest
            && PetTargeting.canTarget(pet, target) && pet.hasLineOfSight(target);
    }

    @Override
    public void begin(Handle handle) {
        startedAt = handle.now();
        handle.stopWalking();
    }

    @Override
    public StepStatus tick(Handle handle) {
        LiveHandle live = LiveHandle.of(handle);
        Optional<LivingEntity> target = Skirmish.hunted(live, foe);
        if (target.isEmpty() || !canHold(live.pet(), target.get(), range, JABS_WITHIN)) {
            return StepStatus.DONE;
        }
        handle.stopWalking();
        handle.lookAt(target.get());
        return handle.now() - startedAt >= aimTicks ? StepStatus.DONE : StepStatus.RUNNING;
    }

    @Override
    public boolean holdsGaze() {
        return true;
    }
}
