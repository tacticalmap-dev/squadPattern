package com.flowingsun.squadpattern.client;

import com.flowingsun.squadpattern.net.MatchHudSyncS2C;

import java.util.ArrayList;
import java.util.List;

public final class ClientHudState {

    public static String mapId;
    public static String teamA;
    public static int colorA;
    public static float pointsA;
    public static int ammoA;
    public static int oilA;
    public static String teamB;
    public static int colorB;
    public static float pointsB;
    public static int ammoB;
    public static int oilB;
    public static final List<MatchHudSyncS2C.PointView> points = new ArrayList<>();

    public static void apply(MatchHudSyncS2C pkt) {
        mapId = pkt.mapId;
        teamA = pkt.teamA;
        colorA = pkt.colorA;
        pointsA = pkt.pointsA;
        ammoA = pkt.ammoA;
        oilA = pkt.oilA;
        teamB = pkt.teamB;
        colorB = pkt.colorB;
        pointsB = pkt.pointsB;
        ammoB = pkt.ammoB;
        oilB = pkt.oilB;
        points.clear();
        points.addAll(pkt.points);
    }

    public static void clear() {
        mapId = null;
        teamA = null;
        colorA = 0;
        pointsA = 0F;
        ammoA = 0;
        oilA = 0;
        teamB = null;
        colorB = 0;
        pointsB = 0F;
        ammoB = 0;
        oilB = 0;
        points.clear();
    }
}
