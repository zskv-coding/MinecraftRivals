package com.zskv.minecraftRivals.dishdash;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Represents recipes that can be made in Dish Dash
 */
public enum DishDashRecipe {
    // Simple recipes (cooked items)
    COOKED_BEEF("Cooked Beef", Material.COOKED_BEEF, 1, RecipeComplexity.SIMPLE, 
        Arrays.asList(Material.BEEF)),
    COOKED_PORKCHOP("Cooked Porkchop", Material.COOKED_PORKCHOP, 1, RecipeComplexity.SIMPLE,
        Arrays.asList(Material.PORKCHOP)),
    BAKED_POTATO("Baked Potato", Material.BAKED_POTATO, 1, RecipeComplexity.SIMPLE,
        Arrays.asList(Material.POTATO)),
    COOKED_CHICKEN("Cooked Chicken", Material.COOKED_CHICKEN, 1, RecipeComplexity.SIMPLE,
        Arrays.asList(Material.CHICKEN)),
    WATER_BOTTLE("Water Bottle", Material.POTION, 1, RecipeComplexity.SIMPLE,
        Arrays.asList(Material.GLASS_BOTTLE)),
    
    // Medium recipes (combinations)
    GOLDEN_CARROT("Golden Carrot", Material.GOLDEN_CARROT, 2, RecipeComplexity.MEDIUM,
        Arrays.asList(Material.GOLD_INGOT, Material.CARROT)),
    
    // Complex recipes (multi-ingredient)
    PORK_STEW("Pork Stew", Material.RABBIT_STEW, 3, RecipeComplexity.COMPLEX,
        Arrays.asList(Material.POTATO, Material.CARROT, Material.COOKED_PORKCHOP)),
    CAKE("Cake", Material.CAKE, 4, RecipeComplexity.COMPLEX,
        Arrays.asList(Material.SUGAR, Material.EGG, Material.WHEAT, Material.MILK_BUCKET));
    
    private final String displayName;
    private final Material result;
    private final int points;
    private final RecipeComplexity complexity;
    private final List<Material> ingredients;
    
    DishDashRecipe(String displayName, Material result, int points, RecipeComplexity complexity, List<Material> ingredients) {
        this.displayName = displayName;
        this.result = result;
        this.points = points;
        this.complexity = complexity;
        this.ingredients = ingredients;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Material getResult() {
        return result;
    }
    
    public ItemStack getResultItem() {
        return new ItemStack(result);
    }
    
    public int getPoints() {
        return points;
    }
    
    public RecipeComplexity getComplexity() {
        return complexity;
    }
    
    public List<Material> getIngredients() {
        return ingredients;
    }
    
    public enum RecipeComplexity {
        SIMPLE,   // Just cooking
        MEDIUM,   // 2 ingredients
        COMPLEX   // 3+ ingredients
    }
}