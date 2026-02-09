package pwn.noobs.trouserstreak.mixin.accessor;

import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ReadyC2SPacket.class)
public interface ReadyC2SPacketAccessor {
    @Invoker("<init>")
    static ReadyC2SPacket create() {
        throw new AssertionError();
    }
}
