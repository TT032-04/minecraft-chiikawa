package com.dwinovo.chiikawa.client.anim.render.layer;

import com.dwinovo.chiikawa.Constants;
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
 * Shows that a pet is wearing armour, and no more than shows it: a pet is not a player, and its body is not made for
 * armour to be drawn round it. A helmet is a star-shaped hairpin on the pet's head, a chestplate is a pair of round
 * mittens, boots are a pair of round covers over the feet, each in the colour of what it is made of, or the leather's
 * own dye. They are sized from the pet's own model, so that they fit whichever pet wears them, and move with the bone
 * they are on, so that they turn with the head and swing with the arms and legs.
 *
 * <p>Every piece is built from plain boxes of one colour: the star from a grid of cells, the rounded ones from three
 * boxes each with the corners taken off, which is a round in the game's own blocky way.
 */
public final class ArmorLayer implements RenderLayer {
    /** A plain white texture, which each box is tinted from: the vertex colour is the material. */
    private static final ResourceLocation WHITE = new ResourceLocation(Constants.MOD_ID, "textures/entities/armor_hint.png");
    private static final int DEFAULT_COLOUR = 0xC8C8D0;
    private static final int LEATHER_COLOUR = 0xA06540;
    /** The star, row by row from the top, a cell for each X. */
    private static final String[] STAR = {"..X..", ".XXX.", "XXXXX", ".XXX.", ".X.X."};
    /** How wide the star is, as a part of the head's width. */
    private static final float STAR_OF_HEAD = 0.27F;
    private final BoneTransformWalker walker = new BoneTransformWalker();

