package com.zskv.minecraftRivals;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks the ready state of a team during the event intro ready check.
 */
public class TeamReadySnapshot {
    private final String teamName;
    private final Set<UUID> members;
    private final Set<UUID> readyMembers;

    public TeamReadySnapshot(String teamName) {
        this.teamName = teamName;
        this.members = new HashSet<>();
        this.readyMembers = new HashSet<>();
    }

    public String getTeamName() {
        return teamName;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getReadyMembers() {
        return readyMembers;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void setReady(UUID uuid, boolean ready) {
        if (ready) {
            readyMembers.add(uuid);
        } else {
            readyMembers.remove(uuid);
        }
    }

    public boolean isReady(UUID uuid) {
        return readyMembers.contains(uuid);
    }

    public boolean isTeamReady() {
        return !members.isEmpty() && members.equals(readyMembers);
    }

    public int getReadyCount() {
        return readyMembers.size();
    }

    public int getTotalCount() {
        return members.size();
    }
}