package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import pwn.noobs.trouserstreak.utils.StampedeBot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * StampedeCommand - Mass login bots to the current server
 * 
 * Usage without proxies (sequential mode - 8 second delay between each):
 * .stampede <base_username> <count> "<command_to_run>"
 * 
 * Usage with proxies (parallel mode - 3 bots at a time):
 * .stampede <base_username> <count> "<command_to_run>" proxies.txt
 * 
 * Example: .stampede aab 50 "/register 123456"
 * Example with proxies: .stampede aab 50 "/register 123456" proxies.txt
 * 
 * This will create bots named aab1, aab2, aab3, ... aab50
 * Each bot connects, executes the command, and disconnects.
 * 
 * Proxy file format (one per line):
 * host:port
 * host:port:username:password
 */
public class StampedeCommand extends Command {
    private static final int BOTS_PER_WAVE = 3; // Number of bots to connect simultaneously (with proxies)
    private static final int DELAY_BETWEEN_WAVES_MS = 2000; // Delay between waves (with proxies)
    private static final int DELAY_SEQUENTIAL_MS = 8000; // Delay between bots without proxies
    private final File stampedeFolder = new File(MeteorClient.FOLDER, "stampede");

    public StampedeCommand() {
        super("stampede", "Mass login bots that execute a command upon joining.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .stampede <base_username> <count> "<command>" [proxy_file]
        builder.then(RequiredArgumentBuilder.<CommandSource, String>argument("base_username", StringArgumentType.word())
                .then(RequiredArgumentBuilder
                        .<CommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 100))
                        .then(RequiredArgumentBuilder
                                .<CommandSource, String>argument("command", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String baseUsername = StringArgumentType.getString(context, "base_username");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    String commandPart = StringArgumentType.getString(context, "command");

                                    // Parse command and optional proxy file
                                    ParsedArgs parsed = parseCommandArgs(commandPart);

                                    return runStampede(baseUsername, count, parsed.command, parsed.proxyFile);
                                }))));
    }

    private static class ParsedArgs {
        String command;
        String proxyFile;
    }

    private ParsedArgs parseCommandArgs(String input) {
        ParsedArgs result = new ParsedArgs();
        input = input.trim();

        // Check if command is quoted
        if (input.startsWith("\"")) {
            int endQuote = input.indexOf("\"", 1);
            if (endQuote > 0) {
                result.command = input.substring(1, endQuote);
                String remainder = input.substring(endQuote + 1).trim();
                if (!remainder.isEmpty()) {
                    result.proxyFile = remainder;
                }
            } else {
                result.command = input;
            }
        } else {
            // No quotes - check if there's a .txt file at the end
            if (input.endsWith(".txt")) {
                int lastSpace = input.lastIndexOf(" ");
                if (lastSpace > 0) {
                    result.command = input.substring(0, lastSpace).trim();
                    result.proxyFile = input.substring(lastSpace + 1).trim();
                } else {
                    result.command = input;
                }
            } else {
                result.command = input;
            }
        }

        return result;
    }

    private int runStampede(String baseUsername, int count, String command, String proxyFile) {
        if (mc.getCurrentServerEntry() == null) {
            ChatUtils.error("You must be on a server.");
            return SINGLE_SUCCESS;
        }

        String serverIp = mc.getCurrentServerEntry().address;

        // Load proxies if specified
        List<String> proxies = new ArrayList<>();
        if (proxyFile != null) {
            proxies = loadProxies(proxyFile);
            if (proxies.isEmpty()) {
                ChatUtils.warning(
                        "Stampede: No valid proxies loaded from " + proxyFile + ". Running in sequential mode.");
            } else {
                ChatUtils.info("Stampede: Loaded " + proxies.size() + " proxies from " + proxyFile);
            }
        }

        final List<String> finalProxies = proxies;
        final boolean hasProxies = !proxies.isEmpty();

        ChatUtils.info(
                "Stampede: Starting with " + count + " bots (" + baseUsername + "1 to " + baseUsername + count + ")");
        ChatUtils.info("Stampede: Command to execute: " + command);

        if (hasProxies) {
            ChatUtils.info("Stampede: Mode: PARALLEL (waves of " + BOTS_PER_WAVE + " with proxies)");
        } else {
            ChatUtils.info("Stampede: Mode: SEQUENTIAL (" + (DELAY_SEQUENTIAL_MS / 1000) + "s delay between bots)");
            ChatUtils.warning(
                    "Stampede: Without proxies, bots will be throttled by server. Consider adding proxies.txt");
        }

        // Run in background thread
        final String finalCommand = command;
        new Thread(() -> {
            try {
                if (hasProxies) {
                    executeStampedeWithProxies(serverIp, baseUsername, count, finalCommand, finalProxies);
                } else {
                    executeStampedeSequential(serverIp, baseUsername, count, finalCommand);
                }
            } catch (Exception e) {
                ChatUtils.error("Stampede Error: " + e.getMessage());
            }
        }).start();

        return SINGLE_SUCCESS;
    }

    private List<String> loadProxies(String fileName) {
        List<String> proxies = new ArrayList<>();

        if (!stampedeFolder.exists()) {
            stampedeFolder.mkdirs();
        }

        if (!fileName.endsWith(".txt")) {
            fileName += ".txt";
        }

        File proxyFile = new File(stampedeFolder, fileName);
        if (!proxyFile.exists()) {
            ChatUtils.error("Proxy file not found: " + proxyFile.getAbsolutePath());
            return proxies;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(proxyFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Validate format: host:port or host:port:user:pass
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        try {
                            Integer.parseInt(parts[1]); // Validate port
                            proxies.add(line);
                        } catch (NumberFormatException e) {
                            ChatUtils.warning("Stampede: Invalid proxy format: " + line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChatUtils.error("Error reading proxy file: " + e.getMessage());
        }

        return proxies;
    }

    private void executeStampedeWithProxies(String serverIp, String baseUsername, int totalCount, String command,
            List<String> proxies) {
        int botsLaunched = 0;
        int waveNumber = 0;
        int proxyIndex = 0;

        while (botsLaunched < totalCount) {
            waveNumber++;
            int botsInThisWave = Math.min(BOTS_PER_WAVE, totalCount - botsLaunched);

            ChatUtils.info("Stampede: Wave " + waveNumber + " - Launching " + botsInThisWave + " bots...");

            // Create a latch for synchronized start
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Thread> waveThreads = new ArrayList<>();

            // Create and start bot threads (they will wait on the latch)
            for (int i = 0; i < botsInThisWave; i++) {
                int botNumber = botsLaunched + i + 1;
                String username = baseUsername + botNumber;

                // Get proxy for this bot (cycle through proxies)
                String proxy = proxies.get(proxyIndex % proxies.size());
                proxyIndex++;

                StampedeBot bot = new StampedeBot(serverIp, username, command, startLatch, botNumber, proxy);

                Thread botThread = new Thread(bot);
                botThread.setName("StampedeBot-" + username);
                waveThreads.add(botThread);
                botThread.start();
            }

            // Small delay to ensure all threads are waiting on the latch
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Release all bots in this wave simultaneously
            startLatch.countDown();

            // Wait for all bots in this wave to complete (with timeout)
            for (Thread thread : waveThreads) {
                try {
                    thread.join(15000); // 15 second timeout per bot
                } catch (InterruptedException ignored) {
                }
            }

            botsLaunched += botsInThisWave;

            // Delay before next wave (if there are more bots)
            if (botsLaunched < totalCount) {
                ChatUtils.info("Stampede: Wave " + waveNumber + " complete. " + botsLaunched + "/" + totalCount
                        + " bots done.");
                try {
                    Thread.sleep(DELAY_BETWEEN_WAVES_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }

        ChatUtils.info("Stampede: Complete! All " + totalCount + " bots have finished.");
    }

    private void executeStampedeSequential(String serverIp, String baseUsername, int totalCount, String command) {
        for (int i = 1; i <= totalCount; i++) {
            String username = baseUsername + i;

            ChatUtils.info("Stampede: Bot " + i + "/" + totalCount + " - " + username);

            // Run bot directly (no latch needed for sequential)
            StampedeBot bot = new StampedeBot(serverIp, username, command, null, i, null);
            Thread botThread = new Thread(bot);
            botThread.setName("StampedeBot-" + username);
            botThread.start();

            // Wait for bot to complete
            try {
                botThread.join(15000);
            } catch (InterruptedException ignored) {
            }

            // Delay before next bot (except for the last one)
            if (i < totalCount) {
                ChatUtils.info("Stampede: Waiting " + (DELAY_SEQUENTIAL_MS / 1000) + " seconds before next bot...");
                try {
                    Thread.sleep(DELAY_SEQUENTIAL_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }

        ChatUtils.info("Stampede: Complete! All " + totalCount + " bots have finished.");
    }
}
