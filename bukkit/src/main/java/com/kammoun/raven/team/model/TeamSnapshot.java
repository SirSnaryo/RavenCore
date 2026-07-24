package com.kammoun.raven.team.model;

import com.kammoun.raven.home.api.HomeDestination;

import java.util.List;
import java.util.UUID;

public record TeamSnapshot(UUID id, String name, UUID leaderUuid, boolean pvpEnabled,
                           HomeDestination home, List<Member> members) {
    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public Member member(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst().orElse(null);
    }

    public record Member(UUID uuid, String lastName, int permissions, long joinedAt) {
        public boolean has(TeamPermission permission) {
            return (permissions & permission.bit()) != 0;
        }
    }
}
