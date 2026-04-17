package com.flowingsun.warpattern.cohmode;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared DTOs and enums for cohmode server/client sync.
 */
public final class CohModeModels {
    private CohModeModels() {
    }

    public enum Camp {
        RED("Red (Warsaw)"),
        BLUE("Blue (NATO)");

        public final String label;

        Camp(String label) {
            this.label = label;
        }
    }

    public enum Role {
        COMMANDER("Commander"),
        RIFLEMAN("Rifleman"),
        ASSAULT("Assault"),
        SUPPORT("Support"),
        SNIPER("Sniper");

        public final String label;

        Role(String label) {
            this.label = label;
        }
    }

    public enum InviteKind {
        PARTY,
        ROOM
    }

    public static final class InviteView {
        public String id;
        public String fromName;
        public InviteKind kind;
        public String roomId;
        public long expireAtMillis;
    }

    public static final class PartyView {
        public String leaderName;
        public boolean leader;
        public int maxSize;
        public List<String> members = new ArrayList<>();
    }

    public static final class QueueView {
        public boolean queued;
        public int redQueued;
        public int blueQueued;
        public long joinedAtMillis;
    }

    public static final class MapView {
        public String mapName;
        public int recommendedMinPlayers;
        public int recommendedMaxPlayers;
        public boolean ready;
    }

    public static final class RoomMemberView {
        public String playerName;
        public Camp camp;
        public Role role;
        public boolean ready;
        public boolean host;
    }

    public static final class RoomView {
        public String roomId;
        public String hostName;
        public String mapName;
        public boolean selfHost;
        public boolean canStart;
        public List<RoomMemberView> members = new ArrayList<>();
    }

    public static final class RoomListItemView {
        public String roomId;
        public String hostName;
        public String mapName;
        public int members;
        public int maxPlayers;
    }

    public static final class BackpackItemOptionView {
        public String itemId;
        public String label;
        public int count;
    }

    public static final class BackpackSlotView {
        public int slotIndex;
        public String slotName;
        public String selectedItemId;
        public List<BackpackItemOptionView> options = new ArrayList<>();
    }

    public static final class RoleBackpackView {
        public Role role;
        public String roleName;
        public List<BackpackSlotView> slots = new ArrayList<>();
    }

    public static final class LobbyStateView {
        public String selfName;
        public Camp selectedCamp;
        public Role selectedRole;
        public PartyView party;
        public QueueView queue = new QueueView();
        public RoomView currentRoom;
        public List<RoomListItemView> rooms = new ArrayList<>();
        public List<InviteView> invites = new ArrayList<>();
        public List<MapView> maps = new ArrayList<>();
        public List<RoleBackpackView> roleBackpacks = new ArrayList<>();
        public long serverTimeMillis;
        public String statusText;
    }
}
