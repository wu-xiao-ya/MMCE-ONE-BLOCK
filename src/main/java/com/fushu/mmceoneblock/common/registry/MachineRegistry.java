package com.fushu.mmceoneblock.common.registry;

import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.config.MachineConfigLoader;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.config.SingleBlockMachineTileFactory;
import com.fushu.mmceoneblock.common.item.ItemBlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MachineRegistry {
    public static final String MODID = MachineConfigLoader.MODID;
    private static final Logger LOGGER = LogManager.getLogger(MODID);
    private static final Map<String, MachineEntry> ENTRIES = new LinkedHashMap<String, MachineEntry>();
    private static boolean bootstrapped = false;
    private static boolean tileRegistered = false;

    private MachineRegistry() {
    }

    public static synchronized void bootstrap() {
        bootstrap(null);
    }

    public static synchronized void bootstrap(SingleBlockMachineTileFactory tileFactory) {
        if (bootstrapped) {
            return;
        }

        registerTileEntity();
        List<MachineDefinition> definitions = MachineConfigLoader.loadAll();
        prepare(definitions, tileFactory);
        bootstrapped = true;
    }

    public static synchronized void registerBlocks(IForgeRegistry<Block> registry) {
        bootstrap();
        for (MachineEntry entry : ENTRIES.values()) {
            registry.register(entry.block);
        }
    }

    public static synchronized void registerItems(IForgeRegistry<Item> registry) {
        bootstrap();
        for (MachineEntry entry : ENTRIES.values()) {
            registry.register(entry.item);
        }
    }

    public static synchronized void prepare(List<MachineDefinition> definitions, SingleBlockMachineTileFactory tileFactory) {
        Map<String, MachineEntry> nextEntries = new LinkedHashMap<String, MachineEntry>();
        List<MachineDefinition> enabledDefinitions = filterEnabledMachineDefinitions(definitions);
        if (enabledDefinitions.isEmpty()) {
            ENTRIES.clear();
            return;
        }
        if (tileFactory == null) {
            tileFactory = new SingleBlockMachineTileFactory() {
                @Override
                public TileEntity create(MachineDefinition definition) {
                    return new TileSingleBlockMachineController(null, definition.getId());
                }
            };
        }

        for (MachineDefinition definition : enabledDefinitions) {
            String rawId = definition.getId();
            String id = normalizeId(rawId);
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Machine definition is missing a valid id: " + definition.getSourceFile());
            }
            if (rawId == null || !rawId.trim().equals(id) || id.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Machine definition has an illegal id: " + rawId);
            }
            if (nextEntries.containsKey(id)) {
                throw new IllegalStateException("Duplicate machine id '" + definition.getId() + "'");
            }
            MachineEntry entry = registerOne(definition, tileFactory);
            nextEntries.put(id, entry);
        }
        ENTRIES.clear();
        ENTRIES.putAll(nextEntries);
    }

    static List<MachineDefinition> filterEnabledMachineDefinitions(List<MachineDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<MachineDefinition> out = new ArrayList<MachineDefinition>();
        for (MachineDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if (!definition.isEnabled()) {
                LOGGER.info("Machine {} is disabled and will not be registered", definition.getId());
                continue;
            }
            out.add(definition);
        }
        return out;
    }

    public static synchronized void validateLoadedMachines() {
        int removed = 0;
        Iterator<Map.Entry<String, MachineEntry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            MachineEntry entry = iterator.next().getValue();
            MachineDefinition definition = entry.definition;
            DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineRegistry
                .getRegistry()
                .getMachine(definition.getMachine());
            if (machine == null) {
                LOGGER.error(
                    "Skipping one-block machine '{}' because backing MMCE machine '{}' is not loaded. Source: {}",
                    definition.getId(),
                    definition.getMachine(),
                    definition.getSourceFile()
                );
                iterator.remove();
                removed++;
                continue;
            }
            if (machine.isFactoryOnly()) {
                LOGGER.warn(
                    "Skipping one-block machine '{}' because backing MMCE machine '{}' is factory-only. Source: {}",
                    definition.getId(),
                    definition.getMachine(),
                    definition.getSourceFile()
                );
                iterator.remove();
                removed++;
            }
        }
        LOGGER.info(
            "Validated {} one-block machine definition(s) against loaded MMCE machines ({} skipped)",
            ENTRIES.size(),
            removed
        );
    }

    static List<MachineDefinition> filterKnownMachineDefinitions(List<MachineDefinition> definitions,
                                                                 MachineExistenceChecker checker) {
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<MachineDefinition> out = new ArrayList<MachineDefinition>();
        for (MachineDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if (checker == null || checker.exists(definition.getMachine())) {
                out.add(definition);
                continue;
            }
            LOGGER.error(
                "Skipping one-block machine '{}' because backing MMCE machine '{}' is not loaded. Source: {}",
                definition.getId(),
                definition.getMachine(),
                definition.getSourceFile()
            );
        }
        return out;
    }

    @Nullable
    public static MachineDefinition getDefinition(String id) {
        MachineEntry entry = ENTRIES.get(normalizeId(id));
        return entry == null ? null : entry.definition;
    }

    @Nullable
    public static BlockSingleBlockMachineController getBlock(String id) {
        MachineEntry entry = ENTRIES.get(normalizeId(id));
        return entry == null ? null : entry.block;
    }

    @Nullable
    public static ItemBlockSingleBlockMachineController getItem(String id) {
        MachineEntry entry = ENTRIES.get(normalizeId(id));
        return entry == null ? null : entry.item;
    }

    public static List<MachineDefinition> getDefinitions() {
        List<MachineDefinition> out = new ArrayList<MachineDefinition>();
        for (MachineEntry entry : ENTRIES.values()) {
            out.add(entry.definition);
        }
        return Collections.unmodifiableList(out);
    }

    public static Map<String, MachineEntry> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, MachineEntry>(ENTRIES));
    }

    private static MachineEntry registerOne(MachineDefinition definition, SingleBlockMachineTileFactory tileFactory) {
        BlockSingleBlockMachineController block = new BlockSingleBlockMachineController(definition, tileFactory);
        ItemBlockSingleBlockMachineController item = block.createItemBlock();
        return new MachineEntry(definition, block, item);
    }

    private static void registerTileEntity() {
        if (tileRegistered) {
            return;
        }
        GameRegistry.registerTileEntity(TileSingleBlockMachineController.class,
            new ResourceLocation(MODID, "single_block_machine_controller"));
        tileRegistered = true;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    interface MachineExistenceChecker {
        boolean exists(ResourceLocation machine);
    }

    public static final class MachineEntry {
        private final MachineDefinition definition;
        private final BlockSingleBlockMachineController block;
        private final ItemBlockSingleBlockMachineController item;

        private MachineEntry(MachineDefinition definition,
                             BlockSingleBlockMachineController block,
                             ItemBlockSingleBlockMachineController item) {
            this.definition = definition;
            this.block = block;
            this.item = item;
        }

        public MachineDefinition getDefinition() {
            return definition;
        }

        public BlockSingleBlockMachineController getBlock() {
            return block;
        }

        public ItemBlockSingleBlockMachineController getItem() {
            return item;
        }
    }
}
