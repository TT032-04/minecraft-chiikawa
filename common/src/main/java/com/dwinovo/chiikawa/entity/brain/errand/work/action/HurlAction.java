package com.dwinovo.chiikawa.entity.brain.errand.work.action;

import com.dwinovo.chiikawa.Constants;
import com.dwinovo.chiikawa.entity.brain.errand.Check;
import com.dwinovo.chiikawa.entity.brain.errand.ErrandSettings;
import com.dwinovo.chiikawa.entity.brain.errand.ErrandTunings;
import com.dwinovo.chiikawa.entity.brain.errand.Offer;
import com.dwinovo.chiikawa.entity.brain.errand.Offers;
import com.dwinovo.chiikawa.entity.brain.errand.rest.RestStore;
import com.dwinovo.chiikawa.entity.brain.errand.run.Routine;
import com.dwinovo.chiikawa.entity.brain.errand.step.Fetch;
import com.dwinovo.chiikawa.entity.brain.errand.step.Heft;
import com.dwinovo.chiikawa.entity.brain.errand.step.Hurl;
import com.dwinovo.chiikawa.entity.brain.errand.step.Poise;
import com.dwinovo.chiikawa.entity.brain.errand.step.Skirmish;
import com.dwinovo.chiikawa.entity.brain.errand.view.AttackTarget;
import com.dwinovo.chiikawa.entity.brain.errand.view.PetView;
import com.dwinovo.chiikawa.entity.brain.errand.work.Work;
import com.dwinovo.chiikawa.entity.brain.errand.work.WorkAction;
import com.dwinovo.chiikawa.entity.brain.errand.work.WorkTarget;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Fights a foe that is some way off by throwing the trident at it, as a drowned does: an aim where the pet stands, a
 * throw, and fetching the trident back. There is no backing away to get the distance, as an archer has: the foe is
 * thrown at while it is far off, and jabbed when it comes near, which is the strike. Offered only for a foe at a
 * distance, and the fight ends as soon as the foe comes close, so that the next decision picks the jab. The trident
 * that flies is a copy that cannot be picked up, so nothing is ever lost.
 */
public final class HurlAction implements WorkAction<Duel, ErrandTunings.Ranged> {
    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "hurl");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Codec<ErrandTunings.Ranged> tuningCodec() {
        return ErrandTunings.Ranged.CODEC;
    }

    @Override
    public Optional<String> refuses(Optional<WorkTarget> target) {
        return WorkTarget.neededBy("hurl", target, WorkTarget.Kind.FOE);
    }

    @Override
    public Offers<Duel> offers(Work<Duel, ErrandTunings.Ranged> work, ResourceLocation errand, PetView view,
            ErrandTunings.Ranged tuning) {
        WorkTarget.Foe target = work.targetOf(WorkTarget.Foe.class);
        Optional<AttackTarget> foe = target.in(view);
        if (foe.isEmpty()) {
            return Offers.none(Check.fail(target.missing()));
        }
        if (view.attackCoolingDown()) {
            return Offers.none(Check.fail("attack_cooling_down"));
        }
        // Too near, or too far for a throw to reach, is a foe for a jab, or for the footwork of another errand.
        double distance = between(view.pos(), foe.get().where());
        if (distance < Heft.THROWS_FROM || distance > tuning.range()) {
            return Offers.none(Check.fail(target.missing()));
        }
        return Offers.of(Offer.of(new Duel(foe.get().id())));
    }

    @Override
    public Routine routine(Work<Duel, ErrandTunings.Ranged> work, Duel plan,
            ErrandSettings<ErrandTunings.Ranged> settings) {
        return Routine.of(fight(plan.foe(), settings.tuning()));
    }

    /**
     * @param foe the creature the fight is about
     * @param tuning how the pet throws
     * @return the fight itself: round after round of an aim, a throw and fetching the trident back, for as long as the
     *         sensor names the foe, it is alive and it is not up close
     */
    public static Routine.Part fight(UUID foe, ErrandTunings.Ranged tuning) {
        return Routine.repeatWhile(Skirmish.whileHunting(foe).and(Skirmish.whileBeyond(foe, Heft.JABS_WITHIN)),
            () -> new Poise(foe, tuning.range()),
            () -> new Heft(foe, tuning.range(), tuning.drawTicks()),
            () -> new Hurl(foe, tuning.range(), tuning.cooldownTicks()),
            () -> new Fetch(foe));
    }

    /** @return how far apart two places are, and no end of it when they are not in the same world */
    private static double between(GlobalPos from, GlobalPos to) {
        if (!from.dimension().equals(to.dimension())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(from.pos().distSqr(to.pos()));
    }

    @Override
    public RestStore.Kind restStore() {
        return RestStore.shared();
    }
}
