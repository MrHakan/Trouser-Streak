package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import pwn.noobs.trouserstreak.Trouser;

import java.util.Arrays;
import java.util.List;

public class MannequinPlus extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgStyle = settings.createGroup("Name Style");
        private final SettingGroup sgAttributes = settings.createGroup("Attributes");
        private final SettingGroup sgFlags = settings.createGroup("Flags");
        private final SettingGroup sgTransform = settings.createGroup("Transform");
        private final SettingGroup sgEquipment = settings.createGroup("Equipment");
        private final SettingGroup sgEffects = settings.createGroup("Effects");
        private final SettingGroup sgActions = settings.createGroup("Actions");

        // General
        private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
                        .name("Name")
                        .description("Name of the Mannequin.")
                        .defaultValue("Mannequin")
                        .renderer(StarscriptTextBoxRenderer.class)
                        .build());

        private final Setting<String> description = sgGeneral.add(new StringSetting.Builder()
                        .name("Description")
                        .description("Description of the Mannequin.")
                        .defaultValue("")
                        .build());

        private final Setting<String> tags = sgGeneral.add(new StringSetting.Builder()
                        .name("Tags")
                        .description("Comma separated list of tags.")
                        .defaultValue("")
                        .build());

        private final Setting<String> team = sgGeneral.add(new StringSetting.Builder()
                        .name("Team")
                        .description("Team name.")
                        .defaultValue("")
                        .build());

        private final Setting<Boolean> agroTroll = sgGeneral.add(new BoolSetting.Builder()
                        .name("Agro Troll")
                        .description("Rides an invisible Vindicator to attack players.")
                        .defaultValue(false)
                        .build());

        // Profile
        public enum ProfileMode {
                Username,
                Texture,
                None
        }

        private final Setting<ProfileMode> profileMode = sgGeneral.add(new EnumSetting.Builder<ProfileMode>()
                        .name("Profile Mode")
                        .description("How to set the Mannequin's skin.")
                        .defaultValue(ProfileMode.Username)
                        .build());

        private final Setting<String> profileName = sgGeneral.add(new StringSetting.Builder()
                        .name("Profile Name")
                        .description("Username of the player skin.")
                        .defaultValue("GeronForever")
                        .visible(() -> profileMode.get() == ProfileMode.Username)
                        .build());

        private final Setting<String> profileTexture = sgGeneral.add(new StringSetting.Builder()
                        .name("Profile Texture")
                        .description("Path to texture (e.g. entity/player/slim/kai).")
                        .defaultValue("entity/player/slim/kai")
                        .visible(() -> profileMode.get() == ProfileMode.Texture)
                        .build());

        // Name Style
        private final Setting<Boolean> nameVisible = sgStyle.add(new BoolSetting.Builder()
                        .name("Name Visible")
                        .description("Whether the custom name is visible.")
                        .defaultValue(true)
                        .build());

        private final Setting<String> nameColor = sgStyle.add(new StringSetting.Builder()
                        .name("Name Color")
                        .description("Color name (e.g. dark_red).")
                        .defaultValue("dark_red")
                        .build());

        private final Setting<Boolean> bold = sgStyle.add(new BoolSetting.Builder()
                        .name("Bold")
                        .defaultValue(true)
                        .build());

        private final Setting<Boolean> italic = sgStyle.add(new BoolSetting.Builder()
                        .name("Italic")
                        .defaultValue(true)
                        .build());

        private final Setting<Boolean> underlined = sgStyle.add(new BoolSetting.Builder()
                        .name("Underlined")
                        .defaultValue(true)
                        .build());

        private final Setting<Boolean> strikethrough = sgStyle.add(new BoolSetting.Builder()
                        .name("Strikethrough")
                        .defaultValue(true)
                        .build());

        private final Setting<Boolean> obfuscated = sgStyle.add(new BoolSetting.Builder()
                        .name("Obfuscated")
                        .defaultValue(false)
                        .build());

        // Attributes / Stats
        private final Setting<Double> health = sgAttributes.add(new DoubleSetting.Builder()
                        .name("Health")
                        .description("Current Health.")
                        .defaultValue(20.0)
                        .min(1.0)
                        .build());

        private final Setting<Double> maxHealth = sgAttributes.add(new DoubleSetting.Builder()
                        .name("Max Health")
                        .description("Maximum Health Attribute.")
                        .defaultValue(20.0)
                        .min(1.0)
                        .build());

        private final Setting<Double> scale = sgAttributes.add(new DoubleSetting.Builder()
                        .name("Scale")
                        .description("Scale Attribute.")
                        .defaultValue(1.0)
                        .min(0.1)
                        .build());

        // Flags
        private final Setting<Boolean> immobile = sgFlags
                        .add(new BoolSetting.Builder().name("Immovable").defaultValue(false).build());
        private final Setting<Boolean> canPickUpLoot = sgFlags
                        .add(new BoolSetting.Builder().name("CanPickUpLoot").defaultValue(false).build());
        private final Setting<Boolean> fallFlying = sgFlags
                        .add(new BoolSetting.Builder().name("FallFlying").defaultValue(false).build());
        private final Setting<Boolean> glowing = sgFlags
                        .add(new BoolSetting.Builder().name("Glowing").defaultValue(false).build());
        private final Setting<Boolean> hasVisualFire = sgFlags
                        .add(new BoolSetting.Builder().name("HasVisualFire").defaultValue(false).build());
        private final Setting<Boolean> invulnerable = sgFlags
                        .add(new BoolSetting.Builder().name("Invulnerable").defaultValue(false).build());
        private final Setting<Boolean> leftHanded = sgFlags
                        .add(new BoolSetting.Builder().name("LeftHanded").defaultValue(false).build());
        private final Setting<Boolean> noAI = sgFlags
                        .add(new BoolSetting.Builder().name("NoAI").defaultValue(false).build());
        private final Setting<Boolean> noGravity = sgFlags
                        .add(new BoolSetting.Builder().name("NoGravity").defaultValue(false).build());
        private final Setting<Boolean> onGround = sgFlags
                        .add(new BoolSetting.Builder().name("OnGround").defaultValue(false).build());
        private final Setting<Boolean> persistenceRequired = sgFlags
                        .add(new BoolSetting.Builder().name("PersistenceRequired").defaultValue(false).build());
        private final Setting<Boolean> silent = sgFlags
                        .add(new BoolSetting.Builder().name("Silent").defaultValue(false).build());

        // Transform
        public enum Pose {
                standing, crouching, swimming, fall_flying, sleeping
        }

        private final Setting<Pose> pose = sgTransform.add(new EnumSetting.Builder<Pose>()
                        .name("Pose")
                        .defaultValue(Pose.standing)
                        .build());

        private final Setting<Double> rotationYaw = sgTransform.add(
                        new DoubleSetting.Builder().name("Yaw").defaultValue(0.0).range(0, 360).sliderMax(360).build());
        private final Setting<Double> rotationPitch = sgTransform.add(new DoubleSetting.Builder().name("Pitch")
                        .defaultValue(0.0).range(-90, 90).sliderRange(-90, 90).build());

        private final Setting<Double> motionX = sgTransform
                        .add(new DoubleSetting.Builder().name("Motion X").defaultValue(0.0).build());
        private final Setting<Double> motionY = sgTransform
                        .add(new DoubleSetting.Builder().name("Motion Y").defaultValue(0.0).build());
        private final Setting<Double> motionZ = sgTransform
                        .add(new DoubleSetting.Builder().name("Motion Z").defaultValue(0.0).build());

        // Equipment
        private final Setting<Item> mainHand = sgEquipment
                        .add(new ItemSetting.Builder().name("Main Hand").defaultValue(Items.AIR).build());
        private final Setting<Item> offHand = sgEquipment
                        .add(new ItemSetting.Builder().name("Off Hand").defaultValue(Items.AIR).build());
        private final Setting<Item> helmet = sgEquipment
                        .add(new ItemSetting.Builder().name("Helmet").defaultValue(Items.AIR).build());
        private final Setting<Item> chestplate = sgEquipment
                        .add(new ItemSetting.Builder().name("Chestplate").defaultValue(Items.AIR).build());
        private final Setting<Item> leggings = sgEquipment
                        .add(new ItemSetting.Builder().name("Leggings").defaultValue(Items.AIR).build());
        private final Setting<Item> boots = sgEquipment
                        .add(new ItemSetting.Builder().name("Boots").defaultValue(Items.AIR).build());

        // Effects
        private final Setting<List<StatusEffect>> effects = sgEffects.add(new StatusEffectListSetting.Builder()
                        .name("Effects")
                        .description("Active effects to apply.")
                        .defaultValue()
                        .build());

        private final Setting<Integer> effectDuration = sgEffects.add(new IntSetting.Builder().name("Duration")
                        .defaultValue(99999).visible(() -> !effects.get().isEmpty()).build());
        private final Setting<Integer> effectAmplifier = sgEffects.add(new IntSetting.Builder().name("Amplifier")
                        .defaultValue(0).visible(() -> !effects.get().isEmpty()).build());
        private final Setting<Boolean> effectAmbient = sgEffects.add(new BoolSetting.Builder().name("Ambient")
                        .defaultValue(true).visible(() -> !effects.get().isEmpty()).build());
        private final Setting<Boolean> effectParticles = sgEffects.add(new BoolSetting.Builder().name("Show Particles")
                        .defaultValue(true).visible(() -> !effects.get().isEmpty()).build());

        // Item Data
        private final Setting<String> spawnEggName = sgActions
                        .add(new StringSetting.Builder().name("Spawn Egg Name").defaultValue("spawn_egg_name").build());
        private final Setting<String> spawnEggLore = sgActions
                        .add(new StringSetting.Builder().name("Spawn Egg Lore").defaultValue("spawn_egg_lore").build());
        private final Setting<String> spawnerName = sgActions
                        .add(new StringSetting.Builder().name("Spawner Name").defaultValue("spawner_name").build());
        private final Setting<String> spawnerLore = sgActions
                        .add(new StringSetting.Builder().name("Spawner Lore").defaultValue("spawner_lore").build());

        // Actions
        private final Setting<Boolean> summon = sgActions
                        .add(new BoolSetting.Builder().name("Summon").defaultValue(false).build());
        private final Setting<Boolean> spawnEgg = sgActions
                        .add(new BoolSetting.Builder().name("Spawn Egg").defaultValue(false).build());
        private final Setting<Boolean> spawner = sgActions
                        .add(new BoolSetting.Builder().name("Spawner").defaultValue(false).build());

        // Spam
        private final Setting<Boolean> spam = sgActions.add(new BoolSetting.Builder()
                        .name("Spam")
                        .description("Continuously summons mannequins to all players.")
                        .defaultValue(false)
                        .build());

        private final Setting<Integer> spamDelay = sgActions.add(new IntSetting.Builder()
                        .name("Spam Delay")
                        .description("Tick delay between summons.")
                        .defaultValue(20)
                        .min(1)
                        .visible(spam::get)
                        .build());

        private final Setting<Boolean> ignoreSelf = sgActions.add(new BoolSetting.Builder()
                        .name("Ignore Self")
                        .description("Don't spam yourself.")
                        .defaultValue(true)
                        .visible(spam::get)
                        .build());

        private final Setting<Boolean> ignoreFriends = sgActions.add(new BoolSetting.Builder()
                        .name("Ignore Friends")
                        .description("Don't spam friends.")
                        .defaultValue(true)
                        .visible(spam::get)
                        .build());

        private int timer;

        public MannequinPlus() {
                super(Trouser.operator, "MannequinPlus", "Customizable Mannequin generator.");
        }

        @EventHandler
        public void onTick(TickEvent.Post event) {
                if (summon.get()) {
                        summon.set(false);
                        doSummon(null);
                }
                if (spawnEgg.get()) {
                        spawnEgg.set(false);
                        giveSpawnEgg();
                }
                if (spawner.get()) {
                        spawner.set(false);
                        giveSpawner();
                }

                if (spam.get()) {
                        if (timer >= spamDelay.get()) {
                                timer = 0;
                                if (mc.getNetworkHandler() != null && mc.player != null) {
                                        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                                                String name = entry.getProfile().name();
                                                if (ignoreSelf.get() && name.equals(mc.player.getName().getString()))
                                                        continue;
                                                if (ignoreFriends.get() && Friends.get().isFriend(entry))
                                                        continue;

                                                doSummon(name);
                                        }
                                }
                        } else {
                                timer++;
                        }
                }
        }

        private NbtCompound createTag() {
                NbtCompound baseMannequin = createMannequinTag();

                if (agroTroll.get()) {
                        NbtCompound vindicator = new NbtCompound();
                        vindicator.putString("id", "minecraft:vindicator");

                        // Team
                        if (!team.get().isEmpty()) {
                                vindicator.putString("Team", team.get());
                        }

                        // Attributes: Scale 0.5
                        NbtList attributes = new NbtList();
                        NbtCompound scaleAttr = new NbtCompound();
                        scaleAttr.putString("id", "scale");
                        scaleAttr.putFloat("base", 0.5f);
                        attributes.add(scaleAttr);
                        vindicator.put("attributes", attributes);

                        // Invisible Effect
                        NbtList effectsList = new NbtList();
                        NbtCompound invis = new NbtCompound();
                        invis.putString("id", "invisibility");
                        invis.putInt("duration", 999999);
                        invis.putInt("amplifier", 0);
                        invis.putBoolean("ambient", true);
                        invis.putBoolean("show_particles", false);
                        effectsList.add(invis);
                        vindicator.put("active_effects", effectsList);

                        // Passengers
                        NbtList passengers = new NbtList();
                        passengers.add(baseMannequin);
                        vindicator.put("Passengers", passengers);

                        // Silent? (Optional, but user didn't specify, leaving default)
                        // HandItems: Explicitly AIR to avoid floating axe
                        NbtList handItems = new NbtList();
                        handItems.add(new NbtCompound()); // Mainhand empty
                        handItems.add(new NbtCompound()); // Offhand empty
                        vindicator.put("HandItems", handItems);

                        return vindicator;
                }

                return baseMannequin;
        }

        private NbtCompound createMannequinTag() {
                NbtCompound tag = new NbtCompound();

                tag.putString("id", "minecraft:mannequin");

                if (profileMode.get() == ProfileMode.Username) {
                        tag.putString("profile", profileName.get());
                } else if (profileMode.get() == ProfileMode.Texture) {
                        NbtCompound pTag = new NbtCompound();
                        pTag.putString("texture", profileTexture.get());
                        tag.put("profile", pTag);
                }

                tag.putString("pose", pose.get().name());
                if (immobile.get())
                        tag.putInt("immovable", 1);
                if (!description.get().isEmpty())
                        tag.putString("description", description.get());

                // CustomName
                String nameStr = MeteorStarscript.run(MeteorStarscript.compile(name.get()));
                if (!nameStr.isEmpty()) {
                        NbtList nameList = new NbtList();
                        NbtCompound nameObj = new NbtCompound();
                        nameObj.putString("text", nameStr);
                        if (bold.get())
                                nameObj.putBoolean("bold", true);
                        if (italic.get())
                                nameObj.putBoolean("italic", true);
                        if (strikethrough.get())
                                nameObj.putBoolean("strikethrough", true);
                        if (underlined.get())
                                nameObj.putBoolean("underlined", true);
                        if (obfuscated.get())
                                nameObj.putBoolean("obfuscated", true);
                        nameObj.putString("color", nameColor.get());

                        nameList.add(nameObj);
                        tag.put("CustomName", nameList);
                }

                if (nameVisible.get())
                        tag.putByte("CustomNameVisible", (byte) 1);

                // Stats
                tag.putFloat("Health", health.get().floatValue());

                // Flags
                if (canPickUpLoot.get())
                        tag.putByte("CanPickUpLoot", (byte) 1);
                if (fallFlying.get())
                        tag.putByte("FallFlying", (byte) 1);
                if (glowing.get())
                        tag.putByte("Glowing", (byte) 1);
                if (hasVisualFire.get())
                        tag.putByte("HasVisualFire", (byte) 1);
                if (invulnerable.get())
                        tag.putByte("Invulnerable", (byte) 1);
                if (leftHanded.get())
                        tag.putByte("LeftHanded", (byte) 1);
                if (noAI.get())
                        tag.putByte("NoAI", (byte) 1);
                if (noGravity.get())
                        tag.putByte("NoGravity", (byte) 1);
                if (onGround.get())
                        tag.putByte("OnGround", (byte) 1);
                if (persistenceRequired.get())
                        tag.putByte("PersistenceRequired", (byte) 1);
                if (silent.get())
                        tag.putByte("Silent", (byte) 1);

                // Rotation
                NbtList rotList = new NbtList();
                rotList.add(NbtFloat.of(rotationYaw.get().floatValue()));
                rotList.add(NbtFloat.of(rotationPitch.get().floatValue()));
                tag.put("Rotation", rotList);

                // Motion
                NbtList motionList = new NbtList();
                motionList.add(NbtDouble.of(motionX.get()));
                motionList.add(NbtDouble.of(motionY.get()));
                motionList.add(NbtDouble.of(motionZ.get()));
                tag.put("Motion", motionList);

                // Tags
                if (!tags.get().isEmpty()) {
                        NbtList tagList = new NbtList();
                        for (String t : tags.get().split(",")) {
                                tagList.add(NbtString.of(t.trim().replace("\"", "")));
                        }
                        tag.put("Tags", tagList);
                }

                // Team
                if (!team.get().isEmpty())
                        tag.putString("Team", team.get());

                // Active Effects
                if (!effects.get().isEmpty()) {
                        NbtList effectsList = new NbtList();
                        for (StatusEffect effect : effects.get()) {
                                NbtCompound eff = new NbtCompound();
                                eff.putString("id", Registries.STATUS_EFFECT.getId(effect).getPath());
                                eff.putInt("duration", effectDuration.get());
                                eff.putInt("amplifier", effectAmplifier.get());
                                eff.putBoolean("ambient", effectAmbient.get());
                                eff.putBoolean("show_particles", effectParticles.get());
                                effectsList.add(eff);
                        }
                        tag.put("active_effects", effectsList);
                }

                // Equipment
                NbtCompound equipment = new NbtCompound();
                equipment.put("mainhand", itemToNbt(mainHand.get()));
                equipment.put("offhand", itemToNbt(offHand.get()));
                equipment.put("head", itemToNbt(helmet.get()));
                equipment.put("chest", itemToNbt(chestplate.get()));
                equipment.put("legs", itemToNbt(leggings.get()));
                equipment.put("feet", itemToNbt(boots.get()));
                tag.put("equipment", equipment);

                // Attributes
                NbtList attributes = new NbtList();

                NbtCompound scaleAttr = new NbtCompound();
                scaleAttr.putString("id", "scale");
                scaleAttr.putFloat("base", scale.get().floatValue());
                attributes.add(scaleAttr);

                NbtCompound hpAttr = new NbtCompound();
                hpAttr.putString("id", "max_health");
                hpAttr.putFloat("base", maxHealth.get().floatValue());
                attributes.add(hpAttr);

                tag.put("attributes", attributes);

                return tag;
        }

        private NbtCompound itemToNbt(Item item) {
                NbtCompound tag = new NbtCompound();
                tag.putString("id", Registries.ITEM.getId(item).toString());
                return tag;
        }

        private void doSummon(String targetName) {
                if (mc.player == null)
                        return;

                if (agroTroll.get()) {
                        long idVal = System.currentTimeMillis();
                        String vinTag = "AgroVin_" + idVal;
                        String manTag = "AgroMan_" + idVal;

                        // 1. Mannequin NBT
                        NbtCompound manNbt = createMannequinTag();
                        NbtList mTags;
                        if (manNbt.contains("Tags")) {
                                mTags = (NbtList) manNbt.get("Tags");
                        } else {
                                mTags = new NbtList();
                                manNbt.put("Tags", mTags);
                        }
                        mTags.add(NbtString.of(manTag));

                        // 2. Vindicator NBT
                        NbtCompound vinNbt = new NbtCompound();
                        vinNbt.putString("id", "minecraft:vindicator");
                        vinNbt.putBoolean("Silent", true);

                        // Team Logic
                        String teamName = team.get();
                        if (teamName.isEmpty()) {
                                teamName = "Agro" + idVal;
                        }

                        // Assign Team to entities
                        vinNbt.putString("Team", teamName);
                        manNbt.putString("Team", teamName);

                        // Create Team and set collision
                        ChatUtils.sendPlayerMsg("/team add " + teamName);
                        ChatUtils.sendPlayerMsg("/team modify " + teamName + " collisionRule never");

                        // Attributes: Scale 0.5
                        NbtList attributes = new NbtList();
                        NbtCompound scaleAttr = new NbtCompound();
                        scaleAttr.putString("id", "scale");
                        scaleAttr.putFloat("base", 0.5f);
                        attributes.add(scaleAttr);
                        vinNbt.put("attributes", attributes);

                        // Invisible Effect
                        NbtList effectsList = new NbtList();
                        NbtCompound invis = new NbtCompound();
                        invis.putString("id", "invisibility");
                        invis.putInt("duration", 999999);
                        invis.putInt("amplifier", 0);
                        invis.putBoolean("ambient", true);
                        invis.putBoolean("show_particles", false);
                        effectsList.add(invis);
                        vinNbt.put("active_effects", effectsList);

                        NbtList vTags = new NbtList();
                        vTags.add(NbtString.of(vinTag));
                        vinNbt.put("Tags", vTags);

                        String prefix = "";
                        if (targetName != null) {
                                prefix = "execute at " + targetName + " run ";
                        }

                        // Execute Summons
                        // Vindicator first
                        ChatUtils.sendPlayerMsg(prefix + "/summon minecraft:vindicator ~ ~ ~ " + vinNbt.toString());
                        // Mannequin second
                        ChatUtils.sendPlayerMsg(prefix + "/summon minecraft:mannequin ~ ~ ~ " + manNbt.toString());

                        // Command Block for Teleport
                        // Added "~ ~" for rotation sync
                        String cmd = "execute at @e[tag=" + vinTag + ",limit=1] run tp @e[tag=" + manTag
                                        + ",limit=1] ~ ~ ~ ~ ~";

                        // Place repeating command block at ~ ~-2 ~
                        ChatUtils.sendPlayerMsg(
                                        prefix + "/setblock ~ ~-2 ~ repeating_command_block{auto:1,Command:\"" + cmd
                                                        + "\"} replace");

                } else {
                        NbtCompound tag = createTag();
                        String prefix = "";
                        if (targetName != null) {
                                prefix = "execute at " + targetName + " run ";
                        }
                        ChatUtils.sendPlayerMsg(prefix + "/summon minecraft:mannequin ~ ~ ~ " + tag.toString());
                }
        }

        private void giveSpawnEgg() {
                if (!creativeCheck())
                        return;

                ItemStack item = new ItemStack(Items.ALLAY_SPAWN_EGG);
                NbtCompound entityTag = createTag();
                String entityId = entityTag.getString("id").orElse("");

                var changes = ComponentChanges.builder()
                                .add(DataComponentTypes.ENTITY_DATA, TypedEntityData.create(
                                                Registries.ENTITY_TYPE.get(Identifier.tryParse(entityId)),
                                                entityTag))
                                .add(DataComponentTypes.CUSTOM_NAME,
                                                Text.literal(spawnEggName.get()).formatted(Formatting.RESET))
                                .add(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(Arrays
                                                .asList(Text.literal(spawnEggLore.get()).formatted(Formatting.RESET))))
                                .build();

                item.applyChanges(changes);
                giveItem(item);
        }

        private void giveSpawner() {
                if (!creativeCheck())
                        return;

                ItemStack item = new ItemStack(Items.COMMAND_BLOCK);

                NbtCompound blockEntityData = new NbtCompound();
                blockEntityData.putString("id", "command_block");
                blockEntityData.putInt("auto", 1);

                String spawnDataNbt = createTag().toString().replace("\"", "\\\"");
                String cmd = "/setblock ~ ~0 ~ spawner{SpawnData:{entity:" + spawnDataNbt + "}} replace";

                blockEntityData.putString("Command", cmd);

                var changes = ComponentChanges.builder()
                                .add(DataComponentTypes.BLOCK_ENTITY_DATA,
                                                TypedEntityData.create(BlockEntityType.COMMAND_BLOCK, blockEntityData))
                                .add(DataComponentTypes.CUSTOM_NAME,
                                                Text.literal(spawnerName.get()).formatted(Formatting.RESET))
                                .add(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(Arrays
                                                .asList(Text.literal(spawnerLore.get()).formatted(Formatting.RESET))))
                                .build();

                item.applyChanges(changes);
                giveItem(item);
        }

        private boolean creativeCheck() {
                if (mc.player == null || mc.interactionManager == null)
                        return false;
                if (!mc.player.getAbilities().creativeMode) {
                        error("Creative mode required for this action.");
                        return false;
                }
                return true;
        }

        private void giveItem(ItemStack item) {
                if (mc.player == null || mc.interactionManager == null)
                        return;
                int slot = 36 + mc.player.getInventory().selectedSlot;
                mc.interactionManager.clickCreativeStack(item, slot);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP,
                                mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP,
                                mc.player);
                info("Created Mannequin item.");
        }
}
