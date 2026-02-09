package pwn.noobs.trouserstreak.mixin.accessor;

import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EnterConfigurationC2SPacket.class)
public interface EnterConfigurationC2SPacketAccessor {
    @Invoker("<init>")
    static EnterConfigurationC2SPacket create() {
        throw new AssertionError();
    }
}
