package com.zskv.minecraftRivals.dishdash;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores data for each team playing Dish Dash
 */
public class DishDashTeamData {
    
    private final String teamName;
    private final Location spawnLocation;
    private final Location shopGuiLocation;
    private final Location barrelLocation;
    private final List<Location> customerLocations;
    private final Location driveThruLocation;
    private final Location cauldronLocation;
    private final Location composterLocation;
    private final Location seedBarrelLocation;
    
    private int points;
    private int customersServed;
    private boolean finished;
    private final List<DishDashCustomer> activeCustomers;
    
    public DishDashTeamData(String teamName, Location spawn, Location shopGui, Location barrel,
                            List<Location> customers, Location driveThru, Location cauldron,
                            Location composter, Location seedBarrel) {
        this.teamName = teamName;
        this.spawnLocation = spawn;
        this.shopGuiLocation = shopGui;
        this.barrelLocation = barrel;
        this.customerLocations = customers;
        this.driveThruLocation = driveThru;
        this.cauldronLocation = cauldron;
        this.composterLocation = composter;
        this.seedBarrelLocation = seedBarrel;
        
        this.points = 0;
        this.customersServed = 0;
        this.finished = false;
        this.activeCustomers = new ArrayList<>();
    }
    
    public String getTeamName() {
        return teamName;
    }
    
    public Location getSpawnLocation() {
        return spawnLocation;
    }
    
    public Location getShopGuiLocation() {
        return shopGuiLocation;
    }
    
    public Location getBarrelLocation() {
        return barrelLocation;
    }
    
    public List<Location> getCustomerLocations() {
        return customerLocations;
    }
    
    public Location getDriveThruLocation() {
        return driveThruLocation;
    }
    
    public Location getCauldronLocation() {
        return cauldronLocation;
    }
    
    public Location getComposterLocation() {
        return composterLocation;
    }
    
    public Location getSeedBarrelLocation() {
        return seedBarrelLocation;
    }
    
    public int getPoints() {
        return points;
    }
    
    public void addPoints(int amount) {
        this.points += amount;
    }
    
    public int getCustomersServed() {
        return customersServed;
    }
    
    public void incrementCustomersServed() {
        this.customersServed++;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    public void setFinished(boolean finished) {
        this.finished = finished;
    }
    
    public List<DishDashCustomer> getActiveCustomers() {
        return activeCustomers;
    }
    
    public void addCustomer(DishDashCustomer customer) {
        activeCustomers.add(customer);
    }
    
    public void removeCustomer(DishDashCustomer customer) {
        activeCustomers.remove(customer);
    }
    
    public void clearCustomers() {
        // Remove all customer entities
        for (DishDashCustomer customer : activeCustomers) {
            customer.remove();
        }
        activeCustomers.clear();
    }
}