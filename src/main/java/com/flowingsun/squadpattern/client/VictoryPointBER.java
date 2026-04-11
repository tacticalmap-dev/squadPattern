package com.flowingsun.squadpattern.client;

import com.flowingsun.squadpattern.vp.VictoryPointBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * World-space label renderer for capture points.
 */
public class VictoryPointBER implements BlockEntityRenderer<VictoryPointBlockEntity> {
    public VictoryPointBER(BlockEntityRendererProvider.Context ignored) {}

    @Override
    public void render(VictoryPointBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // Skip when no local player context or point is too far for readable labels.
        if (Minecraft.getInstance().player == null || be.getLevel() == null) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos()).add(0, 1.6, 0);
        if (cam.distanceTo(center) > 48) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 1.6, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().getEntityRenderDispatcher().camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(Minecraft.getInstance().getEntityRenderDispatcher().camera.getXRot()));
        float s = 0.025F;
        poseStack.scale(-s, -s, s);

        Font font = Minecraft.getInstance().font;
        String status = buildRealtimeStatusText(be);
        int color = resolveStatusColor(be);
        drawCentered(font, poseStack, buffer, status, 0, -18, color);

        // progress bar
        float p = be.getSignedProgress();
        drawBar(poseStack, buffer, p, be.getTeamAColor(), be.getTeamBColor());
        poseStack.popPose();
    }

    private static void drawCentered(Font font, PoseStack pose, MultiBufferSource buf, String text, int x, int y, int color) {
        float tx = x - font.width(text) / 2F;
        // Render in full-bright mode so labels stay readable and emissive-looking in dark areas.
        font.drawInBatch(text, tx, y, color, false, pose.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static void drawBar(PoseStack pose, MultiBufferSource buffers, float signed, int aColor, int bColor) {
        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        Matrix4f m = pose.last().pose();
        float w = 32F;
        float h = 4F;
        float y = -6F;
        float x0 = -w / 2F;
        float x1 = w / 2F;

        rect(vc, m, x0, y, x1, y + h, 0xAA000000);
        if (signed > 0F) {
            float x = x0 + w * signed;
            rect(vc, m, x0, y, x, y + h, 0xFF000000 | aColor);
        } else if (signed < 0F) {
            float x = x1 + w * signed;
            rect(vc, m, x, y, x1, y + h, 0xFF000000 | bColor);
        }
    }

    private static String buildRealtimeStatusText(VictoryPointBlockEntity be) {
        if (be.isContested()) {
            return I18n.get("hud.squadpattern.point_status.contested");
        }

        float progress = be.getSignedProgress();
        int percent = Math.round(Math.abs(progress) * 100.0F);
        boolean towardsTeamA = progress >= 0.0F;
        String teamName = towardsTeamA ? safeTeamName(be.getTeamA()) : safeTeamName(be.getTeamB());

        if (be.isCapturing()) {
            return I18n.get("hud.squadpattern.point_status.capturing", teamName, percent);
        }

        if (be.getOwnerTeam() != null) {
            return I18n.get("hud.squadpattern.point_status.owned", safeTeamName(be.getOwnerTeam()));
        }

        if (percent > 0) {
            return I18n.get("hud.squadpattern.point_status.reverting", teamName, percent);
        }
        return I18n.get("hud.squadpattern.point_status.neutral");
    }

    private static int resolveStatusColor(VictoryPointBlockEntity be) {
        if (be.isContested()) {
            return 0xFFD966;
        }
        if (be.getOwnerTeam() == null) {
            if (be.getSignedProgress() > 0.0F) {
                return be.getTeamAColor();
            }
            if (be.getSignedProgress() < 0.0F) {
                return be.getTeamBColor();
            }
            return 0xFFFFFF;
        }
        return be.getOwnerTeam().equals(be.getTeamA()) ? be.getTeamAColor() : be.getTeamBColor();
    }

    private static String safeTeamName(String name) {
        return (name == null || name.isBlank()) ? "?" : name;
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = (argb >> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        vc.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
    }
}
