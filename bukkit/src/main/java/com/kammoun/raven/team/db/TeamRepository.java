package com.kammoun.raven.team.db;

import com.kammoun.raven.home.api.HomeDestination;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.team.model.TeamPermission;
import com.kammoun.raven.team.model.TeamSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TeamRepository {
    private final MySQLDBManager database;

    public TeamRepository(MySQLDBManager database) {
        this.database = database;
    }

    public Optional<TeamSnapshot> findByPlayer(UUID playerUuid) throws SQLException {
        String sql = "SELECT team_id FROM ravencore_team_members WHERE player_uuid=?";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? findById(connection, UUID.fromString(result.getString(1))) : Optional.empty();
            }
        }
    }

    public Optional<TeamSnapshot> findById(UUID teamId) throws SQLException {
        try (Connection connection = database.getConnection()) {
            return findById(connection, teamId);
        }
    }

    public CreateResult create(UUID leader, String leaderName, String teamName) throws SQLException {
        UUID teamId = UUID.randomUUID();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (existsMember(connection, leader)) return CreateResult.ALREADY_IN_TEAM;
                try (PreparedStatement team = connection.prepareStatement(
                        "INSERT INTO ravencore_teams(team_id,team_name,leader_uuid) VALUES(?,?,?)");
                     PreparedStatement member = connection.prepareStatement(
                             "INSERT INTO ravencore_team_members(player_uuid,team_id,player_name,permissions) VALUES(?,?,?,?)")) {
                    team.setString(1, teamId.toString());
                    team.setString(2, teamName);
                    team.setString(3, leader.toString());
                    team.executeUpdate();
                    member.setString(1, leader.toString());
                    member.setString(2, teamId.toString());
                    member.setString(3, leaderName);
                    member.setInt(4, TeamPermission.all());
                    member.executeUpdate();
                }
                connection.commit();
                return CreateResult.CREATED;
            } catch (SQLException e) {
                connection.rollback();
                return "23000".equals(e.getSQLState()) ? CreateResult.NAME_TAKEN : throwSql(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public InviteResult invite(UUID actor, UUID target, String targetName, int maxMembers, long expiresAt)
            throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, actor);
            if (found.isEmpty()) return InviteResult.NO_TEAM;
            TeamSnapshot team = found.get();
            TeamSnapshot.Member member = team.member(actor);
            if (!team.isLeader(actor) && (member == null || !member.has(TeamPermission.MANAGE_MEMBERS))) {
                return InviteResult.NO_PERMISSION;
            }
            if (existsMember(connection, target)) return InviteResult.ALREADY_IN_TEAM;
            if (team.members().size() >= maxMembers) return InviteResult.FULL;
            if (!invitesEnabled(connection, target)) return InviteResult.INVITES_DISABLED;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ravencore_team_invites(team_id,target_uuid,inviter_uuid,target_name,expires_at) "
                            + "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE inviter_uuid=VALUES(inviter_uuid),"
                            + "target_name=VALUES(target_name),expires_at=VALUES(expires_at)")) {
                statement.setString(1, team.id().toString());
                statement.setString(2, target.toString());
                statement.setString(3, actor.toString());
                statement.setString(4, targetName);
                statement.setLong(5, expiresAt);
                statement.executeUpdate();
            }
            return InviteResult.SENT;
        }
    }

    public JoinResult accept(UUID player, String playerName, String requestedTeam, int maxMembers, long now)
            throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (existsMember(connection, player)) return JoinResult.ALREADY_IN_TEAM;
                String sql = "SELECT i.team_id FROM ravencore_team_invites i JOIN ravencore_teams t ON t.team_id=i.team_id "
                        + "WHERE i.target_uuid=? AND i.expires_at>? "
                        + (requestedTeam == null ? "ORDER BY i.expires_at DESC LIMIT 1" : "AND LOWER(t.team_name)=LOWER(?) LIMIT 1");
                UUID teamId;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, player.toString());
                    statement.setLong(2, now);
                    if (requestedTeam != null) statement.setString(3, requestedTeam);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return JoinResult.NO_INVITE;
                        teamId = UUID.fromString(result.getString(1));
                    }
                }
                if (memberCount(connection, teamId) >= maxMembers) return JoinResult.FULL;
                try (PreparedStatement member = connection.prepareStatement(
                        "INSERT INTO ravencore_team_members(player_uuid,team_id,player_name,permissions) VALUES(?,?,?,?)");
                     PreparedStatement clear = connection.prepareStatement(
                             "DELETE FROM ravencore_team_invites WHERE target_uuid=?")) {
                    member.setString(1, player.toString());
                    member.setString(2, teamId.toString());
                    member.setString(3, playerName);
                    member.setInt(4, TeamPermission.VISIT_HOME.bit() | TeamPermission.USE_TEAM_CHAT.bit());
                    member.executeUpdate();
                    clear.setString(1, player.toString());
                    clear.executeUpdate();
                }
                connection.commit();
                return JoinResult.JOINED;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean deny(UUID player, String requestedTeam) throws SQLException {
        String sql = requestedTeam == null
                ? "DELETE FROM ravencore_team_invites WHERE target_uuid=?"
                : "DELETE i FROM ravencore_team_invites i JOIN ravencore_teams t ON t.team_id=i.team_id "
                + "WHERE i.target_uuid=? AND LOWER(t.team_name)=LOWER(?)";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.toString());
            if (requestedTeam != null) statement.setString(2, requestedTeam);
            return statement.executeUpdate() > 0;
        }
    }

    public MutationResult leave(UUID player) throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, player);
            if (found.isEmpty()) return MutationResult.NO_TEAM;
            if (found.get().isLeader(player)) return MutationResult.LEADER_CANNOT_LEAVE;
            deleteMember(connection, player);
            return MutationResult.SUCCESS;
        }
    }

    public MutationResult kick(UUID actor, UUID target) throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, actor);
            if (found.isEmpty()) return MutationResult.NO_TEAM;
            TeamSnapshot team = found.get();
            TeamSnapshot.Member actorMember = team.member(actor);
            TeamSnapshot.Member targetMember = team.member(target);
            if (targetMember == null) return MutationResult.NOT_MEMBER;
            if (team.isLeader(target) || actor.equals(target)) return MutationResult.NO_PERMISSION;
            if (!team.isLeader(actor) && (actorMember == null || !actorMember.has(TeamPermission.MANAGE_MEMBERS)
                    || targetMember.has(TeamPermission.MANAGE_MEMBERS))) return MutationResult.NO_PERMISSION;
            deleteMember(connection, target);
            return MutationResult.SUCCESS;
        }
    }

    public MutationResult disband(UUID actor) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ravencore_teams WHERE leader_uuid=?")) {
            statement.setString(1, actor.toString());
            return statement.executeUpdate() > 0 ? MutationResult.SUCCESS : MutationResult.NO_PERMISSION;
        }
    }

    public MutationResult setHome(UUID actor, HomeDestination home) throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, actor);
            if (found.isEmpty()) return MutationResult.NO_TEAM;
            TeamSnapshot team = found.get();
            TeamSnapshot.Member member = team.member(actor);
            if (!team.isLeader(actor) && (member == null || !member.has(TeamPermission.EDIT_HOME))) {
                return MutationResult.NO_PERMISSION;
            }
            String sql = home == null
                    ? "UPDATE ravencore_teams SET home_server=NULL,home_world=NULL,home_x=NULL,home_y=NULL,"
                    + "home_z=NULL,home_yaw=NULL,home_pitch=NULL WHERE team_id=?"
                    : "UPDATE ravencore_teams SET home_server=?,home_world=?,home_x=?,home_y=?,home_z=?,"
                    + "home_yaw=?,home_pitch=? WHERE team_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (home != null) {
                    statement.setString(index++, home.serverId());
                    statement.setString(index++, home.world());
                    statement.setDouble(index++, home.x());
                    statement.setDouble(index++, home.y());
                    statement.setDouble(index++, home.z());
                    statement.setFloat(index++, home.yaw());
                    statement.setFloat(index++, home.pitch());
                }
                statement.setString(index, team.id().toString());
                statement.executeUpdate();
            }
            return MutationResult.SUCCESS;
        }
    }

    public MutationResult togglePvp(UUID actor) throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, actor);
            if (found.isEmpty()) return MutationResult.NO_TEAM;
            TeamSnapshot team = found.get();
            TeamSnapshot.Member member = team.member(actor);
            if (!team.isLeader(actor) && (member == null || !member.has(TeamPermission.TOGGLE_PVP))) {
                return MutationResult.NO_PERMISSION;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ravencore_teams SET pvp_enabled=NOT pvp_enabled WHERE team_id=?")) {
                statement.setString(1, team.id().toString());
                statement.executeUpdate();
            }
            return MutationResult.SUCCESS;
        }
    }

    public MutationResult setPermission(UUID actor, UUID target, TeamPermission permission, boolean enabled)
            throws SQLException {
        try (Connection connection = database.getConnection()) {
            Optional<TeamSnapshot> found = findByPlayer(connection, actor);
            if (found.isEmpty()) return MutationResult.NO_TEAM;
            TeamSnapshot team = found.get();
            if (!team.isLeader(actor) || team.isLeader(target) || team.member(target) == null) {
                return MutationResult.NO_PERMISSION;
            }
            String sql = enabled
                    ? "UPDATE ravencore_team_members SET permissions=permissions|? WHERE player_uuid=? AND team_id=?"
                    : "UPDATE ravencore_team_members SET permissions=permissions&~? WHERE player_uuid=? AND team_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, permission.bit());
                statement.setString(2, target.toString());
                statement.setString(3, team.id().toString());
                statement.executeUpdate();
            }
            return MutationResult.SUCCESS;
        }
    }

    public boolean toggleInvites(UUID player) throws SQLException {
        try (Connection connection = database.getConnection()) {
            boolean next = !invitesEnabled(connection, player);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ravencore_team_settings(player_uuid,invites_enabled) VALUES(?,?) "
                            + "ON DUPLICATE KEY UPDATE invites_enabled=VALUES(invites_enabled)")) {
                statement.setString(1, player.toString());
                statement.setBoolean(2, next);
                statement.executeUpdate();
            }
            return next;
        }
    }

    private Optional<TeamSnapshot> findByPlayer(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT team_id FROM ravencore_team_members WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? findById(connection, UUID.fromString(result.getString(1))) : Optional.empty();
            }
        }
    }

    private Optional<TeamSnapshot> findById(Connection connection, UUID teamId) throws SQLException {
        String sql = "SELECT * FROM ravencore_teams WHERE team_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teamId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                HomeDestination home = result.getString("home_server") == null ? null
                        : new HomeDestination("Team Home", result.getString("home_server"), result.getString("home_world"),
                        result.getDouble("home_x"), result.getDouble("home_y"), result.getDouble("home_z"),
                        result.getFloat("home_yaw"), result.getFloat("home_pitch"));
                return Optional.of(new TeamSnapshot(teamId, result.getString("team_name"),
                        UUID.fromString(result.getString("leader_uuid")), result.getBoolean("pvp_enabled"),
                        home, members(connection, teamId)));
            }
        }
    }

    private List<TeamSnapshot.Member> members(Connection connection, UUID teamId) throws SQLException {
        List<TeamSnapshot.Member> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid,player_name,permissions,UNIX_TIMESTAMP(joined_at)*1000 joined FROM "
                        + "ravencore_team_members WHERE team_id=? ORDER BY joined_at")) {
            statement.setString(1, teamId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) members.add(new TeamSnapshot.Member(
                        UUID.fromString(result.getString("player_uuid")), result.getString("player_name"),
                        result.getInt("permissions"), result.getLong("joined")));
            }
        }
        return List.copyOf(members);
    }

    private boolean existsMember(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM ravencore_team_members WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private int memberCount(Connection connection, UUID teamId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM ravencore_team_members WHERE team_id=?")) {
            statement.setString(1, teamId.toString());
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private boolean invitesEnabled(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT invites_enabled FROM ravencore_team_settings WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) { return !result.next() || result.getBoolean(1); }
        }
    }

    private void deleteMember(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ravencore_team_members WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    private CreateResult throwSql(SQLException e) throws SQLException {
        throw e;
    }

    public enum CreateResult { CREATED, NAME_TAKEN, ALREADY_IN_TEAM }
    public enum InviteResult { SENT, NO_TEAM, NO_PERMISSION, ALREADY_IN_TEAM, FULL, INVITES_DISABLED }
    public enum JoinResult { JOINED, NO_INVITE, ALREADY_IN_TEAM, FULL }
    public enum MutationResult { SUCCESS, NO_TEAM, NO_PERMISSION, NOT_MEMBER, LEADER_CANNOT_LEAVE }
}