    @Override
    public void submit(RenderLayerContext ctx) {
        ItemStack head = ctx.state().get(PetData.ARMOR_HEAD);
        ItemStack chest = ctx.state().get(PetData.ARMOR_CHEST);
        ItemStack feet = ctx.state().get(PetData.ARMOR_FEET);
        boolean any = !isEmpty(head) || !isEmpty(chest) || !isEmpty(feet);
        if (!any) {
            return;
        }
        VertexConsumer vc = ctx.bufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));
        if (!isEmpty(head)) {
            hairpin(ctx, vc, colour(head));
        }
        if (!isEmpty(chest)) {
            mitten(ctx, vc, "RightArm", colour(chest));
            mitten(ctx, vc, "LeftArm", colour(chest));
        }
        if (!isEmpty(feet)) {
            footCover(ctx, vc, "RightLeg", colour(feet));
            footCover(ctx, vc, "LeftLeg", colour(feet));
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /**
     * A star hairpin on the head's top corner at the front, on the pet's left side: stood across the corner, turned
     * half way between facing the front and facing the side, so that it is seen from both.
     */
    private void hairpin(RenderLayerContext ctx, VertexConsumer vc, int colour) {
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
        float cell = (head.maxX - head.minX) * STAR_OF_HEAD / STAR[0].length();
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, stack);
        // The front is -Z in these models, and the pet's left is where its left arm is.
        stack.translate((side > 0 ? head.maxX : head.minX) - bone.pivotX, head.maxY - 0.4F - bone.pivotY,
            head.minZ - bone.pivotZ);
        stack.mulPose(Axis.YP.rotationDegrees(-45.0F * side));
        // A little off the head, so that it does not sink into it.
        stack.translate(0.0F, 0.0F, -cell * 0.9F);
        float half = STAR[0].length() * cell / 2.0F;
        float thick = cell * 1.1F;
        for (int row = 0; row < STAR.length; row++) {
            String cells = STAR[row];
            int from = 0;
            while (from < cells.length()) {
                if (cells.charAt(from) != 'X') {
                    from++;
                    continue;
                }
                int to = from;
                while (to < cells.length() && cells.charAt(to) == 'X') {
                    to++;
                }
                float y1 = half - row * cell;
                box(vc, stack.last(), colour, ctx.packedLight(), ctx.packedOverlay(),
                    from * cell - half, y1 - cell, -thick / 2.0F, to * cell - half, y1, thick / 2.0F);
                from = to;
            }
        }
        stack.popPose();
    }

    /** A round mitten at the end of the arm, big for the arm that is in it, and a little off the body. */
    private void mitten(RenderLayerContext ctx, VertexConsumer vc, String armBone, int colour) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get(armBone);
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        BakedBone bone = model.bones[index];
        BakedCube arm = biggest(model, bone);
        if (arm == null) {
            return;
        }
        float armWidth = arm.maxX - arm.minX;
        float outward = bone.pivotX < 0 ? -1.0F : 1.0F;
        float w = armWidth * 2.5F;
        float h = armWidth * 2.2F;
        float cx = (arm.minX + arm.maxX) / 2.0F + outward * 0.35F * armWidth - bone.pivotX;
        float cy = arm.minY - 0.5F * armWidth + h / 2.0F - bone.pivotY;
        float cz = (arm.minZ + arm.maxZ) / 2.0F - bone.pivotZ;
        drawOn(ctx, vc, index, colour, cx, cy, cz, w, h, w);
    }

    /** A low, round cover over the foot, wider than the leg and longer than it to the front. */
    private void footCover(RenderLayerContext ctx, VertexConsumer vc, String legBone, int colour) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get(legBone);
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        BakedBone bone = model.bones[index];
        BakedCube leg = biggest(model, bone);
        if (leg == null) {
            return;
        }
        float legWidth = leg.maxX - leg.minX;
        float legDepth = leg.maxZ - leg.minZ;
        // As high as the leg that shows under the body, and no higher.
        float top = bodyBottom(model, leg.minY + legWidth * 1.2F);
        float h = Math.max(top - leg.minY, legWidth * 1.0F);
        float w = legWidth * 2.9F;
        float d = legDepth * 3.3F;
        float cx = (leg.minX + leg.maxX) / 2.0F - bone.pivotX;
        float cy = leg.minY - 0.05F + h / 2.0F - bone.pivotY;
        float cz = (leg.minZ + leg.maxZ) / 2.0F - 0.2F * d - bone.pivotZ;
        drawOn(ctx, vc, index, colour, cx, cy, cz, w, h, d);
    }

    /** Draws one rounded box on a bone, centred where it is told to be in the bone's own space. */
    private void drawOn(RenderLayerContext ctx, VertexConsumer vc, int boneIndex, int colour,
            float cx, float cy, float cz, float w, float h, float d) {
        PoseStack stack = ctx.poseStack();
        stack.pushPose();
        walker.transformToBone(ctx.model(), ctx.poseBuf(), boneIndex, stack);
        rounded(vc, stack.last(), colour, ctx.packedLight(), ctx.packedOverlay(), cx, cy, cz, w, h, d);
        stack.popPose();
    }

    /** @return where the underside of the body is, which is where a foot cover stops; the fallback if it has none */
    private static float bodyBottom(BakedModel model, float fallback) {
        Integer index = model.boneIndex.get("Body");
        if (index == null) {
            return fallback;
        }
        BakedCube body = biggest(model, model.bones[index]);
        return body == null ? fallback : body.minY;
    }

    /** @return which way the pet's left is along the X axis, which is where its left arm is: 1, or -1 */
    private static float leftSide(BakedModel model) {
        Integer left = model.boneIndex.get("LeftArm");
        return left != null && model.bones[left].pivotX < 0 ? -1.0F : 1.0F;
    }

    /** @return the biggest of the bone's own cubes, which is the head or the arm or the leg, and not what is on it */
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

    /** The colour of what the armour is made of: a leather one by its dye, any other by its material. */
    private static int colour(ItemStack stack) {
        if (stack.getItem() instanceof DyeableLeatherItem dye) {
            return dye.hasCustomColor(stack) ? dye.getColor(stack) : LEATHER_COLOUR;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return switch (armor.getMaterial().getName()) {
                case "chainmail" -> 0xB8B8C0;
                case "iron" -> 0xDADADA;
                case "gold" -> 0xF6D24A;
                case "diamond" -> 0x4BEDD6;
                case "netherite" -> 0x4B4247;
                case "turtle" -> 0x4FA03F;
                default -> DEFAULT_COLOUR;
            };
        }
        return DEFAULT_COLOUR;
    }

    /**
     * A box with its corners taken off, round in the game's blocky way: three boxes, each the whole size in one
     * direction and short of it by the corner in the other two, crossed at the middle.
     */
    private static void rounded(VertexConsumer vc, PoseStack.Pose pose, int colour, int light, int overlay,
            float cx, float cy, float cz, float w, float h, float d) {
        float c = Math.min(Math.min(w, h), d) * 0.3F;
        float hw = w / 2.0F;
        float hh = h / 2.0F;
        float hd = d / 2.0F;
        box(vc, pose, colour, light, overlay, cx - hw, cy - hh + c, cz - hd + c, cx + hw, cy + hh - c, cz + hd - c);
        box(vc, pose, colour, light, overlay, cx - hw + c, cy - hh, cz - hd + c, cx + hw - c, cy + hh, cz + hd - c);
        box(vc, pose, colour, light, overlay, cx - hw + c, cy - hh + c, cz - hd, cx + hw - c, cy + hh - c, cz + hd);
    }

    /** One box, six faces, in the pose's own space. The texture is one plain colour, so the UV is its middle. */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, int colour, int light, int overlay,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        int r = colour >> 16 & 0xFF;
        int g = colour >> 8 & 0xFF;
        int b = colour & 0xFF;
        face(vc, pose, r, g, b, light, overlay, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        face(vc, pose, r, g, b, light, overlay, 0, 0, 1, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        face(vc, pose, r, g, b, light, overlay, -1, 0, 0, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        face(vc, pose, r, g, b, light, overlay, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        face(vc, pose, r, g, b, light, overlay, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(vc, pose, r, g, b, light, overlay, 0, -1, 0, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, int r, int g, int b, int light, int overlay,
            float nx, float ny, float nz, float... corners) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(pose.pose(), corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
                .color(r, g, b, 255)
                .uv(0.5F, 0.5F)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        }
    }
}
