package com.kammoun.raven.scoreboard.board;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

final class RavenPacketSidebar {

    private static final String OBJECTIVE_NAME = "raven_sb";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Handles HANDLES = Handles.create();

    private final Player player;
    private Object objective;
    private boolean registered;

    RavenPacketSidebar(Player player) {
        this.player = player;
    }

    void create(Component title) {
        objective = HANDLES.newObjective(OBJECTIVE_NAME, title, null);
        send(HANDLES.objectivePacket(objective, "REGISTER"));
        send(HANDLES.displayPacket(objective));
        registered = true;
    }

    void updateTitle(Component title) {
        if (!registered) {
            create(title);
            return;
        }
        objective = HANDLES.newObjective(OBJECTIVE_NAME, title, null);
        send(HANDLES.objectivePacket(objective, "UPDATE"));
    }

    void setLine(String holder, int score, Component displayName, Component numberFormat) {
        if (!registered) {
            return;
        }
        send(HANDLES.scorePacket(holder, OBJECTIVE_NAME, score, displayName, numberFormat));
    }

    void removeLine(String holder) {
        if (!registered) {
            return;
        }
        send(HANDLES.resetScorePacket(holder, OBJECTIVE_NAME));
    }

    void delete() {
        if (!registered) {
            return;
        }
        send(HANDLES.objectivePacket(objective, "UNREGISTER"));
        registered = false;
        objective = null;
    }

