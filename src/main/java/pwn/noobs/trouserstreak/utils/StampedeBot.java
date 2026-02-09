package pwn.noobs.trouserstreak.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
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
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
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

import net.minecraft.network.message.MessageSignatureData;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Uuids;

import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.message.LastSeenMessageList;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * StampedeBot - A bot that connects to a server with a username and executes a
 * command upon joining.
 * Supports SOCKS proxy connections for bypassing rate limits.
 */
public class StampedeBot implements Runnable, ClientLoginPacketListener {
    private final String ip;
    private final int port;
    private final String username;
    private final String commandToRun;
    private final CountDownLatch startLatch;
    private final int botId;
    private final String proxyAddress; // format: "host:port" or "host:port:user:pass" or null for direct
    private ClientConnection connection;
    private volatile boolean completed = false;

    public StampedeBot(String serverAddress, String username, String commandToRun, CountDownLatch startLatch, int botId,
            String proxyAddress) {
        String[] parts = serverAddress.split(":");
        this.ip = parts[0];
        this.port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
        this.username = username;
        this.commandToRun = commandToRun;
        this.startLatch = startLatch;
        this.botId = botId;
        this.proxyAddress = proxyAddress;
    }

    public boolean isCompleted() {
        return completed;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        try {
            // Wait for all bots in this wave to be ready
            if (startLatch != null) {
                startLatch.await();
            }

            InetSocketAddress serverAddr = new InetSocketAddress(ip, port);

            // Access NIO backend via reflection
            Object backend = null;
            try {
                java.lang.reflect.Field nioField = NetworkingBackend.class.getDeclaredField("NIO");
                nioField.setAccessible(true);
                backend = nioField.get(null);
            } catch (Exception e) {
                ChatUtils.error("Stampede[" + botId + "]: Failed to access NIO backend: " + e.getMessage());
            }

            connection = new ClientConnection(NetworkSide.CLIENTBOUND);
            ChannelFuture future;

            if (proxyAddress != null && !proxyAddress.isEmpty()) {
                // Connect via SOCKS proxy
                future = connectWithProxy(serverAddr, connection);
            } else {
                // Direct connection
                future = ClientConnection.connect(serverAddr, (NetworkingBackend) backend, connection);
            }

            future.awaitUninterruptibly();

            if (!future.isSuccess()) {
                ChatUtils.error("Stampede[" + botId + "]: Failed to connect: " +
                        (future.cause() != null ? future.cause().getMessage() : "unknown error"));
                completed = true;
                return;
            }

            ChatUtils.info("Stampede[" + botId + "]: " + username + " connected" +
                    (proxyAddress != null ? " via proxy" : "") + ".");

            connection.transitionInbound(LoginStates.S2C, this);

            connection.send(new HandshakeC2SPacket(SharedConstants.getGameVersion().protocolVersion(), ip, port,
                    ConnectionIntent.LOGIN));
            connection.transitionOutbound(LoginStates.C2S);
            connection.send(new LoginHelloC2SPacket(username, Uuids.getOfflinePlayerUuid(username)));

            while (connection.isOpen()) {
                connection.tick();
                Thread.sleep(10);
            }

            if (connection.getDisconnectionInfo() != null) {
                ChatUtils.warning("Stampede[" + botId + "]: " + username + " disconnected: " +
                        connection.getDisconnectionInfo().reason().getString());
            }

        } catch (Exception e) {
            ChatUtils.error("Stampede[" + botId + "] Error: " + e.getMessage());
        } finally {
            completed = true;
        }
    }

