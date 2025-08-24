package org.surf.modules.antiillegal;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.surf.Main;
import org.surf.util.ConfigCache;
import org.surf.util.Utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashMap;
import java.util.Collections;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;

public class ItemUtils {
    private final static Main plugin = Main.getInstance();

    public final static Set<Material> ILLEGALMATERIALS = new HashSet<>();

    public static void loadIllegalMaterials() {
        ILLEGALMATERIALS.clear();
        List<String> items = ConfigCache.AntiillegalIllegalItemsList;
        for (String item : items) {
            Material material = Material.getMaterial(item);
            if (material == null) {
                plugin.getLogger().log(Level.WARNING, "Invalid material: " + item);
                continue;
            }
            ILLEGALMATERIALS.add(material);
        }

    }

    // Weapon enchantment max levels (includes 1.21 mace enchants)
    private static final Map<Enchantment, Integer> WEAPON_ENCHANT_MAX = buildWeaponEnchantMax();

    private static Map<Enchantment, Integer> buildWeaponEnchantMax() {
        Map<Enchantment, Integer> m = new HashMap<>();
        // 通用武器附魔（使用 NamespacedKey 获取，跨版本更稳健）
        Enchantment mending = Enchantment.getByKey(NamespacedKey.minecraft("mending"));                 // 经验修补 I
        if (mending != null) m.put(mending, 1);
        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));           // 耐久 III
        if (unbreaking != null) m.put(unbreaking, 3);
        Enchantment smite = Enchantment.getByKey(NamespacedKey.minecraft("smite"));                     // 亡灵杀手 V
        if (smite != null) m.put(smite, 5);
        Enchantment bane = Enchantment.getByKey(NamespacedKey.minecraft("bane_of_arthropods"));         // 节肢杀手 V
        if (bane != null) m.put(bane, 5);
        Enchantment fireAspect = Enchantment.getByKey(NamespacedKey.minecraft("fire_aspect"));          // 火焰附加 II
        if (fireAspect != null) m.put(fireAspect, 2);
        Enchantment vanishing = Enchantment.getByKey(NamespacedKey.minecraft("vanishing_curse"));       // 消失诅咒 I
        if (vanishing != null) m.put(vanishing, 1);

        // 1.21 重锤（Mace）相关附魔（若服务器 API 存在）
        Enchantment density = Enchantment.getByKey(NamespacedKey.minecraft("density"));       // 致密 V
        if (density != null) m.put(density, 5);
        Enchantment breach = Enchantment.getByKey(NamespacedKey.minecraft("breach"));         // 破甲 IV
        if (breach != null) m.put(breach, 4);
        Enchantment windBurst = Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"));  // 风爆 III
        if (windBurst != null) m.put(windBurst, 3);

        return Collections.unmodifiableMap(m);
    }

    private static boolean isWeapon(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.equals("TRIDENT")
                || name.equals("MACE"); // 1.21+
    }

    public static boolean isIllegal(ItemStack item) {
        return ILLEGALMATERIALS.contains(item.getType());
    }

    public static boolean hasIllegalItemFlag(ItemStack item) {
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) || meta.hasItemFlag(ItemFlag.HIDE_DESTROYS) || meta.hasItemFlag(ItemFlag.HIDE_DYE) || meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) || meta.hasItemFlag(ItemFlag.HIDE_PLACED_ON) || meta.hasItemFlag(ItemFlag.HIDE_POTION_EFFECTS) || meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE) || meta.isUnbreakable();
        }
        return false;
    }

    // TODO - Add ability to filter for custom NBT attributes[configurable]
    public static boolean hasIllegalAttributes(ItemStack item) {
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasAttributeModifiers();
        }
        return false;
    }

    public static boolean hasIllegalEnchants(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) return false;

        boolean weapon = isWeapon(item.getType());

        for (Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            int level = e.getValue();

            if (weapon) {
                Integer max = WEAPON_ENCHANT_MAX.get(ench);
                if (max != null) {
                    if (level > max) return true; // 超出武器专用上限
                    else continue; // 在上限内则跳过阈值判断
                }
            }

            // 其余情况沿用全局阈值（默认 5）
            if (level > ConfigCache.IllegalEnchantsThreshold) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnchantedBlock(ItemStack item) {
        if (item.getType().isBlock()) {
            if (item.hasItemMeta()) {
                return item.getItemMeta().hasEnchants();
            }
        }
        return false;
    }

    public static void deleteIllegals(Inventory inventory) {
        // TODO: use a list to store the items to delete
        ItemStack itemStack = null;
        boolean illegalsFound = false;
        // if inventory is empty, skip
        if (inventory.getContents().length == 0) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            // if item is null, skip
            if (item == null) {
                continue;
            }
            if (item.getDurability() > item.getType().getMaxDurability()) {
                item.setDurability(item.getType().getMaxDurability());
                itemStack = item;
            }
            if (item.getDurability() < 0) {
                item.setDurability((short) 1);
                itemStack = item;
            }

            if (isIllegal(item)) {
                inventory.remove(item);
                illegalsFound = true;
                itemStack = item;
                continue;
            }
            if (hasIllegalItemFlag(item)) {
                inventory.remove(item);
                illegalsFound = true;
                itemStack = item;
                continue;
            }
            if (hasIllegalAttributes(item)) {
                inventory.remove(item);
                //TODO
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_ATTACK_KNOCKBACK);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_FLYING_SPEED);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_FOLLOW_RANGE);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_LUCK);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_MAX_HEALTH);
//                            item.getItemMeta().removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED);
//                            item.getItemMeta().removeAttributeModifier(Attribute.HORSE_JUMP_STRENGTH);
//                            item.getItemMeta().removeAttributeModifier(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS);
                illegalsFound = true;
                itemStack = item;
                continue;
            }
            if (hasIllegalEnchants(item)) {
                for (Entry<Enchantment, Integer> enchantmentIntegerEntry : item.getEnchantments().entrySet()) {
                    item.removeEnchantment(enchantmentIntegerEntry.getKey());
                    illegalsFound = true;
                    itemStack = item;
                }
            }
            if (item.hasItemMeta()) {
                if (isEnchantedBlock(item)) {
                    Iterator<Entry<Enchantment, Integer>> enchants = item.getEnchantments().entrySet()
                            .iterator();
                    illegalsFound = true;
                    itemStack = item;
                    while (enchants.hasNext()) {
                        item.removeEnchantment(enchants.next().getKey());
                    }
                }
            }
        }
        if (illegalsFound) {
            Utils.println(Utils.getPrefix() + "&6Deleted illegals " + itemStack.getType() + " " + itemStack.getI18NDisplayName() + " " + itemStack.getEnchantments() + (itemStack.hasItemMeta() ? " " + itemStack.getItemMeta().getAttributeModifiers() : ""));
        }
    }

}
