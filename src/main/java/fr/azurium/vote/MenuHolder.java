package fr.azurium.vote;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class MenuHolder implements InventoryHolder {

    private final String menu;
    private final Map<Integer, MenuManager.Entry> entries = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(String menu) {
        this.menu = menu;
    }

    public String menu() { return menu; }
    public Map<Integer, MenuManager.Entry> entries() { return entries; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
