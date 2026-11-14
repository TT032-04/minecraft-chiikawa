package com.dwinovo.chiikawa.client.anim.render.layer;

import com.dwinovo.chiikawa.anim.baked.BakedBone;
import com.dwinovo.chiikawa.anim.baked.BakedCube;
import com.dwinovo.chiikawa.anim.baked.BakedModel;
import com.dwinovo.chiikawa.client.anim.render.PetData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the armour a pet wears as a player's armour looks: the game's own armour texture for what it is made of, laid
 * over the pet's own body parts. A helmet goes round the head, a chestplate round the body and over the arms, boots
 * round the legs, as they do on a player, where the texture is open the pet shows through, and it is the same
 * texture, so the same armour, as the player's. The pet's body parts are not a player's shape, so the pattern is
 * squashed or stretched to fit, and no more than that; the sizes are taken from the pet's own model, so that it
 * fits whichever pet wears it, and each piece is on its own bone, so that it turns with the head and swings with
 * the arms and legs.
 */
public final class ArmorLayer implements RenderLayer {
    private static final int LEATHER_COLOUR = 0xA06540;
    /** The materials the game has an armour texture for; any other armour is drawn as iron. */
    private static final java.util.Set<String> VANILLA_MATERIALS =
        java.util.Set.of("leather", "chainmail", "iron", "gold", "diamond", "netherite", "turtle");
    /** How far the armour stands off the body it is round, in model pixels. */
    private static final float INFLATE = 0.3F;
    /** The same for boots, which are thin on a pet's thin legs and wider for it. */
    private static final float INFLATE_BOOTS = 0.45F;
    /** How much of a leg the boots cover, from the foot up, for the boots to be where a boot is. */
    private static final float BOOTS_OF_LEG = 0.5F;
    /**
     * How much of the head the helmet covers, from the top down: a cap over the top and sides of the head, with the
     * pet's face below it. The helmet's own front is half band and half open, so the face would be half hidden by it
     * if it went round the whole head.
     */
    private static final float HELMET_OF_HEAD = 0.45F;
    /** The helmet stands a little off the head, as much as a helmet over hair does, and no more, so that it sits on it. */
    private static final float INFLATE_HEAD = 0.45F;
    /** The hat's width and depth as a part of the head's: a little less, so that it sits on the head and does not cover it. */
    private static final float HAT_OF_HEAD = 0.95F;
    /** How tall the hat is, in pixels, and how far it is sunk into the top of the head. */
    private static final float HAT_HEIGHT = 2.0F;
    private static final float HAT_SINK = 0.4F;
    /** How far the hat is set back from the middle of the head, so that its front clears the pet's brow. */
    private static final float HAT_BACK = 0.3F;
    /** How far the hat is tilted to the pet's left side, and back, in degrees: a hat put on, not a helmet fitted. */
    private static final float HAT_ROLL = 12.0F;
    private static final float HAT_PITCH = 8.0F;
    /** How deep into the head's top the hat is taken to reach, for the fur that comes up into it to be left out. */
    private static final float HAT_DEPTH = 1.4F;
    /** The least a helmet covers of the head from the top, in pixels, whatever the eyebrows are. */
    private static final float MIN_CAP = 1.2F;
    /** How much smaller the top step of a helmet is, on each side, and how high it is, for a rounded top. */
    private static final float DOME_STEP = 0.5F;
    /** The parts of a pet's face that are highest on it: the helmet's edge is set above them. */
    private static final String[] EYE_BONES = {"Eyebrow", "RightEyebrow", "LeftEyebrow", "Eyelid", "RightEyelid",
        "LeftEyelid", "Rightlight", "Leftlight", "HappyEyes", "CryEyes"};
    /**
     * The pets whose ears are big enough to stay in view with a helmet on: the helmet is open for them, and shows them
     * coming out of it. The ears of the rest are left out of the picture when they wear one.
     */
    public static final java.util.Set<String> BIG_EARS = java.util.Set.of("furuhonya", "usagi", "momonga");
    /** How much wider than an ear its opening in the helmet is, on each side. */
    private static final float EAR_ROOM = 0.25F;

