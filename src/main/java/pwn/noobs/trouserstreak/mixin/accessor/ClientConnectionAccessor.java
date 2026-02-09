package pwn.noobs.trouserstreak.mixin.accessor;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.state.NetworkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientConnection.class)
public interface ClientConnectionAccessor {
    @Accessor
    Channel getChannel();

    @Invoker("setPacketListener")
    void callSetPacketListener(NetworkState<?> state, PacketListener listener);
}
