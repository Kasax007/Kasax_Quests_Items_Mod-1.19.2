package net.kasax.questsicons.selftest;

import java.nio.file.Path;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** The self-test mod on both sides: with -Dqi.dump=file it writes the registry dump and stops the server. */
@Mod("questsicons_selftest")
public final class SelftestMod {
    public SelftestMod() {
        String dump = System.getProperty("qi.dump");
        if (dump == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            try {
                RegistryDump.write(event.getServer(), Path.of(dump));
                System.out.println("[selftest] registry dump written to " + dump);
            } catch (Exception e) {
                e.printStackTrace();
            }
            event.getServer().halt(false);
        });
    }
}
