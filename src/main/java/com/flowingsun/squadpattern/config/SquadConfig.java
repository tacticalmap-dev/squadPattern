package com.flowingsun.squadpattern.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SquadConfig {
    private SquadConfig() {}

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue INITIAL_VICTORY_POINTS;
    public static final ForgeConfigSpec.IntValue INITIAL_AMMO;
    public static final ForgeConfigSpec.IntValue INITIAL_OIL;
    public static final ForgeConfigSpec.DoubleValue CAPTURE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue DRAIN_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue RESOURCE_CYCLE_SECONDS;

    public static final ForgeConfigSpec.DoubleValue HUD_LEFT_X;
    public static final ForgeConfigSpec.DoubleValue HUD_RIGHT_X;
    public static final ForgeConfigSpec.DoubleValue HUD_TOP_Y;
    public static final ForgeConfigSpec.DoubleValue HUD_CENTER_Y;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        INITIAL_VICTORY_POINTS = common.comment("Initial victory points for each side in every match")
                .defineInRange("initialVictoryPoints", 500, 1, 100000);
        INITIAL_AMMO = common.comment("Initial team ammo for each side in every match")
                .defineInRange("initialAmmo", 0, 0, 1000000);
        INITIAL_OIL = common.comment("Initial team oil for each side in every match")
                .defineInRange("initialOil", 0, 0, 1000000);
        CAPTURE_SECONDS = common.comment("Seconds required for 1 player advantage to fully capture a neutral point")
                .defineInRange("captureSeconds", 15.0D, 1.0D, 300.0D);
        DRAIN_PER_MINUTE = common.comment("Point drain applied to enemy side for each owned point per minute")
                .defineInRange("drainPerMinute", 25.0D, 0.1D, 1000.0D);
        RESOURCE_CYCLE_SECONDS = common.comment("Resource production cycle in seconds")
                .defineInRange("resourceCycleSeconds", 30, 1, 300);
        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        HUD_LEFT_X = client.comment("Left team ticket text X (0-1 of screen width)")
                .defineInRange("hudLeftX", 0.2D, 0.0D, 1.0D);
        HUD_RIGHT_X = client.comment("Right team ticket text X (0-1 of screen width)")
                .defineInRange("hudRightX", 0.8D, 0.0D, 1.0D);
        HUD_TOP_Y = client.comment("Top Y for ticket texts (0-1 of screen height)")
                .defineInRange("hudTopY", 0.08D, 0.0D, 1.0D);
        HUD_CENTER_Y = client.comment("Center Y for point status stars (0-1 of screen height)")
                .defineInRange("hudCenterY", 0.08D, 0.0D, 1.0D);
        HUD_SCALE = client.comment("HUD scale")
                .defineInRange("hudScale", 1.0D, 0.5D, 3.0D);
        CLIENT_SPEC = client.build();
    }
}
