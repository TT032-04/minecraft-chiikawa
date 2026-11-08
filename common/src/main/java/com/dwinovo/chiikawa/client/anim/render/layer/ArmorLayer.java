package com.dwinovo.chiikawa.client.anim.render.layer;

import com.dwinovo.chiikawa.Constants;
import com.dwinovo.chiikawa.anim.baked.BakedBone;
import com.dwinovo.chiikawa.anim.baked.BakedCube;
import com.dwinovo.chiikawa.anim.baked.BakedModel;
import com.dwinovo.chiikawa.client.anim.render.PetData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

/**
 * Shows that a pet is wearing armour, and no more than shows it: a pet is not a player, and its body is not made for
 * armour to be drawn round it. A helmet is a cap on the pet's head, a chestplate is a pair of gloves, boots are a
 * pair of boots on its feet, each a plain box in the colour of what it is made of, or the leather's own dye. They are
 * placed from the pet's own model, the head and the legs by the biggest cube of the bone, so that they fit whichever
 * pet wears them, and move with the bone, so that they turn with the head and swing with the legs.
 */
public final class ArmorLayer implements RenderLayer {
    /** A plain white texture, which each box is tinted from: the vertex colour is the material. */
    private static final ResourceLocation WHITE = new ResourceLocation(Constants.MOD_ID, "textures/entities/armor_hint.png");
    private static final int DEFAULT_COLOUR = 0xC8C8D0;
    private static final int LEATHER_COLOUR = 0xA06540;
    /** How far a piece stands off the body it is on, in model pixels. */
    private static final float EASE = 0.6F;
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
            cap(ctx, vc, colour(head));
        }
        if (!isEmpty(chest)) {
            glove(ctx, vc, "RightHandLocator", colour(chest));
            glove(ctx, vc, "LeftHandLocator", colour(chest));
        }
        if (!isEmpty(feet)) {
            boot(ctx, vc, "RightLeg", colour(feet));
            boot(ctx, vc, "LeftLeg", colour(feet));
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /** A cap on the top of the head: a flat box a little wider than the head, set on its top edge. */
    private void cap(RenderLayerContext ctx, VertexConsumer vc, int colour) {
        draw(ctx, vc, "Head", colour, (bone, cube) -> new float[] {
            cube.minX - EASE - bone.pivotX, cube.maxY - 1.2F - bone.pivotY, cube.minZ - EASE - bone.pivotZ,
            cube.maxX + EASE - bone.pivotX, cube.maxY + 0.9F - bone.pivotY, cube.maxZ + EASE - bone.pivotZ});
    }

    /** A boot round the foot: the bottom two pixels of the leg, a little wider than it. */
    private void boot(RenderLayerContext ctx, VertexConsumer vc, String legBone, int colour) {
        draw(ctx, vc, legBone, colour, (bone, cube) -> new float[] {
            cube.minX - EASE * 0.7F - bone.pivotX, cube.minY - 0.2F - bone.pivotY, cube.minZ - EASE * 0.7F - bone.pivotZ,
            cube.maxX + EASE * 0.7F - bone.pivotX, cube.minY + 2.2F - bone.pivotY, cube.maxZ + EASE * 0.7F - bone.pivotZ});
    }

    /** A glove: a small box at the hand, where the hand's locator is. */
    private void glove(RenderLayerContext ctx, VertexConsumer vc, String locator, int colour) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get(locator);
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        ctx.poseStack().pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, ctx.poseStack());
        float half = 1.5F;
        box(vc, ctx.poseStack().last(), colour, ctx.packedLight(), ctx.packedOverlay(), -half, -half, -half, half, half, half);
        ctx.poseStack().popPose();
    }

    /** Draws one box on a bone, sized from the bone's biggest cube, which is its body; a bone with none gets nothing. */
    private void draw(RenderLayerContext ctx, VertexConsumer vc, String boneName, int colour,
            java.util.function.BiFunction<BakedBone, BakedCube, float[]> sizeOf) {
        BakedModel model = ctx.model();
        Integer index = model.boneIndex.get(boneName);
        if (index == null || ctx.isHidden(index)) {
            return;
        }
        BakedBone bone = model.bones[index];
        BakedCube body = biggest(model, bone);
        if (body == null) {
            return;
        }
        float[] at = sizeOf.apply(bone, body);
        ctx.poseStack().pushPose();
        walker.transformToBone(model, ctx.poseBuf(), index, ctx.poseStack());
        box(vc, ctx.poseStack().last(), colour, ctx.packedLight(), ctx.packedOverlay(),
            at[0], at[1], at[2], at[3], at[4], at[5]);
        ctx.poseStack().popPose();
    }

    /** @return the biggest of the bone's own cubes, which is the head or the leg and not the ears or the toes on it */
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
