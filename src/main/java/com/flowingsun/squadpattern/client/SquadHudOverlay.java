package com.flowingsun.squadpattern.client;

import com.flowingsun.squadpattern.config.SquadConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SquadHudOverlay {
    @SubscribeEvent
    public void onHud(RenderGuiOverlayEvent.Post event) {
        if (ClientHudState.teamA == null || ClientHudState.teamB == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        float scale = SquadConfig.HUD_SCALE.get().floatValue();
        float leftX = (float) (w * SquadConfig.HUD_LEFT_X.get());
        float rightX = (float) (w * SquadConfig.HUD_RIGHT_X.get());
        float topY = (float) (h * SquadConfig.HUD_TOP_Y.get());
        float centerY = (float) (h * SquadConfig.HUD_CENTER_Y.get());
        float resourceY = topY + 12.0F;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);

        drawCentered(g, font, ClientHudState.teamA + ": " + Math.round(ClientHudState.pointsA), leftX / scale, topY / scale, ClientHudState.colorA);
        drawCentered(g, font, ClientHudState.teamB + ": " + Math.round(ClientHudState.pointsB), rightX / scale, topY / scale, ClientHudState.colorB);

        String ammoLabel = I18n.get("hud.squadpattern.ammo");
        String oilLabel = I18n.get("hud.squadpattern.oil");
        drawCentered(g, font, ammoLabel + " " + ClientHudState.ammoA + " | " + oilLabel + " " + ClientHudState.oilA, leftX / scale, resourceY / scale, ClientHudState.colorA);
        drawCentered(g, font, ammoLabel + " " + ClientHudState.ammoB + " | " + oilLabel + " " + ClientHudState.oilB, rightX / scale, resourceY / scale, ClientHudState.colorB);

        int spacing = 16;
        int total = ClientHudState.points.size();
        int start = (int) ((w / scale) * 0.5F - (total - 1) * spacing * 0.5F);
        int i = 0;
        long t = mc.level != null ? mc.level.getGameTime() : 0L;
        float phase = (t + event.getPartialTick()) * 0.22F;
        for (var p : ClientHudState.points) {
            int color = 0xFFFFFF;
            if (p.ownerTeam() != null) {
                color = p.ownerTeam().equals(ClientHudState.teamA) ? ClientHudState.colorA : ClientHudState.colorB;
            }
            int x = start + i * spacing;
            int starY = (int) (centerY / scale);
            g.drawString(font, "\u2605", x, starY, color, true);
            if (p.ownerTeam() == null && Math.abs(p.progressSigned()) > 0.01F) {
                int cx = x + 4;
                int cy = starY + 4;
                drawCaptureRing(g, cx, cy, phase);
            }
            i++;
        }

        g.pose().popPose();
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, float x, float y, int color) {
        int width = font.width(text);
        g.drawString(font, text, (int) (x - width / 2F), (int) y, color, true);
    }

    private static void drawCaptureRing(GuiGraphics g, int cx, int cy, float phase) {
        // 1) faint ring particles
        int ringCount = 20;
        float radius = 8.0F;
        for (int j = 0; j < ringCount; j++) {
            float a = (float) (Math.PI * 2.0D * j / ringCount);
            int px = Math.round(cx + (float) Math.cos(a) * radius);
            int py = Math.round(cy + (float) Math.sin(a) * radius);
            g.fill(px, py, px + 1, py + 1, 0x66FFFFFF);
        }

        // 2) rotating fan arc (brighter tail -> dim head)
        int fanCount = 7;
        float step = (float) (Math.PI / 9.0D);
        for (int j = 0; j < fanCount; j++) {
            float a = phase - j * step;
            int alpha = 220 - j * 24;
            if (alpha < 40) alpha = 40;
            int px = Math.round(cx + (float) Math.cos(a) * radius);
            int py = Math.round(cy + (float) Math.sin(a) * radius);
            int color = (alpha << 24) | 0x00E8FFFF;
            g.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
    }
}
