package me.krunsh.kjobultimate.action;

import me.krunsh.kjobultimate.jobs.JobDefinition;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

/**
 * Résout la quantité réelle à comptabiliser pour un craft.
 *
 * Vanilla :
 * - clic normal : 1 exécution de recette ;
 * - shift-click : nombre maximal de recettes réellement craftables selon
 *   la matrice ET la place disponible dans l'inventaire.
 *
 * Kcraft :
 * - l'événement représente une exécution ;
 * - RESULT_ITEMS utilise la taille du résultat.
 */
public final class CraftUnitResolver {

    private CraftUnitResolver() {
    }

    public static int resolveVanilla(
            CraftItemEvent event,
            JobDefinition.ActionReward reward) {

        if (event == null
                || reward == null
                || event.getAction() == InventoryAction.NOTHING) {

            return 0;
        }

        Recipe recipe =
            event.getRecipe();

        if (recipe == null) {
            return 0;
        }

        ItemStack result =
            recipe.getResult();

        if (isEmpty(result)) {
            return 0;
        }

        int craftOperations =
            event.isShiftClick()
                ? estimateShiftCraftOperations(
                    event,
                    result
                )
                : 1;

        if (craftOperations <= 0) {
            return 0;
        }

        return toUnits(
            reward.getCountMode(),
            craftOperations,
            Math.max(
                1,
                result.getAmount()
            )
        );
    }

    public static int resolveKcraft(
            ItemStack result,
            JobDefinition.ActionReward reward) {

        if (isEmpty(result)
                || reward == null) {

            return 0;
        }

        return toUnits(
            reward.getCountMode(),
            1,
            Math.max(
                1,
                result.getAmount()
            )
        );
    }

    private static int estimateShiftCraftOperations(
            CraftItemEvent event,
            ItemStack result) {

        int byIngredients =
            estimateByIngredients(
                event.getInventory()
            );

        if (byIngredients <= 0) {
            return 0;
        }

        HumanEntity human =
            event.getWhoClicked();

        if (!(human instanceof Player)) {
            return byIngredients;
        }

        int resultAmount =
            Math.max(
                1,
                result.getAmount()
            );

        int capacity =
            resultCapacity(
                ((Player) human)
                    .getInventory(),
                result
            );

        int bySpace =
            capacity / resultAmount;

        return Math.min(
            byIngredients,
            bySpace
        );
    }

    private static int estimateByIngredients(
            Inventory inventory) {

        if (!(inventory instanceof CraftingInventory)) {
            /*
             * Fallback conservateur : si un fork renvoie une Inventory
             * générique, on ne prétend pas connaître un batch complet.
             */
            return 1;
        }

        ItemStack[] matrix =
            ((CraftingInventory) inventory)
                .getMatrix();

        if (matrix == null
                || matrix.length == 0) {

            return 0;
        }

        int crafts =
            Integer.MAX_VALUE;

        boolean found =
            false;

        for (ItemStack stack : matrix) {

            if (isEmpty(stack)) {
                continue;
            }

            found = true;

            crafts =
                Math.min(
                    crafts,
                    Math.max(
                        0,
                        stack.getAmount()
                    )
                );
        }

        if (!found
                || crafts == Integer.MAX_VALUE) {

            return 0;
        }

        return crafts;
    }

    private static int resultCapacity(
            PlayerInventory inventory,
            ItemStack result) {

        if (inventory == null
                || isEmpty(result)) {

            return 0;
        }

        int maxStack =
            Math.max(
                1,
                result.getMaxStackSize()
            );

        long capacity =
            0L;

        ItemStack[] contents =
            inventory.getContents();

        if (contents == null) {
            return 0;
        }

        for (ItemStack stack : contents) {

            if (isEmpty(stack)) {

                capacity +=
                    maxStack;

            } else if (stack.isSimilar(result)) {

                capacity +=
                    Math.max(
                        0,
                        maxStack
                            - stack.getAmount()
                    );
            }

            if (capacity >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return (int) capacity;
    }

    private static int toUnits(
            JobDefinition.CountMode mode,
            int craftOperations,
            int resultItemsPerCraft) {

        if (craftOperations <= 0) {
            return 0;
        }

        if (mode
                != JobDefinition.CountMode.RESULT_ITEMS) {

            return craftOperations;
        }

        long units =
            (long) craftOperations
                * (long) Math.max(
                    1,
                    resultItemsPerCraft
                );

        return units >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) units;
    }

    private static boolean isEmpty(
            ItemStack stack) {

        return stack == null
            || stack.getType() == null
            || stack.getType() == Material.AIR
            || stack.getAmount() <= 0;
    }
}
