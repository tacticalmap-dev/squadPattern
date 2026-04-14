package com.flowingsun.warpattern.cohmode;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Common config for cohmode matchmaking and room rules.
 */
public final class CohModeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_PARTY_SIZE;
    public static final ForgeConfigSpec.IntValue INVITE_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue INVITE_EXPIRE_SECONDS;
    public static final ForgeConfigSpec.IntValue RANDOM_MIN_TEAM_SIZE;
    public static final ForgeConfigSpec.IntValue RANDOM_MAX_TEAM_SIZE;
    public static final ForgeConfigSpec.IntValue ROOM_MAX_PLAYERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("cohmode");
        MAX_PARTY_SIZE = builder
                .comment("Maximum players allowed in one random-match party")
                .defineInRange("maxPartySize", 4, 1, 16);
        INVITE_COOLDOWN_SECONDS = builder
                .comment("Invite cooldown for the same inviter target pair")
                .defineInRange("inviteCooldownSeconds", 5, 1, 60);
        INVITE_EXPIRE_SECONDS = builder
                .comment("How long an invite stays valid")
                .defineInRange("inviteExpireSeconds", 30, 5, 180);
        RANDOM_MIN_TEAM_SIZE = builder
                .comment("Minimum players per team to launch a random match")
                .defineInRange("randomMinTeamSize", 2, 1, 32);
        RANDOM_MAX_TEAM_SIZE = builder
                .comment("Maximum players per team to launch a random match")
                .defineInRange("randomMaxTeamSize", 8, 1, 64);
        ROOM_MAX_PLAYERS = builder
                .comment("Maximum players in one custom room")
                .defineInRange("roomMaxPlayers", 16, 2, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private CohModeConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static int maxPartySize() {
        return MAX_PARTY_SIZE.get();
    }

    public static int inviteCooldownMillis() {
        return INVITE_COOLDOWN_SECONDS.get() * 1000;
    }

    public static int inviteExpireMillis() {
        return INVITE_EXPIRE_SECONDS.get() * 1000;
    }

    public static int randomMinTeamSize() {
        return RANDOM_MIN_TEAM_SIZE.get();
    }

    public static int randomMaxTeamSize() {
        return Math.max(RANDOM_MAX_TEAM_SIZE.get(), randomMinTeamSize());
    }

    public static int roomMaxPlayers() {
        return ROOM_MAX_PLAYERS.get();
    }
}
