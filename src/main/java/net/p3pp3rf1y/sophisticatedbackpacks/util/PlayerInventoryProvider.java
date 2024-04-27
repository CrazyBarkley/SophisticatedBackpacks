package net.p3pp3rf1y.sophisticatedbackpacks.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

import java.util.*;
import java.util.function.Function;

public class PlayerInventoryProvider {
	public static final String MAIN_INVENTORY = "main";
	public static final String OFFHAND_INVENTORY = "offhand";
	public static final String ARMOR_INVENTORY = "armor";

	private final Map<String, PlayerInventoryHandler> playerInventoryHandlers = new LinkedHashMap<>();
	private final List<String> renderedHandlers = new ArrayList<>();

	private static final PlayerInventoryProvider serverProvider = new PlayerInventoryProvider();
	private static final PlayerInventoryProvider clientProvider = new PlayerInventoryProvider();

	public static PlayerInventoryProvider get() {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			return clientProvider;
		} else {
			return serverProvider;
		}
	}

	private PlayerInventoryProvider() {
		addPlayerInventoryHandler(MAIN_INVENTORY, gameTime -> PlayerInventoryHandler.SINGLE_IDENTIFIER, (player, identifier) -> player.getInventory().items.size(),
				(player, identifier, slot) -> player.getInventory().items.get(slot), true, false, false, false);
		addPlayerInventoryHandler(OFFHAND_INVENTORY, gameTime -> PlayerInventoryHandler.SINGLE_IDENTIFIER, (player, identifier) -> player.getInventory().offhand.size(),
				(player, identifier, slot) -> player.getInventory().offhand.get(slot), false, false, false, false);
		addPlayerInventoryHandler(ARMOR_INVENTORY, gameTime -> PlayerInventoryHandler.SINGLE_IDENTIFIER, (player, identifier) -> 1,
				(player, identifier, slot) -> player.getInventory().armor.get(EquipmentSlot.CHEST.getIndex()), false, true, false, true);
	}

	public void addPlayerInventoryHandler(String name, Function<Long, Set<String>> identifiersGetter, PlayerInventoryHandler.SlotCountGetter slotCountGetter, PlayerInventoryHandler.SlotStackGetter slotStackGetter, boolean visibleInGui, boolean rendered, boolean ownRenderer, boolean accessibleByAnotherPlayer) {
		Map<String, PlayerInventoryHandler> temp = new LinkedHashMap<>(playerInventoryHandlers);
		playerInventoryHandlers.clear();
		playerInventoryHandlers.put(name, new PlayerInventoryHandler(identifiersGetter, slotCountGetter, slotStackGetter, visibleInGui, ownRenderer, accessibleByAnotherPlayer));
		playerInventoryHandlers.putAll(temp);

		if (rendered) {
			ArrayList<String> tempRendered = new ArrayList<>(renderedHandlers);
			renderedHandlers.clear();
			renderedHandlers.add(name);
			renderedHandlers.addAll(tempRendered);
		}
	}

	public Optional<RenderInfo> getBackpackFromRendered(Player player) {
		for (String handlerName : renderedHandlers) {
			PlayerInventoryHandler invHandler = playerInventoryHandlers.get(handlerName);
			if (invHandler == null) {
				return Optional.empty();
			}
			for (String identifier : invHandler.getIdentifiers(player.level().getGameTime())) {
				for (int slot = 0; slot < invHandler.getSlotCount(player, identifier); slot++) {
					ItemStack slotStack = invHandler.getStackInSlot(player, identifier, slot);
					if (slotStack.getItem() instanceof BackpackItem) {
						return invHandler.hasItsOwnRenderer() ? Optional.empty() : Optional.of(new RenderInfo(slotStack, handlerName.equals(ARMOR_INVENTORY)));
					}
				}
			}
		}
		return Optional.empty();
	}

	private Map<String, PlayerInventoryHandler> getPlayerInventoryHandlers() {
		return playerInventoryHandlers;
	}

	public Optional<PlayerInventoryHandler> getPlayerInventoryHandler(String name) {
		return Optional.ofNullable(getPlayerInventoryHandlers().get(name));
	}

	public void runOnBackpacks(Player player, BackpackInventorySlotConsumer backpackInventorySlotConsumer) {
		runOnBackpacks(player, backpackInventorySlotConsumer, false);
	}

	public void runOnBackpacks(Player player, BackpackInventorySlotConsumer backpackInventorySlotConsumer, boolean onlyAccessibleByAnotherPlayer) {
		for (Map.Entry<String, PlayerInventoryHandler> entry : getPlayerInventoryHandlers().entrySet()) {
			PlayerInventoryHandler invHandler = entry.getValue();
			if (onlyAccessibleByAnotherPlayer && !invHandler.isAccessibleByAnotherPlayer()) {
				continue;
			}

			for (String identifier : invHandler.getIdentifiers(player.level().getGameTime())) {
				for (int slot = 0; slot < invHandler.getSlotCount(player, identifier); slot++) {
					ItemStack slotStack = invHandler.getStackInSlot(player, identifier, slot);
					if (slotStack.getItem() instanceof BackpackItem && backpackInventorySlotConsumer.accept(slotStack, entry.getKey(), identifier, slot)) {
						return;
					}
				}
			}
		}
	}

	public interface BackpackInventorySlotConsumer {
		boolean accept(ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
	}

	public static class RenderInfo {
		private final ItemStack backpack;
		private final boolean isArmorSlot;

		public RenderInfo(ItemStack backpack, boolean isArmorSlot) {
			this.backpack = backpack;
			this.isArmorSlot = isArmorSlot;
		}

		public ItemStack getBackpack() {
			return backpack;
		}

		public boolean isArmorSlot() {
			return isArmorSlot;
		}
	}
}
