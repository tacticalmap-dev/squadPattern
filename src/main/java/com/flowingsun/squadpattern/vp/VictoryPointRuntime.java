package com.flowingsun.squadpattern.vp;

import com.flowingsun.squadpattern.SquadPatternMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VictoryPointRuntime {
    private VictoryPointRuntime() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SquadPatternMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SquadPatternMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SquadPatternMod.MOD_ID);

    public static final RegistryObject<Block> VICTORY_POINT = BLOCKS.register("victory_point",
            () -> new VictoryPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.METAL), CapturePointType.VICTORY));

    public static final RegistryObject<Block> NORMAL_POINT = BLOCKS.register("normal_point",
            () -> new VictoryPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.STONE), CapturePointType.NORMAL));

    public static final RegistryObject<Block> AMMO_POINT = BLOCKS.register("ammo_point",
            () -> new VictoryPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.METAL), CapturePointType.AMMO));

    public static final RegistryObject<Block> OIL_POINT = BLOCKS.register("oil_point",
            () -> new VictoryPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.DEEPSLATE), CapturePointType.OIL));

    public static final RegistryObject<Item> VICTORY_POINT_ITEM = ITEMS.register("victory_point",
            () -> new BlockItem(VICTORY_POINT.get(), new Item.Properties()));

    public static final RegistryObject<Item> NORMAL_POINT_ITEM = ITEMS.register("normal_point",
            () -> new BlockItem(NORMAL_POINT.get(), new Item.Properties()));

    public static final RegistryObject<Item> AMMO_POINT_ITEM = ITEMS.register("ammo_point",
            () -> new BlockItem(AMMO_POINT.get(), new Item.Properties()));

    public static final RegistryObject<Item> OIL_POINT_ITEM = ITEMS.register("oil_point",
            () -> new BlockItem(OIL_POINT.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<VictoryPointBlockEntity>> BE_TYPE = BES.register("victory_point",
            () -> BlockEntityType.Builder.of(VictoryPointBlockEntity::new,
                    VICTORY_POINT.get(),
                    NORMAL_POINT.get(),
                    AMMO_POINT.get(),
                    OIL_POINT.get()
            ).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BES.register(bus);
    }
}
