package me.matl114.hacks.modules.slimefun;

import static me.matl114.gui.FilterService.*;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.complex.slimefun.SavedItemWidget;
import me.matl114.gui.complex.slimefun.SlimefunChoiceScreen;
import me.matl114.gui.complex.slimefun.SlimefunEntryListScreen;
import me.matl114.gui.elements.SlotElement;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.RecipeTasks;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.managers.TaskManagers;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.ScreenUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class SlimefunGuide extends BaseModule {
    // todo: support big recipe
    /// gui
    public SlimefunGuide() {
        super("SlimefunGuide");
    }

    public static final String OPEN_GUIDE = "slime-guide";

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getTaskManager()
                .register(TaskManagers.PREFIX_BUTTON_TASKS + "." + OPEN_GUIDE, this::openMainGuideMenu);
    }

    public void handleAutoEnable() {
        RecipeDatabase database = SlimefunTasks.getRecipeDatabase();
        database.enable.set(true);
        database.saveData.set(true);
        Debug.chat(
                "Recipe auto-recording is enabled; use ctrl+G to open Slimefun settings to configure the parameters");
        Debug.chat(Text.literal(
                        "Note: item data from 1.20.5 and above is not compatible with 1.20.4 and below; keep this in mind if you join a Via-supported server!")
                .formatted(Formatting.YELLOW));
    }

    private boolean reject = false;

    public void handleRejectEnable() {
        Debug.chat("You can still use the GUIDE feature; this popup will not appear again during this launch");
        reject = true;
    }

    private static final Text QUESTION_NOT_ENABLE = Text.literal(
            "Recipe recording is currently disabled, so the full GUIDE experience is unavailable. How would you like to proceed?");
    private final List<QuestionScreen.Solution> QUESTION_SOLUTIONS = List.of(
            QuestionScreen.Solution.of(
                    Text.literal("I understand this feature, enable it with one click"), this::handleAutoEnable),
            QuestionScreen.Solution.of(
                    Text.literal("I understand this feature, but do not enable it"), this::handleRejectEnable),
            QuestionScreen.Solution.of(
                    Text.literal("I understand this feature, enable it with one click"), this::handleAutoEnable),
            QuestionScreen.Solution.of(
                    Text.literal("I understand this feature, but do not enable it"), this::handleRejectEnable),
            QuestionScreen.Solution.of(
                    Text.literal("I understand this feature, enable it with one click"), this::handleAutoEnable));

    public boolean handleNotEnable() {
        // 没有启用recipe或者没有启用
        RecipeDatabase database = SlimefunTasks.getRecipeDatabase();
        if ((!database.enable.get() || !database.saveData.get()) && !reject) {
            SlimefunTasks.openOrSwitch(new QuestionScreen(QUESTION_NOT_ENABLE, QUESTION_SOLUTIONS));
            return true;
        }
        return false;
    }
    // vanilla typed screen
    // optimize vanilla type display

    public void openRecipeEntryMenu(RecipeEntry recipe) {
        if (handleNotEnable()) return;
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(List.of(recipe)));
    }

    public void openCraftTypeMenu(RecipeDatabase.CraftingType type) {
        if (handleNotEnable()) return;
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(SlimefunTasks.getAllRecipes().values().stream()
                .filter(i -> Objects.equals(i.rid(), type.id()))
                .map(RecipeEntry.class::cast)
                .toList()));
    }

    private static final Text TITLE_ALL_ITEM = Text.literal("All recorded items");
    public static final List<Text> TOOLTIPS_ITEM_RULE = List.of(
            Text.literal("Left-click to view recipes for this item"),
            Text.literal("Right-click to view recipes that use this item"),
            Text.literal("Shift+right-click also shows the vanilla recipes for the item"),
            Text.literal("Middle-click attempts to fetch the item"));
    private static final Text TITLE_ALL_TYPE = Text.literal("All recorded recipe types");
    private static final Text TITLE_ALL_VANILLA = Text.literal("All vanilla recipes");
    private static final Text TITLE_ALL_SAVED = Text.literal("All saved items");
    public static final List<Text> TOOLTIPS_SAVED_RULE = List.of(
            Text.literal("Left-click to get a stack of this item (creative only)"),
            Text.literal("Shift+left-click to copy the /give command"),
            Text.literal("Right-click to open the item editor"));

    public void openMainGuideMenu() {
        if (handleNotEnable()) return;
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(
                        TITLE_ALL_ITEM,
                        TOOLTIPS_ITEM_RULE,
                        () -> SlimefunTasks.getRecipeDatabase().getId2Recipe().values().stream()
                                .toList(),
                        (entry) -> new ExecutableWidget(0, 0, 16, 16)
                                .setElementHandler(
                                        SlotElement.instance(entry.output().copyWithCount(1), (item, button) -> {
                                            if (button == 0) {
                                                openRecipeEntryMenu((RecipeEntry) entry);
                                                return true;
                                            } else if (button == 1) {
                                                onClickItemStack(item, ScreenUtils.hasShiftDown());
                                                return true;
                                            } else if (button == 2) {
                                                tryGetItemStack(item);
                                                return true;
                                            } else {
                                                return false;
                                            }
                                        })),
                        RecipeDatabase.SlimefunRecipeEntry::output)
                .setSearchFilter((BiPredicate) RECIPE_FILTER));
    }

    public void openSaveItemMenu() {
        if (handleNotEnable()) return;
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(
                        TITLE_ALL_SAVED,
                        TOOLTIPS_SAVED_RULE,
                        () -> InvTasks.getSaveItem().getSavedItemDataMap().keySet().stream()
                                .map(SlimefunTasks::byId)
                                .toList(),
                        (entry) -> new ExecutableWidget(0, 0, 16, 16)
                                .setElementHandler(SlotElement.instance(entry.copyWithCount(1), ((item, button) -> {
                                    if (button == 0) {
                                        tryGetItemStack(item);
                                        return true;
                                    } else if (button == 1) {
                                        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.mapToWidget(
                                                List.of(entry), (iv) -> new SavedItemWidget(0, 0, iv, null)));
                                        return true;
                                    } else return false;
                                }))),
                        Function.identity())
                .setSearchFilter(ITEM_FILTER));
    }

    public static BiPredicate<String, RecipeDatabase.CraftingType> RTYPE_FILTER = (str, i) -> nameMatch(i.id(), str);

    // rtype icon
    public void openCraftTypeMenu() {
        if (handleNotEnable()) return;
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(
                        TITLE_ALL_TYPE,
                        SlimefunTasks.getRecipeDatabase().getId2CraftType().values().stream()
                                .toList(),
                        (ct) -> new ExecutableWidget(0, 0, 16, 16)
                                .setElementHandler(SlotElement.instance(ct.iconStack())
                                        .withInputHandler(InputHandler.isLeft(t -> openCraftTypeMenu(ct)))),
                        RecipeDatabase.CraftingType::iconStack)
                .setSearchFilter(RTYPE_FILTER));
    }

    public void openVanillaRecipesMenu() {
        if (handleNotEnable()) return;
        // remake
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(
                        TITLE_ALL_VANILLA,
                        TOOLTIPS_ITEM_RULE,
                        () -> RecipeTasks.getAllRecipe().values().stream().toList(),
                        (rp) -> new ExecutableWidget(0, 0, 16, 16)
                                .setElementHandler(
                                        SlotElement.instance(rp.output().copyWithCount(1), (item, button) -> {
                                            if (button == 0) {
                                                openRecipeEntryMenu((RecipeEntry) rp);
                                                return true;
                                            } else if (button == 1) {
                                                onClickItemStack(rp.output(), ScreenUtils.hasShiftDown());
                                                return true;
                                            } else if (button == 2) {
                                                tryGetItemStack(rp.output());
                                                return true;
                                            } else {
                                                return false;
                                            }
                                        })),
                        RecipeTasks.RecipeRecord::output)
                .setSearchFilter((BiPredicate<String, RecipeTasks.RecipeRecord>) (BiPredicate) RECIPE_FILTER));
    }

    public void onClickRecipeType(String type, boolean isLeft) {
        if (handleNotEnable()) return;
        List<RecipeEntry> myEntry;

        if (RecipeTasks.isVanillaRecipeType(type)) {
            Map<Identifier, RecipeTasks.RecipeRecord> myCache = RecipeTasks.getAllRecipe();
            myEntry = myCache
                    .values() // RecipeTasks.getRecipeByType(type1)
                    .stream()
                    .filter(i -> Objects.equals(i.rid(), type))
                    .map(RecipeEntry.class::cast)
                    //  .map(i->(RecipeEntry)myCache.get(i.id()))
                    //  .filter(Objects::nonNull)
                    .toList();
        } else {

            myEntry = SlimefunTasks.getAllRecipes().values().stream()
                    .filter(i -> i.rid().equals(type))
                    .toList();
        }
        if (myEntry.isEmpty()) return;
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(myEntry));
    }

    public void tryGetItemStack(ItemStack item) {
        if (ScreenUtils.hasShiftDown()) {
            Debug.chat(Text.literal("Copied the item's Give command to the clipboard")
                    .formatted(Formatting.YELLOW));
            InvTasks.copyGiveCommand(item.copy());
        } else {
            if (mc.player != null && mc.interactionManager.getCurrentGameMode().isCreative()) {
                InvTasks.creativeAddItem(item.copy(), 64);
            } else {
                Debug.chat(Text.literal("Not in creative mode, cannot fetch saved items!")
                        .formatted(Formatting.YELLOW));
                Debug.chat(Text.literal("Use Shift+click to get the item's Give command!")
                        .formatted(Formatting.YELLOW));
            }
        }
    }

    public void onClickItemStack(ItemStack item, boolean isLeft) {
        if (handleNotEnable()) return;
        if (item.isEmpty()) {
            return;
        }
        List<RecipeEntry> resultToDisplay = new ArrayList<>();
        // logic remake
        boolean shiftDown = ScreenUtils.hasShiftDown();
        if (isLeft) {
            // 搞到当前物品的配方表
            // 显示每个输出和当前物品相同的配方表。使用sfid匹配sf物品，弱匹配 匹配其他物品
            // shift点击的时候以itemtype匹配
            String sfid = SlimefunTasks.generateId(item);
            for (var re : SlimefunTasks.getAllRecipes().values()) {
                if (shiftDown) {
                    if (Objects.equals(sfid, SlimefunTasks.generateId(re.output()))) {
                        resultToDisplay.add(re);
                        continue;
                    }
                } else {
                    if (ItemStackUtils.matchItemWithout(re.output(), item, false, false, false)) {
                        resultToDisplay.add(re);
                    }
                }
            }
            for (var re : RecipeTasks.getAllRecipe().values()) {
                if (shiftDown) {
                    if (re.output().isOf(item.getItem())) {
                        resultToDisplay.add(re);
                        continue;
                    }
                } else {
                    if (ItemStackUtils.matchItemWithout(re.output(), item, false, false, false)) {
                        resultToDisplay.add(re);
                    }
                }
            }
        } else {

            String generatedId = SlimefunTasks.generateId(item);
            search:
            for (var re : SlimefunTasks.getRecipeDatabase().getId2Recipe().values()) {
                for (var ingre : re.inputs()) {
                    if (shiftDown) {
                        if (Objects.equals(generatedId, SlimefunTasks.generateId(ingre))) {
                            resultToDisplay.add(re);
                            continue search;
                        }
                    } else {
                        if (ItemStackUtils.matchItemWithout(item, ingre, false, false, false)) {
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }
                }
            }
            search:
            for (var re : RecipeTasks.getAllRecipe().values()) {
                for (var ingre : re.ingredient()) {
                    if (shiftDown) {
                        if (ingre.testItemType(item)) {
                            resultToDisplay.add(re);
                            continue search;
                        }
                    } else {
                        if (!item.isEmpty()) {
                            for (var matchingStack : ingre.matchingStack()) {
                                if (ItemStackUtils.matchItemWithout(matchingStack, item, false, false, false)) {
                                    resultToDisplay.add(re);
                                    continue search;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (resultToDisplay.isEmpty()) {
            return;
        }
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(resultToDisplay));
    }
}