    /**
     * How a helmet fits the head it is on: where its lower edge is, which is above the eyes and the brows of the pet and
     * not at a share of its head, a hole in its top for each big ear, and how high its top has to go to meet the ears.
     */
    private record Fit(float[][] holes, float meetsEarsAt, float lowEdge) {
    }
    private static final float TEXTURE_W = 64.0F;
    private static final float TEXTURE_H = 32.0F;
    private final BoneTransformWalker walker = new BoneTransformWalker();

    /** Which piece, since each is a different part of the texture on different bones. */
    private enum Piece {
        HEAD, CHEST, FEET
    }

    @Override
    public void submit(RenderLayerContext ctx) {
        ItemStack head = ctx.state().get(PetData.ARMOR_HEAD);
        ItemStack chest = ctx.state().get(PetData.ARMOR_CHEST);
        ItemStack feet = ctx.state().get(PetData.ARMOR_FEET);
        if (!isEmpty(head)) {
            wear(ctx, head, Piece.HEAD);
        }
        if (!isEmpty(chest)) {
            wear(ctx, chest, Piece.CHEST);
        }
        if (!isEmpty(feet)) {
            wear(ctx, feet, Piece.FEET);
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /**
     * One piece of armour on the pet, in the texture for what it is made of. Leather is the leather texture in its dye,
     * and the overlay over it, as it is on a player; armour of a material the game has no texture for is iron.
     */
    private void wear(RenderLayerContext ctx, ItemStack stack, Piece piece) {
        String material = "iron";
        int tint = 0xFFFFFF;
        boolean leather = false;
        if (stack.getItem() instanceof ArmorItem armor && VANILLA_MATERIALS.contains(armor.getMaterial().getName())) {
            material = armor.getMaterial().getName();
        }
        if (stack.getItem() instanceof DyeableLeatherItem dye) {
            material = "leather";
            leather = true;
            tint = dye.hasCustomColor(stack) ? dye.getColor(stack) : LEATHER_COLOUR;
        }
        coat(ctx, piece, armorTexture(material, ""), tint);
        if (leather) {
            coat(ctx, piece, armorTexture("leather", "_overlay"), 0xFFFFFF);
        }
    }

    /** The armour texture: helmet, chestplate and boots are all in the first of the two for each material. */
    private static ResourceLocation armorTexture(String material, String suffix) {
        return new ResourceLocation("textures/models/armor/" + material + "_layer_1" + suffix + ".png");
    }

    /**
     * One coat of one piece. Each part is the player's part of the texture: the head 8 by 8 by 8 pixels from (0, 0), the
     * body 8 by 12 by 4 from (16, 16), an arm 4 by 12 by 4 from (40, 16), a leg the same from (0, 16), of which the boots
     * are the foot end.
     */
    private void coat(RenderLayerContext ctx, Piece piece, ResourceLocation texture, int tint) {
        VertexConsumer vc = ctx.bufferSource().getBuffer(RenderType.armorCutoutNoCull(texture));
        switch (piece) {
            case HEAD -> hat(ctx, vc, tint);
            case CHEST -> {
                on(ctx, vc, "Body", tint, 16, 16, 8, 12, 4, 1.0F, INFLATE, false, false, null);
                on(ctx, vc, "RightArm", tint, 40, 16, 4, 12, 4, 1.0F, INFLATE, false, false, null);
                on(ctx, vc, "LeftArm", tint, 40, 16, 4, 12, 4, 1.0F, INFLATE, false, false, null);
            }
            case FEET -> {
                on(ctx, vc, "RightLeg", tint, 0, 16, 4, 12, 4, BOOTS_OF_LEG, INFLATE_BOOTS, false, true, null);
                on(ctx, vc, "LeftLeg", tint, 0, 16, 4, 12, 4, BOOTS_OF_LEG, INFLATE_BOOTS, false, true, null);
            }
        }
    }

    /**
     * One part of a piece on a bone: its biggest cube, a little bigger, wearing that part of the texture. {@code cover} is
     * how much of the cube's height it covers, from the bottom or, with {@code fromTop}, from the top, and the texture
     * is cut the same way, so that boots are the foot end of the leg's texture and a helmet the top of the head's, and
     * not the whole of either. {@code solidEnd} is for the underside to be drawn from the part of the texture that is
     * there, the foot end of the front, and not from the underside's own, which in the game's texture is not filled in
     * where the armour is seen from the side only, so that boots seen from below are a boot's sole, and not open.
     */
    private void on(RenderLayerContext ctx, VertexConsumer vc, String boneName, int tint,
            int u, int v, int dx, int dy, int dz, float cover, float inflate, boolean fromTop, boolean solidEnd,
            Fit fit) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get(boneName);
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        BakedBone bone = model.bones[index];
        BakedCube cube = biggest(model, bone);
        if (cube == null) {
            return;
        }
        float height = cube.maxY - cube.minY;
        int usedRows = cover >= 1.0F ? dy : Math.max(1, Math.round(dy * cover));
        float low;
        float high;
        int firstRow;
        if (cover >= 1.0F) {
            low = cube.minY - inflate;
            high = cube.maxY + inflate;
            firstRow = 0;
        } else if (fromTop) {
            low = cube.maxY - height * cover;
            high = cube.maxY + inflate;
            firstRow = 0;
        } else {
            low = cube.minY - inflate;
            high = cube.minY + height * cover;
            firstRow = dy - usedRows;
        }
        if (fit != null && fromTop && !Float.isNaN(fit.lowEdge())) {
            // The helmet's edge is where the pet's face is, not a share of its head.
            low = fit.lowEdge();
            usedRows = Math.max(1, Math.min(dy, Math.round(dy * (cube.maxY - low) / height)));
        }
        if (fit != null && !Float.isNaN(fit.meetsEarsAt())) {
            high = Math.max(high, fit.meetsEarsAt());
        }
        float[][] holes = fit == null ? new float[0][] : holesRelativeTo(fit.holes(), bone);
        // A helmet with no ears to let out has a rounded top: one step smaller on top of the rest.
        boolean dome = fromTop && holes.length == 0 && high - low > 2.0F * DOME_STEP + 0.2F;
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, stack);
        float bodyTop = dome ? high - DOME_STEP : high;
        texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
            cube.minX - inflate - bone.pivotX, low - bone.pivotY, cube.minZ - inflate - bone.pivotZ,
            cube.maxX + inflate - bone.pivotX, bodyTop - bone.pivotY, cube.maxZ + inflate - bone.pivotZ,
            u, v, dx, dz, firstRow, usedRows, solidEnd, holes.length == 0 ? null : holes);
        if (dome) {
            float inset = DOME_STEP;
            texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
                cube.minX - inflate + inset - bone.pivotX, bodyTop - bone.pivotY,
                cube.minZ - inflate + inset - bone.pivotZ,
                cube.maxX + inflate - inset - bone.pivotX, high - bone.pivotY,
                cube.maxZ + inflate - inset - bone.pivotZ,
                u, v, dx, dz, 0, 1, false, null);
        }
        stack.popPose();
    }

    /**
     * The helmet as a hat sat on the pet's head: the helmet's own texture, on a box a little smaller than the head's top
     * with a smaller step on top of it, set on the top of the head and tilted a little to one side and back, as a hat put
     * on is and a helmet fitted is not. It does not go down the sides of the head, so the pet's face and what hangs at its
     * sides are as they are. The big ears of the pets that have them stand up through it.
     */
    private void hat(RenderLayerContext ctx, VertexConsumer vc, int tint) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get("Head");
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        BakedBone bone = model.bones[index];
        BakedCube head = biggest(model, bone);
        if (head == null) {
            return;
        }
        float side = leftSide(model);
        float w = (head.maxX - head.minX) * HAT_OF_HEAD;
        float d = (head.maxZ - head.minZ) * HAT_OF_HEAD;
        float centreX = (head.minX + head.maxX) / 2.0F - bone.pivotX;
        float centreZ = (head.minZ + head.maxZ) / 2.0F - bone.pivotZ + HAT_BACK;
        float centreY = head.maxY - HAT_SINK + HAT_HEIGHT / 2.0F - bone.pivotY;
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, stack);
        stack.translate(centreX, centreY, centreZ);
        // Down on the pet's left, and back: a rise at the front, which is the low end of Z.
        stack.mulPose(Axis.ZP.rotationDegrees(-side * HAT_ROLL));
        stack.mulPose(Axis.XP.rotationDegrees(HAT_PITCH));
        float bottom = -HAT_HEIGHT / 2.0F;
        float step = bottom + HAT_HEIGHT - DOME_STEP;
        texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
            -w / 2.0F, bottom, -d / 2.0F, w / 2.0F, step, d / 2.0F, 0, 0, 8, 8, 0, 3, false, null);
        texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
            -w / 2.0F + DOME_STEP, step, -d / 2.0F + DOME_STEP, w / 2.0F - DOME_STEP, HAT_HEIGHT / 2.0F,
            d / 2.0F - DOME_STEP, 0, 0, 8, 8, 0, 1, false, null);
        stack.popPose();
    }

    /** @return which way the pet's left is along the X axis, which is where its left arm is: 1, or -1 */
    private static float leftSide(BakedModel model) {
        Integer left = model.boneIndex.get("LeftArm");
        return left != null && model.bones[left].pivotX < 0 ? -1.0F : 1.0F;
    }

    /** @return the holes, which are given where the model puts them, as they are in the bone's own space */
    private static float[][] holesRelativeTo(float[][] holes, BakedBone bone) {
        if (holes == null) {
            return new float[0][];
        }
        float[][] relative = new float[holes.length][];
        for (int i = 0; i < holes.length; i++) {
            relative[i] = new float[] {holes[i][0] - bone.pivotX, holes[i][1] - bone.pivotX,
                holes[i][2] - bone.pivotZ, holes[i][3] - bone.pivotZ};
        }
        return relative;
    }

    /**
     * What a helmet is open for on the pets with big ears: a hole in the top of it for each ear, a little wider than the
     * ear, and the height the top must go to for the ears to come out of it and not float above it, since the fur that
     * joins them to the head is left out under a helmet. Nothing for a pet whose ears are left out.
     */
    private static Fit headFit(RenderLayerContext ctx) {
        BakedModel model = ctx.model();
        float lowEdge = lowEdge(model);
        if (!BIG_EARS.contains(ctx.state().modelKey.getPath())) {
            return new Fit(new float[0][], Float.NaN, lowEdge);
        }
        java.util.List<float[]> holes = new java.util.ArrayList<>();
        float lowest = Float.MAX_VALUE;
        for (String name : new String[] {"LeftEar", "RightEar"}) {
            Integer index = model.boneIndex.get(name);
            if (index == null) {
                continue;
            }
            BakedBone ear = model.bones[index];
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < ear.cubeCount; i++) {
                BakedCube cube = model.cubes[ear.cubeStart + i];
                minX = Math.min(minX, cube.minX);
                maxX = Math.max(maxX, cube.maxX);
                minZ = Math.min(minZ, cube.minZ);
                maxZ = Math.max(maxZ, cube.maxZ);
                lowest = Math.min(lowest, cube.minY);
            }
            if (ear.cubeCount > 0) {
                holes.add(new float[] {minX - EAR_ROOM, maxX + EAR_ROOM, minZ - EAR_ROOM, maxZ + EAR_ROOM});
            }
        }
        if (holes.isEmpty()) {
            return new Fit(new float[0][], Float.NaN, lowEdge);
        }
        // A little into the ear, so that the helmet meets it.
        return new Fit(holes.toArray(new float[0][]), lowest + 0.3F, lowEdge);
    }

    /**
     * @return where the hat's underside reaches down to on this pet, in the model's own height: the top of the head less
     *         the hat's depth. The renderer takes it to know what of the pet's hair comes up into the hat, and leaves
     *         that out. NaN for a model with no head
     */
    public static float lowEdge(BakedModel model) {
        Integer headIndex = model.boneIndex.get("Head");
        if (headIndex == null) {
            return Float.NaN;
        }
        BakedCube head = biggest(model, model.bones[headIndex]);
        return head == null ? Float.NaN : head.maxY - HAT_DEPTH;
    }

    /** @return the biggest of the bone's own cubes, which is the head or the body or the leg, and not what is on it */
    private static BakedCube biggest(BakedModel model, BakedBone bone) {
        BakedCube found = null;
        float most = 0.0F;
        for (int i = 0; i < bone.cubeCount; i++) {
            BakedCube cube = model.cubes[bone.cubeStart + i];
            float volume = (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
            if (found == null || volume > most) {
                found = cube;
                most = volume;
            }
        }
        return found;
    }

    /**
     * A box wearing the texture of a player's body part: the cube of {@code dx} wide and {@code dz} deep the texture is
     * laid out for, from {@code (u, v)}, front, back, sides, top and bottom as in the game. Only {@code usedRows} rows of
     * its sides, from the {@code firstRow}th, are used, for a piece that covers part of the height. Stretched to the box
     * it is on, which the pets' bodies are not the player's shape for, so the pattern is squashed and no more.
     */
    private static void texturedBox(VertexConsumer vc, PoseStack.Pose pose, int tint, int light, int overlay,
            float x0, float y0, float z0, float x1, float y1, float z1,
            int u, int v, int dx, int dz, int firstRow, int usedRows, boolean solidEnd, float[][] topHoles) {
        int r = tint >> 16 & 0xFF;
        int g = tint >> 8 & 0xFF;
        int b = tint & 0xFF;
        float uFront = u + dz;
        float uSide = u + dz + dx;
        float uBack = u + 2 * dz + dx;
        float vBody = v + dz + firstRow;
        float vEnd = vBody + usedRows;
        // Front, at the low end of Z, seen from there: the pet's left on the viewer's left.
        quad(vc, pose, r, g, b, light, overlay, 0, 0, -1,
            x1, y1, z0, uFront, vBody, x0, y1, z0, uSide, vBody, x0, y0, z0, uSide, vEnd, x1, y0, z0, uFront, vEnd);
        // Back.
        quad(vc, pose, r, g, b, light, overlay, 0, 0, 1,
            x0, y1, z1, uBack, vBody, x1, y1, z1, uBack + dx, vBody, x1, y0, z1, uBack + dx, vEnd,
            x0, y0, z1, uBack, vEnd);
        // The sides.
        quad(vc, pose, r, g, b, light, overlay, -1, 0, 0,
            x0, y1, z0, u, vBody, x0, y1, z1, uFront, vBody, x0, y0, z1, uFront, vEnd, x0, y0, z0, u, vEnd);
        quad(vc, pose, r, g, b, light, overlay, 1, 0, 0,
            x1, y1, z1, uSide, vBody, x1, y1, z0, uBack, vBody, x1, y0, z0, uBack, vEnd, x1, y0, z1, uSide, vEnd);
        // Top, with the holes in it that it has.
        top(vc, pose, r, g, b, light, overlay, x0, x1, y1, z0, z1, uFront, uSide, v, dz, topHoles);
        // Bottom: its own part of the texture, or, if it is to be solid, the foot end of the front's.
        float bottomV0 = solidEnd ? Math.max(vBody, vEnd - dz) : v;
        float bottomV1 = solidEnd ? vEnd : v + dz;
        float bottomU0 = solidEnd ? uFront : uSide;
        float bottomU1 = solidEnd ? uSide : uSide + dx;
        quad(vc, pose, r, g, b, light, overlay, 0, -1, 0,
            x1, y0, z1, bottomU0, bottomV0, x0, y0, z1, bottomU1, bottomV0,
            x0, y0, z0, bottomU1, bottomV1, x1, y0, z0, bottomU0, bottomV1);
    }

    /**
     * The top face, in as many pieces as it takes to leave the holes out of it: bands across it, and in a band that has a
     * hole, the piece before the hole and the piece after. Each piece wears its own part of the texture's top, so that
     * the pattern is the same as it would be without the holes.
     */
    private static void top(VertexConsumer vc, PoseStack.Pose pose, int r, int g, int b, int light, int overlay,
            float x0, float x1, float y, float z0, float z1, float uFront, float uSide, float v, float dz,
            float[][] holes) {
        java.util.List<float[]> pieces = new java.util.ArrayList<>();
        if (holes == null || holes.length == 0) {
            pieces.add(new float[] {x0, x1, z0, z1});
        } else {
            java.util.TreeSet<Float> cuts = new java.util.TreeSet<>();
            cuts.add(x0);
            cuts.add(x1);
            for (float[] hole : holes) {
                cuts.add(Math.max(x0, Math.min(x1, hole[0])));
                cuts.add(Math.max(x0, Math.min(x1, hole[1])));
            }
            Float[] xs = cuts.toArray(new Float[0]);
            for (int i = 0; i + 1 < xs.length; i++) {
                float bx0 = xs[i];
                float bx1 = xs[i + 1];
                if (bx1 - bx0 < 1.0E-4F) {
                    continue;
                }
                float[] open = null;
                for (float[] hole : holes) {
                    if (hole[0] <= bx0 + 1.0E-4F && hole[1] >= bx1 - 1.0E-4F) {
                        open = hole;
                    }
                }
                if (open == null) {
                    pieces.add(new float[] {bx0, bx1, z0, z1});
                } else {
                    float hz0 = Math.max(z0, Math.min(z1, open[2]));
                    float hz1 = Math.max(z0, Math.min(z1, open[3]));
                    if (hz0 - z0 > 1.0E-4F) {
                        pieces.add(new float[] {bx0, bx1, z0, hz0});
                    }
                    if (z1 - hz1 > 1.0E-4F) {
                        pieces.add(new float[] {bx0, bx1, hz1, z1});
                    }
                }
            }
        }
        for (float[] p : pieces) {
            float uA = uFront + (x1 - p[1]) / (x1 - x0) * (uSide - uFront);
            float uB = uFront + (x1 - p[0]) / (x1 - x0) * (uSide - uFront);
            float vA = v + (p[2] - z0) / (z1 - z0) * dz;
            float vB = v + (p[3] - z0) / (z1 - z0) * dz;
            quad(vc, pose, r, g, b, light, overlay, 0, 1, 0,
                p[1], y, p[2], uA, vA, p[0], y, p[2], uB, vA, p[0], y, p[3], uB, vB, p[1], y, p[3], uA, vB);
        }
    }

    /** A face of four corners, each given as x, y, z and its place in the texture in pixels. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int r, int g, int b, int light, int overlay,
            float nx, float ny, float nz, float... corners) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(pose.pose(), corners[i * 5], corners[i * 5 + 1], corners[i * 5 + 2])
                .color(r, g, b, 255)
                .uv(corners[i * 5 + 3] / TEXTURE_W, corners[i * 5 + 4] / TEXTURE_H)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        }
    }
}