    private void send(Object packet) {
        if (!player.isOnline()) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = field(handle.getClass(), "connection").get(handle);
            Method send = sendMethod(connection.getClass());
            send.invoke(connection, packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to send scoreboard packet", exception);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method sendMethod(Class<?> connectionType) throws ClassNotFoundException {
        Class<?> packetType = Class.forName("net.minecraft.network.protocol.Packet");
        for (Method method : connectionType.getMethods()) {
            if (!method.getName().equals("send") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(packetType)
                    || packetType.isAssignableFrom(method.getParameterTypes()[0])) {
                return method;
            }
        }
        throw new IllegalStateException("No packet send method found on " + connectionType.getName());
    }

    private static final class Handles {

        private final Object dummyScoreboard;
        private final Object dummyCriteria;
        private final Object integerRenderType;
        private final Object sidebarDisplaySlot;
        private final Constructor<?> objectiveConstructor;
        private final Constructor<?> fixedFormatConstructor;
        private final Constructor<?> displayObjectiveConstructor;
        private final Constructor<?> setScoreConstructor;
        private final Constructor<?> resetScoreConstructor;
        private final Method adventureAsVanilla;
        private final Method literalComponent;
        private final Constructor<?> objectivePacketConstructor;
        private final Class<?> objectiveActionType;

        private Handles(Object dummyScoreboard, Object dummyCriteria, Object integerRenderType,
                        Object sidebarDisplaySlot, Constructor<?> objectiveConstructor,
                        Constructor<?> fixedFormatConstructor, Constructor<?> displayObjectiveConstructor,
                        Constructor<?> setScoreConstructor, Constructor<?> resetScoreConstructor,
                        Method adventureAsVanilla, Method literalComponent,
                        Constructor<?> objectivePacketConstructor, Class<?> objectiveActionType) {
            this.dummyScoreboard = dummyScoreboard;
            this.dummyCriteria = dummyCriteria;
            this.integerRenderType = integerRenderType;
            this.sidebarDisplaySlot = sidebarDisplaySlot;
            this.objectiveConstructor = objectiveConstructor;
            this.fixedFormatConstructor = fixedFormatConstructor;
            this.displayObjectiveConstructor = displayObjectiveConstructor;
            this.setScoreConstructor = setScoreConstructor;
            this.resetScoreConstructor = resetScoreConstructor;
            this.adventureAsVanilla = adventureAsVanilla;
            this.literalComponent = literalComponent;
            this.objectivePacketConstructor = objectivePacketConstructor;
            this.objectiveActionType = objectiveActionType;
        }

        static Handles create() {
            try {
                Class<?> scoreboard = Class.forName("net.minecraft.world.scores.Scoreboard");
                Class<?> criteria = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria");
                Class<?> renderType = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
                Class<?> component = Class.forName("net.minecraft.network.chat.Component");
                Class<?> numberFormat = Class.forName("net.minecraft.network.chat.numbers.NumberFormat");
                Class<?> objective = Class.forName("net.minecraft.world.scores.Objective");
                Class<?> displaySlot = Class.forName("net.minecraft.world.scores.DisplaySlot");
                Class<?> fixedFormat = Class.forName("net.minecraft.network.chat.numbers.FixedFormat");
                Class<?> setObjectivePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");
                Class<?> displayObjectivePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
                Class<?> setScorePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket");
                Class<?> resetScorePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket");

                Object dummyScoreboard = scoreboard.getConstructor().newInstance();
                Object dummyCriteria = criteria.getField("DUMMY").get(null);
                Object integerRenderType = enumValue(renderType, "INTEGER");
                Object sidebarDisplaySlot = enumValue(displaySlot, "SIDEBAR");

                Constructor<?> objectiveConstructor = objective.getConstructor(
                        scoreboard, String.class, criteria, component, renderType, boolean.class, numberFormat);
                Constructor<?> fixedFormatConstructor = fixedFormat.getConstructor(component);
                Constructor<?> displayObjectiveConstructor = displayObjectivePacket.getConstructor(displaySlot, objective);
                Constructor<?> setScoreConstructor = setScorePacket.getConstructor(
                        String.class, String.class, int.class, Optional.class, Optional.class);
                Constructor<?> resetScoreConstructor = resetScorePacket.getConstructor(String.class, String.class);

                Method adventureAsVanilla = adventureAsVanilla();
                Method literalComponent = component.getMethod("literal", String.class);

                Constructor<?> objectivePacketConstructor = null;
                Class<?> objectiveActionType = null;
                for (Constructor<?> constructor : setObjectivePacket.getConstructors()) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if (parameters.length == 2 && parameters[0] == objective) {
                        objectivePacketConstructor = constructor;
                        objectiveActionType = parameters[1];
                        break;
                    }
                }
                if (objectivePacketConstructor == null) {
                    throw new IllegalStateException("No objective packet constructor found");
                }

                return new Handles(dummyScoreboard, dummyCriteria, integerRenderType, sidebarDisplaySlot,
                        objectiveConstructor, fixedFormatConstructor, displayObjectiveConstructor,
                        setScoreConstructor, resetScoreConstructor, adventureAsVanilla, literalComponent,
                        objectivePacketConstructor, objectiveActionType);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Scoreboard packets are not available on this server version", exception);
            }
        }

        Object newObjective(String name, Component title, Component numberFormat) {
            try {
                return objectiveConstructor.newInstance(dummyScoreboard, name, dummyCriteria,
                        vanilla(title), integerRenderType, false, format(numberFormat));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create scoreboard objective", exception);
            }
        }

        Object objectivePacket(Object objective, String action) {
            try {
                return objectivePacketConstructor.newInstance(objective, action(action));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create objective packet", exception);
            }
        }

        Object displayPacket(Object objective) {
            try {
                return displayObjectiveConstructor.newInstance(sidebarDisplaySlot, objective);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create display objective packet", exception);
            }
        }

        Object scorePacket(String holder, String objectiveName, int score, Component displayName, Component numberFormat) {
            try {
                return setScoreConstructor.newInstance(holder, objectiveName, score,
                        Optional.ofNullable(vanilla(displayName)),
                        numberFormat == null ? Optional.empty() : Optional.of(format(numberFormat)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create score packet", exception);
            }
        }

        Object resetScorePacket(String holder, String objectiveName) {
            try {
                return resetScoreConstructor.newInstance(holder, objectiveName);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create reset score packet", exception);
            }
        }

        private Object format(Component component) throws ReflectiveOperationException {
            return fixedFormatConstructor.newInstance(vanilla(component == null ? Component.empty() : component));
        }

        private Object vanilla(Component component) throws ReflectiveOperationException {
            if (component == null) {
                return null;
            }
            if (adventureAsVanilla != null) {
                try {
                    return adventureAsVanilla.invoke(null, component);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Fall back to plain text instead of dropping the sidebar.
                }
            }
            return literalComponent.invoke(null, PLAIN.serialize(component));
        }

        private static Method adventureAsVanilla() {
            try {
                Method method = Class.forName("io.papermc.paper.adventure.PaperAdventure")
                        .getDeclaredMethod("asVanilla", Component.class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Object action(String name) {
            if (objectiveActionType.isEnum()) {
                return enumValue(objectiveActionType, name);
            }
            return switch (name) {
                case "REGISTER" -> 0;
                case "UNREGISTER" -> 1;
                case "UPDATE" -> 2;
                default -> throw new IllegalArgumentException("Unknown objective action " + name);
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object enumValue(Class<?> enumType, String name) {
            return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
        }
    }
}
