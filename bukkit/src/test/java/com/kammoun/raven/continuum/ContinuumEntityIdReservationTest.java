package com.kammoun.raven.continuum;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.ServerLinks;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuumEntityIdReservationTest {

    @Test
    void reservesEntityIdThroughCanvasInternalSetter() {
        TestConnection connection = new TestConnection();

        ContinuumEntityIdReservation.Result result = new ContinuumEntityIdReservation().reserve(connection, 5821);

        assertTrue(result.reserved());
        assertEquals(5821, connection.entityId);
    }

    private static final class TestConnection implements PlayerConfigurationConnection {

        private int entityId;

        @SuppressWarnings("unused")
        public void setInternalPluginDefinedEntityId(int entityId) {
            this.entityId = entityId;
        }

        @Override
        public Audience getAudience() {
            return Audience.empty();
        }

        @Override
        public PlayerProfile getProfile() {
            return null;
        }

        @Override
        public void clearChat() {
        }

        @Override
        public void completeReconfiguration() {
        }

        @Override
        public void sendReportDetails(java.util.Map<String, String> details) {
        }

        @Override
        public void sendLinks(ServerLinks links) {
        }

        @Override
        public void transfer(String host, int port) {
        }

        @Override
        public <T> T getClientOption(ClientOption<T> type) {
            return null;
        }

        public String getClientBrandName() {
            return null;
        }

        @Override
        public void storeCookie(NamespacedKey key, byte[] value) {
        }

        @Override
        public CompletableFuture<byte[]> retrieveCookie(NamespacedKey key) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void disconnect(Component component) {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isTransferred() {
            return false;
        }

        @Override
        public SocketAddress getAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getClientAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getVirtualHost() {
            return null;
        }

        @Override
        public InetSocketAddress getHAProxyAddress() {
            return null;
        }

        @Override
        public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        }

        @Override
        public Set<String> getListeningPluginChannels() {
            return Set.of();
        }
    }
}
