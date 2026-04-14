package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.flowingsun.warpattern.cohmode.net.CohModeActionC2S;
import com.flowingsun.warpattern.cohmode.net.CohModeNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Lightweight client-side state holder for cohmode UI.
 */
public final class CohModeClientState {
    private static final Gson GSON = new Gson();
    private static CohModeModels.LobbyStateView state = new CohModeModels.LobbyStateView();

    private CohModeClientState() {
    }

    public static void applyStateJson(String json) {
        CohModeModels.LobbyStateView parsed = GSON.fromJson(json, CohModeModels.LobbyStateView.class);
        state = parsed == null ? new CohModeModels.LobbyStateView() : parsed;
    }

    public static CohModeModels.LobbyStateView state() {
        return state;
    }

    public static void sendAction(String action, JsonObject payload) {
        String json = payload == null ? "{}" : GSON.toJson(payload);
        CohModeNetwork.CHANNEL.sendToServer(new CohModeActionC2S(action, json));
    }

    public static JsonObject json() {
        return new JsonObject();
    }
}
