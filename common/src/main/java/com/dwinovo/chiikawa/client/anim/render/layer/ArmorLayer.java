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
 * Draws the armour a pet wears as a player's armour looks: the game's own armour texture for what it is made of. A
 * chestplate goes round the pet's body and over its arms, boots round its legs, laid over the pet's own body parts, with
 * the pattern squashed or stretched to fit, and no more than that. A helmet is not fitted to the pet's head, which it
 * does not fit: it is the player's helmet, its own size, put on top of the head and tilted back, hinged on the back of
 * the head, as far as it takes for what stands up on the pet's head, its ears and its tufts, to be under it and not
 * through it. The pet itself is drawn as it is without it, with nothing left out.
 *
 * <p>The sizes are taken from the pet's own model, so that it goes on whichever pet wears it, and each piece is on its
 * own bone, so that it turns with the head and swings with the arms and legs.
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
    private static final float TEXTURE_W = 64.0F;
    private static final float TEXTURE_H = 32.0F;
    /**
     * A broad plate on the head counts as the top of it, and not the box under it: the fur that makes the head rounder
     * than its box is as wide as this share of the box's top, or wider.
     */
    private static final float TOP_PLATE_SHARE = 0.25F;
    /** How far the helmet's back edge is in from the back of the head, and sunk into the top of it, in pixels. */
    private static final float HINGE_IN = 0.5F;
    private static final float HINGE_SUNK = 0.2F;
    /** The least and the most the helmet is tilted back, in degrees, and the room over what it has to clear. */
    private static final float MIN_PITCH = 10.0F;
    private static final float MAX_PITCH = 45.0F;
    private static final float PITCH_MARGIN = 4.0F;
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
            case HEAD -> helmetOnTop(ctx, vc, tint);
            case CHEST -> {
                on(ctx, vc, "Body", tint, 16, 16, 8, 12, 4, 1.0F, INFLATE, false);
                on(ctx, vc, "RightArm", tint, 40, 16, 4, 12, 4, 1.0F, INFLATE, false);
                on(ctx, vc, "LeftArm", tint, 40, 16, 4, 12, 4, 1.0F, INFLATE, false);
            }
            case FEET -> {
                on(ctx, vc, "RightLeg", tint, 0, 16, 4, 12, 4, BOOTS_OF_LEG, INFLATE_BOOTS, true);
                on(ctx, vc, "LeftLeg", tint, 0, 16, 4, 12, 4, BOOTS_OF_LEG, INFLATE_BOOTS, true);
            }
        }
    }

    /**
     * The player's helmet on top of the pet's head: its own size, a cube as wide as the head, whole, with its own texture
     * as the player wears it, and not fitted to the head, which it does not fit. It rests on the top of the head by its
     * back edge, which is its hinge, and is tilted back from it, the front lifted, by as much as it takes for what
     * stands up on the head to be under it: the pet's ears and the tufts and plates over its head, which are drawn as
     * ever and are not left out. The top of the head is the top of the fur, if the fur makes a plate over it.
     */
    private void helmetOnTop(RenderLayerContext ctx, VertexConsumer vc, int tint) {
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
        float headTopArea = (head.maxX - head.minX) * (head.maxZ - head.minZ);
        float topY = head.maxY;
        for (int i = 0; i < bone.cubeCount; i++) {
            BakedCube cube = model.cubes[bone.cubeStart + i];
            float area = (cube.maxX - cube.minX) * (cube.maxZ - cube.minZ);
            if (cube != head && area >= TOP_PLATE_SHARE * headTopArea) {
                topY = Math.max(topY, cube.maxY);
            }
        }
        float hingeZ = head.maxZ - HINGE_IN;
        // The tilt: the helmet's underside is a slope from its hinge up to the front, and every cube that stands above
        // the top of the head has to be under it, so the slope has to be at least as steep as the line from the hinge
        // to the cube's top at the cube's nearest side to the hinge.
        float slope = 0.0F;
        for (int i = 0; i < bone.cubeCount; i++) {
            BakedCube cube = model.cubes[bone.cubeStart + i];
            if (cube != head) {
                slope = Math.max(slope, slopeOver(cube, topY, hingeZ));
            }
        }
        for (String ear : new String[] {"LeftEar", "RightEar"}) {
            Integer earIndex = model.boneIndex.get(ear);
            if (earIndex == null) {
                continue;
            }
            BakedBone earBone = model.bones[earIndex];
            for (int i = 0; i < earBone.cubeCount; i++) {
                slope = Math.max(slope, slopeOver(model.cubes[earBone.cubeStart + i], topY, hingeZ));
            }
        }
        float pitch = (float) Math.toDegrees(Math.atan(slope)) + PITCH_MARGIN;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        float size = head.maxX - head.minX;
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, stack);
        stack.translate((head.minX + head.maxX) / 2.0F - bone.pivotX, topY - HINGE_SUNK - bone.pivotY,
            hingeZ - bone.pivotZ);
        // Back, which lifts the front: the front is the low end of Z.
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
            -size / 2.0F, 0.0F, -size, size / 2.0F, size, 0.0F, 0, 0, 8, 8, 0, 8, false);
        stack.popPose();
    }

    /** @return how steep a slope up from the hinge the cube needs to be under: how high above the top, over how far */
    private static float slopeOver(BakedCube cube, float topY, float hingeZ) {
        float rise = cube.maxY - topY;
        if (rise <= 0.05F) {
            return 0.0F;
        }
        // Its side nearest the hinge is the one the slope is the least over; a cube at the hinge is as near as 0.5.
        float run = Math.max(0.5F, hingeZ - cube.maxZ);
        return rise / run;
    }

    /**
     * One part of a piece on a bone: its biggest cube, a little bigger, wearing that part of the texture. {@code cover} is
     * how much of the cube's height it covers from the bottom, and the texture is cut the same way, so that boots are
     * the foot end of the leg's texture and not the whole leg's. {@code solidEnd} is for the underside to be drawn from
     * the part of the texture that is there, the foot end of the front, and not from the underside's own, which in the
     * game's texture is not filled in where the armour is seen from the side only, so that boots seen from below are a
     * boot's sole, and not open.
     */
    private void on(RenderLayerContext ctx, VertexConsumer vc, String boneName, int tint,
            int u, int v, int dx, int dy, int dz, float cover, float inflate, boolean solidEnd) {
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
        float low = cube.minY - inflate;
        float high = cover >= 1.0F ? cube.maxY + inflate : cube.minY + height * cover;
        int firstRow = cover >= 1.0F ? 0 : dy - usedRows;
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, stack);
        texturedBox(vc, stack.last(), tint, ctx.packedLight(), ctx.packedOverlay(),
            cube.minX - inflate - bone.pivotX, low - bone.pivotY, cube.minZ - inflate - bone.pivotZ,
            cube.maxX + inflate - bone.pivotX, high - bone.pivotY, cube.maxZ + inflate - bone.pivotZ,
            u, v, dx, dz, firstRow, usedRows, solidEnd);
        stack.popPose();
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
            int u, int v, int dx, int dz, int firstRow, int usedRows, boolean solidEnd) {
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
        // Top.
        quad(vc, pose, r, g, b, light, overlay, 0, 1, 0,
            x1, y1, z0, uFront, v, x0, y1, z0, uSide, v, x0, y1, z1, uSide, v + dz, x1, y1, z1, uFront, v + dz);
        // Bottom: its own part of the texture, or, if it is to be solid, the foot end of the front's.
        float bottomV0 = solidEnd ? Math.max(vBody, vEnd - dz) : v;
        float bottomV1 = solidEnd ? vEnd : v + dz;
        float bottomU0 = solidEnd ? uFront : uSide;
        float bottomU1 = solidEnd ? uSide : uSide + dx;
        quad(vc, pose, r, g, b, light, overlay, 0, -1, 0,
            x1, y0, z1, bottomU0, bottomV0, x0, y0, z1, bottomU1, bottomV0,
            x0, y0, z0, bottomU1, bottomV1, x1, y0, z0, bottomU0, bottomV1);
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
