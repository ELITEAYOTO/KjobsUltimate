package me.krunsh.kjobultimate.hooks;

import me.krunsh.kjobultimate.KjobUltimate;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Hook Vault — permet les dépôts d'argent lors des actions de job.
 */
public final class VaultHook {

    private final KjobUltimate plugin;
    private Economy economy;

    public VaultHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        RegisteredServiceProvider<Economy> rsp =
            plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Dépose de l'argent dans le compte du joueur.
     * @param playerName Nom exact du joueur (Vault 1.7 utilise le nom, pas l'UUID)
     * @param amount     Montant à déposer (ignoré si <= 0)
     */
    public void deposit(String playerName, double amount) {
        if (economy == null || amount <= 0) return;
        economy.depositPlayer(playerName, amount);
    }

    public double getBalance(String playerName) {
        if (economy == null || playerName == null) return 0D;
        return economy.getBalance(playerName);
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isReady() {
        return economy != null;
    }
}
