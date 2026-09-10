package me.matl114.hacks.modules.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.matl114.commands.MainCommand;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.CommandUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.TabResult;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class ConfigManager extends BaseModule {
    public ConfigManager() {
        super("ConfigManager");
    }

    private final ModulePath root = makePath(Configs.MISC_CONFIG, "config");
    private final ListRef privacyPathKeywords = builder(root.add("privacy-protection-path-keywords"), ListRef.TYPE)
            .defaultValue(List.of("chat", "http"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapConfigCommand);
    }

    public void bootStrapConfigCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name("config").build();
        SimpleCommandArgs.Argument configNameArgument = SimpleCommandArgs.argumentBuilder()
                .name("config_name")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Config.REGISTRY.stream()
                        .map(Config::getRegistryKey)
                        .filter(Objects::nonNull)
                        .map(registryKey -> registryKey.getValue().toString())))
                .build();

        SimpleCommandArgs.Argument configOrAllNameArgument = SimpleCommandArgs.argumentBuilder()
                .name("config_name")
                .defaultValue("all")
                .select("all")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Config.REGISTRY.stream()
                        .map(Config::getRegistryKey)
                        .filter(Objects::nonNull)
                        .map(registryKey -> registryKey.getValue().toString())))
                .build();
        SimpleCommandArgs.Argument pathArgument = SimpleCommandArgs.argumentBuilder()
                .name("path")
                .tabCompletor(TabResult.ofDispatcher((sender, configName) -> {
                    Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
                    if (config == null) {
                        return Stream.empty();
                    }
                    return Stream.concat(config.getVisiblePaths().stream(), config.getPaths().stream())
                            .distinct()
                            .sorted();
                }))
                .build();
        SimpleCommandArgs.Argument manualPathArgument = SimpleCommandArgs.argumentBuilder()
                .name("path")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Stream.of("<Enter path>")))
                .build();
        SimpleCommandArgs.Argument pathPrefixArgument = SimpleCommandArgs.argumentBuilder()
                .name("path_prefix")
                .defaultValue("")
                .tabCompletor(TabResult.ofDispatcher((sender, configName) -> getPathPrefixSuggestions(configName)))
                .build();
        SimpleCommandArgs.Argument fileLoadArgument = SimpleCommandArgs.argumentBuilder()
                .name("path")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Stream.of("<Enter path>")))
                .tabCompletor(
                        TabResult.ofStreamSupplier(CommandUtils.fileSupplier(FileManager.CONFIG_SAVE_FOLDER, (sx) -> {
                            return sx.endsWith(".nbt") || sx.endsWith(".dat");
                        })))
                .build();
        main.subBuilder(SubCommand.taskBuilder())
                .name("open")
                .helper("message.command.config.open.help")
                .post(e -> e.executor(CommandContext.run(this::onOpen)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("reload")
                .helper("message.command.config.reload.help")
                .post(e -> e.executor(CommandContext.run(this::onReload)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("openfolder")
                .helper("message.command.config.openfolder.help")
                .post(s -> s.executor(CommandContext.run(this::onOpenFolder)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("save")
                .helper("message.command.config.save.help")
                .arg(manualPathArgument)
                .arg(configOrAllNameArgument)
                .arg(pathPrefixArgument)
                .post(e -> e.executor(CommandContext.run(this::onSave)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("savemodule")
                .helper("message.command.config.savemodule.help")
                .arg(manualPathArgument)
                .post(e -> e.executor(new CommandContext() {
                    @Override
                    public boolean execute(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        onSaveModule(streamArgs, argsReader);
                        return true;
                    }

                    @Override
                    public List<String> supplyTab(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        String[] remainingArgs = argsReader.getRemainingArgs();
                        ;
                        String lastArg = remainingArgs.length > 0 ? remainingArgs[remainingArgs.length - 1] : "";
                        return getModuleNameSuggestions()
                                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lastArg.toLowerCase(Locale.ROOT)))
                                .toList();
                    }
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("load")
                .helper("message.command.config.load.help")
                .arg(fileLoadArgument)
                .arg(configOrAllNameArgument)
                .arg(pathPrefixArgument)
                .post(e -> e.executor(CommandContext.run(this::onLoad)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("loadmodule")
                .helper("message.command.config.loadmodule.help")
                .arg(fileLoadArgument)
                .post(e -> e.executor(new CommandContext() {
                    @Override
                    public boolean execute(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        onLoadModule(streamArgs, argsReader);
                        return true;
                    }

                    @Override
                    public List<String> supplyTab(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        String[] remainingArgs = argsReader.getRemainingArgs();
                        ;
                        String lastArg = remainingArgs.length > 0 ? remainingArgs[remainingArgs.length - 1] : "";
                        return getModuleNameSuggestions()
                                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lastArg.toLowerCase(Locale.ROOT)))
                                .toList();
                    }
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("set")
                .helper("message.command.config.set.help")
                .arg(configNameArgument)
                .arg(pathArgument)
                .arg(SimpleCommandArgs.argumentBuilder().name("string").build())
                .post(e -> e.executor(CommandContext.run(this::onSet)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("reset")
                .helper("message.command.config.reset.help")
                .arg(configNameArgument)
                .arg(pathArgument)
                .post(e -> e.executor(CommandContext.run(this::onReset)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("resetall")
                .helper("message.command.config.resetall.help")
                .arg(configOrAllNameArgument)
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("confirm")
                        .defaultValue("")
                        .select("--confirm")
                        .build())
                .post(e -> e.executor(CommandContext.run(this::onResetAll)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("resetmodule")
                .helper("message.command.config.resetmodule.help")
                .post(e -> e.executor(new CommandContext() {
                    @Override
                    public boolean execute(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        onResetModule(streamArgs, argsReader);
                        return true;
                    }

                    @Override
                    public List<String> supplyTab(
                            me.matl114.utils.commands.params.api.CommandExecution sender,
                            ArgumentInputStream streamArgs,
                            me.matl114.utils.commands.params.ArgumentReader argsReader) {
                        String[] remainingArgs = argsReader.getRemainingArgs();
                        ;
                        String lastArg = remainingArgs.length > 0 ? remainingArgs[remainingArgs.length - 1] : "";
                        return getModuleNameSuggestions()
                                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lastArg.toLowerCase(Locale.ROOT)))
                                .toList();
                    }
                }))
                .complete();
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.config-manager.command", 0, dblank, dx, dy));
    }

    public void onOpen() {
        Tasks.scheduleDelayed(MainTasks::openConfigNewStyleScreen, 1);
        Debug.chat(Text.literal("Opened the config screen successfully").formatted(Formatting.GREEN));
    }

    public void onReload() {
        Tasks.scheduleDelayed(Config::reloadAll, 1);
        Debug.chat(Text.literal("Reloaded the config file successfully").formatted(Formatting.GREEN));
    }

    public void onOpenFolder() {
        Util.getOperatingSystem().open(FileManager.CONFIG_SAVE_FOLDER);
        Debug.chat(Text.literal("Opened the config save and import folder successfully")
                .formatted(Formatting.GREEN));
    }

    public void onSet(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String rawPath = args.nextNonnullString();
        String value = args.nextNonnullString();

        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            Debug.chat(Text.literal("Config file not found: " + configName).formatted(Formatting.RED));
            return;
        }

        Ref<?> ref = config.get(rawPath.split("\\."));
        if (ref == null) {
            Debug.chat(Text.literal("Config entry not found: " + configName + "." + rawPath)
                    .formatted(Formatting.RED));
            return;
        }

        AttrKeyValue<?> keyValue = ref.createKeyValue(rawPath);
        keyValue.valueChange(this, value);
        if (!keyValue.isValidate()) {
            Debug.chat(Text.literal("Invalid config entry format: " + configName + "." + rawPath)
                    .formatted(Formatting.RED));
            return;
        }
        Debug.chat(Text.literal("Config entry set successfully: " + configName + "." + rawPath)
                .formatted(Formatting.GREEN));
    }

    public void onReset(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String rawPath = args.nextNonnullString();

        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            Debug.chat(Text.literal("Config file not found: " + configName).formatted(Formatting.RED));
            return;
        }

        Ref<?> ref = config.get(rawPath.split("\\."));
        if (ref == null) {
            Debug.chat(Text.literal("Config entry not found: " + configName + "." + rawPath)
                    .formatted(Formatting.RED));
            return;
        }
        if (!ref.hasDefaultValue()) {
            Debug.chat(Text.literal("Config entry has no default value: " + configName + "." + rawPath)
                    .formatted(Formatting.RED));
            return;
        }

        ref.resetValue();
        Debug.chat(Text.literal("Config entry reset successfully: " + configName + "." + rawPath)
                .formatted(Formatting.GREEN));
    }

    public void onResetModule(ArgumentInputStream args, ArgumentReader reader) {
        List<BaseModule> baseModules = readModuleArguments(reader);
        if (baseModules.isEmpty()) {
            return;
        }
        for (var re : baseModules) {
            for (var ref : re.getEditableConfig()) {
                ref.ref().resetValue();
            }
            Debug.chat(Text.literal("Module config entry reset successfully: " + re.getName())
                    .formatted(Formatting.GREEN));
        }
    }

    public void onResetAll(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String confirm = args.nextNonnullString();
        if ("--confirm".equals(confirm)) {
            if ("all".equalsIgnoreCase(configName)) {
                for (var config : Config.REGISTRY) {
                    for (var path : config.getVisiblePaths()) {
                        var ref = config.get(Config.cutToPath(path));
                        if (ref != null && ref.hasDefaultValue()) {
                            ref.resetValue();
                        }
                    }
                }
            } else {
                var config = Config.REGISTRY.get(Identifier.tryParse(configName));
                if (config == null) {
                    Debug.chat(
                            Text.literal("Config file not found: " + configName).formatted(Formatting.RED));
                    return;
                }
                for (var path : config.getVisiblePaths()) {
                    var ref = config.get(Config.cutToPath(path));
                    if (ref != null && ref.hasDefaultValue()) {
                        ref.resetValue();
                    }
                }
            }
        } else {
            Debug.chat(
                    ChatUtils.stringToText("&cThis command will reset some config files, confirm? "),
                    Text.literal("[Confirm]")
                            .formatted(Formatting.RED)
                            .formatted(Formatting.BOLD)
                            .styled(style -> style.withClickEvent(ChatUtils.getSuggestCommand(
                                    MainCommand.MAIN_PREFIX + "config resetall " + configName + " --confirm"))));
        }
    }

    public static final Codec<MapRef> CONFIG_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> {
                Object value = dynamic.convert(ConfigOp.INSTANCE).getValue();
                if (value instanceof MapRef mapRef) {
                    return DataResult.success(mapRef);
                }
                return DataResult.error(() -> "Config payload is not a MapRef: " + value);
            },
            mapRef -> new Dynamic<>(ConfigOp.INSTANCE, mapRef));

    public static record ConfigSnapshot(Map<Identifier, MapRef> snapSnot) {
        public static final Codec<ConfigSnapshot> CODEC =
                Codec.unboundedMap(Identifier.CODEC, CONFIG_CODEC).xmap(ConfigSnapshot::new, ConfigSnapshot::snapSnot);
    }

    public void onSave(ArgumentInputStream args) {
        String rawPath = args.nextNonnullString();
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            return;
        }
        if (FileManager.getInstance().hasConfigStorage(fileName)) {
            Debug.chat(Text.literal("Config file already exists: " + fileName).formatted(Formatting.RED));
            Debug.chat(Text.literal("Click this text to open the folder to view or rename")
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style.withClickEvent(ChatUtils.getOpenFile(FileManager.CONFIG_SAVE_FOLDER))));
            return;
        }

        String allName = args.nextNonnullString();
        String pathPrefix = args.nextNonnullString().trim();
        ConfigSnapshot snapshot;
        if ("all".equalsIgnoreCase(allName)) {
            List<String> privacyKeywords = privacyPathKeywords.get();

            Map<Identifier, MapRef> snapshotMap = new LinkedHashMap<>();
            for (Config config : Config.REGISTRY) {
                if (config.getRegistryKey() == null) {
                    continue;
                }
                Identifier identifier = config.getRegistryKey().getValue();
                if (isPrivacyConfig(identifier, privacyKeywords)) {
                    Debug.chat(Text.literal("Skipped config while saving: " + identifier
                                    + " to avoid leaking private information (keywords can be adjusted in settings)")
                            .formatted(Formatting.YELLOW));
                    continue;
                }
                snapshotMap.put(identifier, filterSnapshotByPathPrefix(config, pathPrefix));
            }

            snapshot = new ConfigSnapshot(snapshotMap);
        } else {
            Config config = Config.REGISTRY.get(Identifier.tryParse(allName));
            if (config == null) {
                Debug.chat(Text.literal("Config file not found: " + allName).formatted(Formatting.RED));
                return;
            }
            Map<Identifier, MapRef> snapshotMap = new LinkedHashMap<>();
            Identifier identifier = config.getRegistryKey().getValue();
            snapshotMap.put(identifier, filterSnapshotByPathPrefix(config, pathPrefix));
            snapshot = new ConfigSnapshot(snapshotMap);
        }

        DataResult<me.matl114.managers.config.Ref<?>> encoded =
                ConfigSnapshot.CODEC.encodeStart(ConfigOp.INSTANCE, snapshot);
        save(fileName, snapshot);
    }

    public void onSaveModule(ArgumentInputStream args, me.matl114.utils.commands.params.ArgumentReader argsReader) {
        String rawPath = args.nextNonnullString();
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            return;
        }
        if (FileManager.getInstance().hasConfigStorage(fileName)) {
            Debug.chat(Text.literal("Config file already exists: " + fileName).formatted(Formatting.RED));
            Debug.chat(Text.literal("Click this text to open the folder to view or rename")
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style.withClickEvent(ChatUtils.getOpenFile(FileManager.CONFIG_SAVE_FOLDER))));
            return;
        }
        List<BaseModule> baseModules = readModuleArguments(argsReader);
        if (baseModules.isEmpty()) {
            return;
        }
        Map<Identifier, MapRef> snapshotMap = new LinkedHashMap<>();
        for (BaseModule baseModule : baseModules) {
            for (var entry : baseModule.getEditableConfig()) {
                var config = entry.config();
                var path = entry.path();
                var ff = entry.ref();
                snapshotMap
                        .computeIfAbsent(config.getRegistryKey().getValue(), k -> new MapRef())
                        .setValue(ff, path);
            }
        }

        List<String> privacyKeywords = privacyPathKeywords.get();
        for (var entry : new HashSet<>(snapshotMap.keySet())) {
            if (isPrivacyConfig(entry, privacyKeywords)) {
                Debug.chat(Text.literal("Skipped config while saving: " + entry
                                + " to avoid leaking private information (keywords can be adjusted in settings)")
                        .formatted(Formatting.YELLOW));
                snapshotMap.remove(entry);
            }
        }
        ConfigSnapshot snapshot = new ConfigSnapshot(snapshotMap);
        save(fileName, snapshot);
    }

    public void save(String fileName, ConfigSnapshot snapshot) {
        DataResult<me.matl114.managers.config.Ref<?>> encoded =
                ConfigSnapshot.CODEC.encodeStart(ConfigOp.INSTANCE, snapshot);
        if (encoded.isError()) {
            String message = encoded.error().map(DataResult.Error::message).orElse("Unknown encoding error");
            Debug.chat(
                    Text.literal("Failed to save config snapshot: " + message).formatted(Formatting.RED));
            return;
        }

        try (FileStorage storage =
                FileManager.getInstance().getConfigStorage(fileName).asAutoSave()) {
            storage.write(encoded.result().get(), ConfigOp.INSTANCE);
            Debug.chat(Text.literal(
                            "Config snapshot saved successfully: " + fileName + " ,click this text to open the folder")
                    .formatted(Formatting.GREEN)
                    .styled(style -> style.withClickEvent(
                            ChatUtils.getOpenFile(storage.getFile().getParentFile()))));
        }
    }

    public void onLoad(ArgumentInputStream args) {
        String rawPath = args.nextArg();
        if (rawPath == null) {
            promptSnapshotFolderImport();
            return;
        }
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            promptSnapshotFolderImport();
            return;
        }
        String name = args.nextNonnullString();
        String prefix = args.nextNonnullString();
        Config config = null;
        if (!"all".equalsIgnoreCase(name)) {
            config = Config.REGISTRY.get(Identifier.tryParse(name));
            if (config == null) {
                Debug.chat(Text.literal("Config file not found: " + name).formatted(Formatting.RED));
                return;
            }
        }
        var snapshot = load(fileName);
        if (snapshot == null) return;
        if (config == null) {
            for (Map.Entry<Identifier, MapRef> entry : snapshot.snapSnot().entrySet()) {
                Config config2 = Config.REGISTRY.get(entry.getKey());
                if (config2 == null) {
                    Debug.chat(Text.literal("Skipped unregistered config: " + entry.getKey())
                            .formatted(Formatting.YELLOW));
                    continue;
                }
                for (LeafEntry leaf : flattenMapRef(entry.getValue())) {
                    String pathStr = String.join(".", leaf.path());
                    if (pathStr.startsWith(prefix)) {
                        Ref<?> currentRef = config2.get(leaf.path());
                        if (currentRef == null) {
                            continue;
                        }
                        currentRef.copyValueFrom(leaf.value());
                    }
                }
            }
        } else {
            MapRef mapRef2 = snapshot.snapSnot().get(config.getRegistryKey().getValue());
            if (mapRef2 != null) {
                for (LeafEntry leaf : flattenMapRef(mapRef2)) {
                    String pathStr = String.join(".", leaf.path());
                    if (pathStr.startsWith(prefix)) {
                        Ref<?> currentRef = config.get(leaf.path());
                        if (currentRef == null) {
                            continue;
                        }
                        currentRef.copyValueFrom(leaf.value());
                    }
                }
            }
        }

        Debug.chat(
                Text.literal("Config snapshot loaded successfully" + fileName).formatted(Formatting.GREEN));
    }

    public ConfigSnapshot load(String fileName) {
        try (FileStorage storage = FileManager.getInstance().getConfigStorage(fileName, true, false)) {
            if (storage == null) {
                Debug.chat(Text.literal("Config snapshot does not exist: " + fileName)
                        .formatted(Formatting.RED));
                promptSnapshotFolderImport();
                return null;
            }
            storage.read();
            Ref<?> rawSnapshot = storage.asReadOnly(ConfigOp.INSTANCE);
            DataResult<ConfigSnapshot> decoded = ConfigSnapshot.CODEC.parse(ConfigOp.INSTANCE, rawSnapshot);
            if (decoded.isError()) {
                String message = decoded.error().map(DataResult.Error::message).orElse("Unknown decoding error");
                Debug.chat(Text.literal("Failed to load config snapshot: " + message)
                        .formatted(Formatting.RED));
                return null;
            }

            ConfigSnapshot snapshot = decoded.result().get();
            snapshot = portConfigs(snapshot);
            return snapshot;
        }
    }

    public void onLoadModule(ArgumentInputStream args, me.matl114.utils.commands.params.ArgumentReader argsReader) {
        String rawPath = args.nextArg();
        if (rawPath == null) {
            promptSnapshotFolderImport();
            return;
        }
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            promptSnapshotFolderImport();
            return;
        }
        List<BaseModule> baseModules = readModuleArguments(argsReader);
        if (baseModules.isEmpty()) {
            return;
        }

        var snapshot = load(fileName);
        if (snapshot == null) return;
        for (BaseModule baseModule : baseModules) {
            for (var entry : baseModule.getEditableConfig()) {
                var config = entry.config();
                var path = entry.path();
                var ff = entry.ref();
                var refMap = snapshot.snapSnot.get(config.getRegistryKey().getValue());
                if (refMap != null) {
                    var ref = refMap.get(path);
                    if (ref != null) {
                        ff.copyValueFrom(ref);
                    }
                }
            }
        }
        Debug.chat(
                Text.literal("Config snapshot loaded successfully" + fileName).formatted(Formatting.GREEN));
    }

    private List<BaseModule> readModuleArguments(me.matl114.utils.commands.params.ArgumentReader argsReader) {
        Map<String, BaseModule> moduleMap = HackModules.getModuleGroups().stream()
                .flatMap(s -> s.getModules().stream())
                .collect(Collectors.toMap(s -> s.getName().toLowerCase(Locale.ROOT), b -> b));
        List<BaseModule> result = new ArrayList<>();
        for (String moduleName : argsReader.getRemainingArgs()) {
            if (moduleName != null && !moduleName.isBlank()) {
                addModuleArgument(result, moduleMap, moduleName);
            }
        }
        return result;
    }

    private void addModuleArgument(List<BaseModule> result, Map<String, BaseModule> moduleMap, String moduleName) {
        BaseModule baseModule = moduleMap.get(moduleName.toLowerCase(Locale.ROOT));
        if (baseModule == null) {
            Debug.chat(Text.literal("Module not found: " + moduleName).formatted(Formatting.RED));
            return;
        }
        if (!result.contains(baseModule)) {
            result.add(baseModule);
        }
    }

    private Stream<String> getModuleNameSuggestions() {
        return HackModules.getModuleGroups().stream()
                .flatMap(s -> s.getModules().stream().map(BaseModule::getName))
                .distinct()
                .sorted();
    }

    private Stream<String> getPathPrefixSuggestions(String configName) {
        if ("all".equalsIgnoreCase(configName)) {
            return Config.REGISTRY.stream()
                    .flatMap(config -> config.getVisiblePaths().stream())
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted();
        }
        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            return Stream.empty();
        }
        return config.getVisiblePaths().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted();
    }

    private MapRef filterSnapshotByPathPrefix(Config config, String pathPrefix) {
        if (pathPrefix.isEmpty()) {
            return config.asRef();
        }
        String normalizedPathPrefix =
                pathPrefix.endsWith(".") ? pathPrefix.substring(0, pathPrefix.length() - 1) : pathPrefix;
        MapRef filteredSnapshot = new MapRef();
        for (LeafEntry leaf : flattenMapRef(config.asRef())) {
            String leafPath = String.join(".", leaf.path());
            if (leafPath.equals(normalizedPathPrefix) || leafPath.startsWith(normalizedPathPrefix + ".")) {
                filteredSnapshot.setValue(leaf.value(), leaf.path());
            }
        }
        return filteredSnapshot;
    }

    private boolean isPrivacyConfig(Identifier identifier, List<String> privacyKeywords) {
        String path = identifier.getPath();
        return privacyKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .anyMatch(path::contains);
    }

    private void promptSnapshotFolderImport() {
        Debug.chat(Text.literal(
                        "Drag the saved config file into the config snapshot directory, or click this text to open the folder")
                .formatted(Formatting.YELLOW)
                .styled(style -> style.withClickEvent(ChatUtils.getOpenFile(FileManager.CONFIG_SAVE_FOLDER))));
    }

    private static List<LeafEntry> flattenMapRef(MapRef mapRef) {
        List<LeafEntry> result = new ArrayList<>();
        flattenMapRef(result, new ArrayList<>(), mapRef);
        return result;
    }

    private static void flattenMapRef(List<LeafEntry> result, List<String> path, MapRef mapRef) {
        for (Map.Entry<String, Ref<?>> entry : mapRef.getValue().entrySet()) {
            path.add(entry.getKey());
            Ref<?> value = entry.getValue();
            if (value instanceof MapRef child) {
                flattenMapRef(result, path, child);
            } else {
                result.add(new LeafEntry(path.toArray(String[]::new), value));
            }
            path.remove(path.size() - 1);
        }
    }

    private record LeafEntry(String[] path, Ref<?> value) {}

    private static String normalizeSnapshotFileName(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Config snapshot name cannot be empty");
        }
        if (path.contains("/") || path.contains("\\")) {
            throw new IllegalArgumentException("Config snapshot name cannot contain path separators");
        }

        int suffixIndex = path.lastIndexOf('.');
        String baseName = suffixIndex > 0 ? path.substring(0, suffixIndex) : path;
        if (baseName.isEmpty() || ".".equals(baseName) || "..".equals(baseName)) {
            throw new IllegalArgumentException("Config snapshot name is not a valid file name");
        }

        for (int i = 0; i < baseName.length(); i++) {
            char ch = baseName.charAt(i);
            if (ch < 32 || "<>:\"/\\|?*".indexOf(ch) >= 0) {
                throw new IllegalArgumentException("Config snapshot name is not a valid file name: " + rawPath);
            }
        }
        return baseName + ".nbt";
    }

    private static ConfigSnapshot portConfigs(ConfigSnapshot snapshot) {
        if (portPaths.isEmpty()) return snapshot;
        Map<Identifier, MapRef> copyMap = new LinkedHashMap<>(snapshot.snapSnot());
        boolean modify = false;
        for (var re : portPaths.entrySet()) {
            ModulePath from = re.getKey();
            ModulePath to = re.getValue();
            Identifier fromId = from.getConfig().getRegistryKey().getValue();
            Identifier toId = to.getConfig().getRegistryKey().getValue();
            if (copyMap.containsKey(fromId)) {
                MapRef ref = copyMap.get(fromId);
                var section = ref.get(from.toPath());
                if (section != null) {
                    modify = true;
                    MapRef toRef = copyMap.computeIfAbsent(toId, (vvv) -> new MapRef());
                    ref.setValue(null, from.toPath());
                    toRef.setValue(section, to.toPath());
                }
            }
        }
        if (modify) {
            return new ConfigSnapshot(copyMap);
        } else {
            return snapshot;
        }
    }
}
