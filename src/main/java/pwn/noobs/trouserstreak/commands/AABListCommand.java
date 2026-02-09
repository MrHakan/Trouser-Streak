package pwn.noobs.trouserstreak.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import meteordevelopment.meteorclient.MeteorClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class AABListCommand extends Command {
    private final File aabFolder = new File(MeteorClient.FOLDER, "aab");

    public AABListCommand() {
        super("aabList", "Manage aab lists.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(LiteralArgumentBuilder.<CommandSource>literal("create")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("list", StringArgumentType.word())
                        .executes(context -> {
                            String listName = StringArgumentType.getString(context, "list");
                            createList(listName);
                            return SINGLE_SUCCESS;
                        })));

        builder.then(LiteralArgumentBuilder.<CommandSource>literal("add")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("list", StringArgumentType.word())
                        .then(RequiredArgumentBuilder
                                .<CommandSource, String>argument("player", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String listName = StringArgumentType.getString(context, "list");
                                    String player = StringArgumentType.getString(context, "player");
                                    addToList(listName, player);
                                    return SINGLE_SUCCESS;
                                }))));
    }

    private void createList(String name) {
        if (!aabFolder.exists()) {
            aabFolder.mkdirs();
        }
        File file = new File(aabFolder, name + ".txt");
        try {
            if (file.createNewFile()) {
                ChatUtils.info("Created list: " + name);
            } else {
                ChatUtils.error("List already exists: " + name);
            }
        } catch (IOException e) {
            ChatUtils.error("Failed to create list: " + e.getMessage());
        }
    }

    private void addToList(String listName, String player) {
        if (!aabFolder.exists()) {
            aabFolder.mkdirs();
        }
        File file = new File(aabFolder, listName + ".txt");
        if (!file.exists()) {
            // User requested: "if aab folder not available it will create one first then
            // create the list1.txt file."
            // Although logically "add" assumes "create" happened, I'll autocreate.
            try {
                file.createNewFile();
                ChatUtils.info("Created new list: " + listName);
            } catch (IOException e) {
                ChatUtils.error("Failed to create file for list: " + listName);
                return;
            }
        }

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(player + "\n");
            ChatUtils.info("Added " + player + " to " + listName);
        } catch (IOException e) {
            ChatUtils.error("Failed to write to list: " + e.getMessage());
        }
    }
}
