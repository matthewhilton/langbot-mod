package matth.langbot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;

public class ClientTickDelay {
    private static final List<Runnable> scheduledTasks = new ArrayList<>();
    private static int ticksRemaining = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickDelay::onTick);
    }

    private static void onTick(MinecraftClient client) {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                for (Runnable task : scheduledTasks) {
                    task.run();
                }
                scheduledTasks.clear();
            }
        }
    }

    public static void waitTicks(int ticks, Runnable task) {
        ticksRemaining = ticks;
        scheduledTasks.add(task);
    }
}