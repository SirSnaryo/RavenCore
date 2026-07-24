package com.kammoun.raven.team.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class TeamTable implements KTables {
    private final MySQLDBManager database;

    public TeamTable(MySQLDBManager database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ravencore_teams ("
                    + "team_id CHAR(36) PRIMARY KEY, team_name VARCHAR(24) NOT NULL UNIQUE,"
                    + "leader_uuid CHAR(36) NOT NULL UNIQUE, pvp_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "home_server VARCHAR(64), home_world VARCHAR(64), home_x DOUBLE, home_y DOUBLE, home_z DOUBLE,"
                    + "home_yaw FLOAT, home_pitch FLOAT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ravencore_team_members ("
                    + "player_uuid CHAR(36) PRIMARY KEY, team_id CHAR(36) NOT NULL, player_name VARCHAR(16) NOT NULL,"
                    + "permissions INT NOT NULL DEFAULT 0, joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "INDEX idx_team_members_team(team_id),"
                    + "CONSTRAINT fk_team_member_team FOREIGN KEY(team_id) REFERENCES ravencore_teams(team_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ravencore_team_invites ("
                    + "team_id CHAR(36) NOT NULL, target_uuid CHAR(36) NOT NULL, inviter_uuid CHAR(36) NOT NULL,"
                    + "target_name VARCHAR(16) NOT NULL, expires_at BIGINT NOT NULL,"
                    + "PRIMARY KEY(team_id,target_uuid), INDEX idx_team_invite_target(target_uuid),"
                    + "CONSTRAINT fk_team_invite_team FOREIGN KEY(team_id) REFERENCES ravencore_teams(team_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ravencore_team_settings ("
                    + "player_uuid CHAR(36) PRIMARY KEY, invites_enabled BOOLEAN NOT NULL DEFAULT TRUE)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create team tables", e);
        }
    }

    @Override
    public void dropTable() {
        throw new UnsupportedOperationException("Team tables must not be dropped through the runtime module");
    }
}
