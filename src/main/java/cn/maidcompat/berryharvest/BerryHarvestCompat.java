package cn.maidcompat.berryharvest;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.crop.SpecialCropManager;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod(BerryHarvestCompat.MOD_ID)
@LittleMaidExtension
public final class BerryHarvestCompat implements ILittleMaid {
    public static final String MOD_ID = "tlm_berry_harvest_compat";
    private static final ResourceLocation FARM_TASK = new ResourceLocation("touhou_little_maid", "farm");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GameProfile HARVESTER_PROFILE = new GameProfile(
            UUID.fromString("dfc9238a-99d9-4acf-bf8c-b10c785ec461"), "MaidBerryHarvester");
    private static final BerryCropHandler HANDLER = new BerryCropHandler();
    private static final Set<Block> REGISTERED_BERRIES = new HashSet<>();

    public BerryHarvestCompat() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("女仆浆果采摘兼容已加载");
    }

    @Override
    public void registerSpecialCropHandler(SpecialCropManager register) {
        REGISTERED_BERRIES.clear();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            String path = id.getPath();
            if (isFruitCropName(path) && HANDLER.findHarvestProperty(state) != null) {
                register.addCrop(block, HANDLER);
                REGISTERED_BERRIES.add(block);
                LOGGER.info("已向女仆农场模式注册右键果实作物：{}", id);
            } else if (HANDLER.hasBerriesProperty(state)) {
                register.addCrop(block, HANDLER);
                REGISTERED_BERRIES.add(block);
                LOGGER.info("已向女仆农场模式注册右键作物：{}", id);
            }
        }
        LOGGER.info("女仆浆果兼容注入完成，共注册 {} 个方块", REGISTERED_BERRIES.size());
    }

    @SubscribeEvent
    public void onMaidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel level)
                || maid.tickCount % 10 != 0
                || !FARM_TASK.equals(maid.getTask().getUid())
                || REGISTERED_BERRIES.isEmpty()) {
            return;
        }

        BlockPos origin = maid.blockPosition();
        BlockPos target = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 4, 8))) {
            BlockState state = level.getBlockState(pos);
            if (!REGISTERED_BERRIES.contains(state.getBlock()) || !HANDLER.canHarvest(maid, pos, state)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < nearest) {
                nearest = distance;
                target = pos.immutable();
            }
        }

        if (target == null) {
            return;
        }
        if (nearest <= 9.0D) {
            HANDLER.harvest(maid, target, level.getBlockState(target), false);
            maid.swing(InteractionHand.MAIN_HAND);
        } else {
            maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.6D);
            maid.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        }
    }

    private static boolean isFruitCropName(String path) {
        String[] fruitNames = {
                "berry", "berries", "melon", "fruit", "apple", "grape", "banana",
                "currant", "raspberry", "blueberry", "blackberry", "strawberry",
                "gooseberry", "cranberry", "elderberry", "bush", "vine"
        };
        for (String name : fruitNames) {
            if (path.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static final class BerryCropHandler implements ISpecialCropHandler {
        @Override
        public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
            Property<?> property = findHarvestProperty(cropState);
            return property != null && isMature(cropState, property);
        }

        @Override
        public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState, boolean isDestroyMode) {
            if (!(maid.level() instanceof ServerLevel level)) {
                return;
            }

            AABB collectionBox = new AABB(cropPos).inflate(1.0D);
            Set<UUID> existingItems = new HashSet<>();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, collectionBox)) {
                existingItems.add(item.getUUID());
            }

            FakePlayer player = FakePlayerFactory.get(level, HARVESTER_PROFILE);
            player.setPos(maid.getX(), maid.getY(), maid.getZ());
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cropPos), net.minecraft.core.Direction.UP, cropPos, false);
            cropState.use(level, player, InteractionHand.MAIN_HAND, hit);

            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack harvested = player.getInventory().getItem(slot);
                if (harvested.isEmpty()) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), harvested.copy(), false);
                player.getInventory().setItem(slot, remainder);
            }

            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, collectionBox)) {
                if (existingItems.contains(item.getUUID())) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), item.getItem(), false);
                if (remainder.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(remainder);
                }
            }
        }

        private boolean hasBerriesProperty(BlockState state) {
            return state.getProperties().stream().anyMatch(property -> "berries".equals(property.getName()));
        }

        private Property<?> findHarvestProperty(BlockState state) {
            for (Property<?> property : state.getProperties()) {
                String name = property.getName();
                if (property instanceof BooleanProperty
                        && ("berries".equals(name) || "ripe".equals(name) || "mature".equals(name) || "fruiting".equals(name))) {
                    return property;
                }
                if (property instanceof IntegerProperty integer
                        && ("age".equals(name) || "stage".equals(name))
                        && integer.getPossibleValues().size() > 1) {
                    return property;
                }
            }
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private boolean isMature(BlockState state, Property property) {
            Comparable value = (Comparable) state.getValue(property);
            if (property instanceof BooleanProperty) {
                return Boolean.TRUE.equals(value);
            }
            Comparable lastValue = (Comparable) property.getPossibleValues().stream()
                    .reduce((first, second) -> second)
                    .orElse(value);
            return value.equals(lastValue);
        }
    }
}
