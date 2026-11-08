package com.dwinovo.chiikawa.entity.brain.errand.step;

import com.dwinovo.chiikawa.entity.brain.errand.run.Handle;
import com.dwinovo.chiikawa.entity.brain.errand.run.Step;
import com.dwinovo.chiikawa.entity.brain.errand.run.StepStatus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/**
 * Waits where it stands, facing the foe, until a throw can be taken: the trident is in the hand and the foe is far
 * enough and not too far. A lancer does not back away from a foe to get the distance for a throw, as an archer does:
 * when the foe comes near, the fight is over and the jab takes it, see {@link Skirmish#whileBeyond}. Done when a throw
 * can be taken; gives up when the foe is too far off to throw at and is not coming, or when it takes too long, which
 * hands the pet over to whatever it does next, which is to go up to the foe.
 *
 * <p>Always interruptible.
 */
public final class Poise implements Step {
    /** How long the pet waits for a throw to be possible before it gives up on it, in ticks. */
    private static final int PATIENCE = 100;
    /** How far past the range of a throw the foe may be and the pet still wait for it to come. */
    private static final double SLACK = 2.0;

    private final UUID foe;
    private final double range;
    private long startedAt;

    /**
     * @param foe the creature the fight is about
     * @param range how far away the foe may be for a throw to be worth it
     */
    public Poise(UUID foe, double range) {
        this.foe = foe;
        this.range = range;
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
        if (target.isEmpty()) {
            return StepStatus.DONE;
        }
        double distance = live.pet().distanceTo(target.get());
        if (distance < Heft.JABS_WITHIN) {
            // The foe is up close: the round ends here, and the fight with it, which is for the jab.
            return StepStatus.DONE;
        }
        if (distance > range + SLACK) {
            return StepStatus.gaveUp("out_of_range");
        }
        handle.stopWalking();
        handle.lookAt(target.get());
        if (Heft.canHeft(live.pet(), target.get(), range)) {
            return StepStatus.DONE;
        }
        return handle.now() - startedAt >= PATIENCE ? StepStatus.gaveUp("waited_too_long") : StepStatus.RUNNING;
    }

    @Override
    public boolean holdsGaze() {
        return true;
    }
}