    private ChannelFuture connectWithProxy(InetSocketAddress serverAddr, ClientConnection conn) {
        String[] proxyParts = proxyAddress.split(":");
        String proxyHost = proxyParts[0];
        int proxyPort = Integer.parseInt(proxyParts[1]);
        String proxyUser = proxyParts.length > 2 ? proxyParts[2] : null;
        String proxyPass = proxyParts.length > 3 ? proxyParts[3] : null;

        EventLoopGroup group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // Add SOCKS5 proxy handler
                        InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
                        if (proxyUser != null && proxyPass != null) {
                            ch.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr, proxyUser, proxyPass));
                        } else {
                            ch.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr));
                        }

                        // Copy the channel to connection using reflection
                        try {
                            java.lang.reflect.Field channelField = ClientConnection.class.getDeclaredField("channel");
                            channelField.setAccessible(true);
                            channelField.set(conn, ch);
                        } catch (Exception e) {
                            ChatUtils.error("Stampede: Failed to set channel: " + e.getMessage());
                        }
                    }
                });

        return bootstrap.connect(serverAddr);
    }

    @Override
    public void onHello(LoginHelloS2CPacket packet) {
        // Server wants encryption - offline mode servers don't send this
    }

    @Override
    public void onSuccess(LoginSuccessS2CPacket packet) {
        ChatUtils.info("Stampede[" + botId + "]: " + username + " login success!");
        try {
            connection.send(getPacketInstance(EnterConfigurationC2SPacket.class));
        } catch (Exception e) {
            ChatUtils.error("Stampede[" + botId + "] Error: " + e.getMessage());
        }
        connection.transitionOutbound(ConfigurationStates.C2S);

        connection.transitionInbound(ConfigurationStates.S2C,
                (ClientConfigurationPacketListener) Proxy.newProxyInstance(
                        StampedeBot.class.getClassLoader(),
                        new Class[] { ClientConfigurationPacketListener.class },
                        (proxy, method, args) -> {
                            String name = method.getName();

                            if (name.equals("getPhase"))
                                return NetworkPhase.CONFIGURATION;
                            if (name.equals("getSide"))
                                return NetworkSide.CLIENTBOUND;
                            if (name.equals("accepts"))
                                return true;
                            if (name.equals("isConnectionOpen"))
                                return connection.isOpen();

                            if (name.equals("onSelectKnownPacks") && args[0] instanceof SelectKnownPacksS2CPacket) {
                                connection.send(new SelectKnownPacksC2SPacket(java.util.List.of()));
                                return null;
                            }

                            if (name.equals("onDynamicRegistries") && args[0] instanceof DynamicRegistriesS2CPacket) {
                                return null;
                            }

                            if (name.equals("onSynchronizeTags") && args[0] instanceof SynchronizeTagsS2CPacket) {
                                return null;
                            }

                            if (name.equals("onReady") && args[0] instanceof ReadyS2CPacket) {
                                try {
                                    connection.send(getPacketInstance(ReadyC2SPacket.class));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // Setup Play Listener
                                try {
                                    ClientPlayPacketListener listener = (ClientPlayPacketListener) Proxy
                                            .newProxyInstance(
                                                    StampedeBot.class.getClassLoader(),
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
                                                                ChatUtils.info("Stampede[" + botId + "]: " + username
                                                                        + " joined game!");
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
                                                                    ChatUtils.warning(
                                                                            "Stampede[" + botId + "]: Disconnected: "
                                                                                    + dp.reason().getString());
                                                                }
                                                                return null;
                                                            }
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
                                                            return null;
                                                        }
                                                    });

                                    // Prepare Play Transition
                                    DynamicRegistryManager drm = null;
                                    try {
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

                                    // SEND COMMAND NOW
                                    ChatUtils.info(
                                            "Stampede[" + botId + "]: " + username + " executing: " + commandToRun);
                                    try {
                                        if (commandToRun.startsWith("/")) {
                                            // It's a command
                                            Object packetCmd = createCommandPacket(commandToRun);
                                            if (packetCmd != null) {
                                                connection.send((net.minecraft.network.packet.Packet<?>) packetCmd);
                                            }
                                        } else {
                                            // It's a chat message
                                            Object packetChat = createChatPacket(commandToRun);
                                            if (packetChat != null) {
                                                connection.send((net.minecraft.network.packet.Packet<?>) packetChat);
                                            }
                                        }
                                    } catch (Exception e) {
                                        ChatUtils.error("Stampede[" + botId + "]: Failed to send: " + e.getMessage());
                                    }

                                    // Setup inbound listener
                                    connection.transitionInbound(
                                            PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(drm)),
                                            listener);

                                    // Disconnect after a short delay
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(1500);
                                            if (connection.isOpen()) {
                                                connection.disconnect(Text.literal("Stampede: Done"));
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
        ChatUtils.warning("Stampede[" + botId + "]: " + username + " login rejected: " + p.reason().getString());
    }

    @Override
    public void onCompression(LoginCompressionS2CPacket p) {
        connection.setCompressionThreshold(p.getCompressionThreshold(), false);
    }

    @Override
    public void onQueryRequest(LoginQueryRequestS2CPacket p) {
    }

    @Override
    public void onCookieRequest(CookieRequestS2CPacket p) {
    }

    @Override
    public void onDisconnected(DisconnectionInfo info) {
        ChatUtils.warning("Stampede[" + botId + "]: " + username + " disconnected: " + info.reason().getString());
    }

    @Override
    public boolean isConnectionOpen() {
        return connection != null && connection.isOpen();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPacketInstance(Class<T> clazz) throws Exception {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (NoSuchFieldException e) {
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
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        Constructor<ChatCommandSignedC2SPacket> packetCtor = ChatCommandSignedC2SPacket.class.getDeclaredConstructor(
                String.class, Instant.class, long.class, ArgumentSignatureDataMap.class,
                LastSeenMessageList.Acknowledgment.class);
        packetCtor.setAccessible(true);
        return packetCtor.newInstance(command, timestamp, salt, argSigs, ack);
    }

    private static Object createChatPacket(String message) throws Exception {
        Instant timestamp = Instant.now();
        long salt = ThreadLocalRandom.current().nextLong();

        Object ack = null;
        try {
            Constructor<?>[] ctors = LastSeenMessageList.Acknowledgment.class.getDeclaredConstructors();
            for (Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] types = c.getParameterTypes();
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
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        Constructor<ChatMessageC2SPacket> packetCtor = ChatMessageC2SPacket.class.getDeclaredConstructor(
                String.class, Instant.class, long.class, MessageSignatureData.class,
                LastSeenMessageList.Acknowledgment.class);
        packetCtor.setAccessible(true);
        return packetCtor.newInstance(message, timestamp, salt, null, ack);
    }
}
