# KjobsUltimate — Système de Places de Métiers (anciennement "slots")

> **Clarification du terme "slot" :** Dans cette documentation, "slot" ne désigne PAS un slot d'inventaire Minecraft. C'est simplement une **place de métier** — le nombre de métiers qu'un joueur peut pratiquer simultanément. Le joueur sélectionne ses métiers depuis le GUI `/jobs` comme d'habitude ; la "place" détermine combien il peut en avoir d'actifs en même temps.

> Décisions actées : configurable, défaut 1 place, déblocage progressif par level, désactivable.

---

## 0. Comprendre le Concept

**Exemple concret :**
- Joueur niveau 1 → 1 place active → peut sélectionner 1 seul métier (ex: Mineur)
- Joueur atteint niveau 5 en Mineur → 2 places actives débloquées → peut maintenant sélectionner un 2ème métier (ex: Farmer)
- Les 5 métiers sont toujours affichés dans le GUI, mais ceux non sélectionnables sont grisés avec un message de hover "Niveau X requis pour débloquer une nouvelle place"

**Confirmation avant changement :** Si un joueur veut remplacer un métier actif par un autre, une confirmation s'affiche en chat :
```
[KjobsUltimate] §cAttention ! Remplacer §bMineur§c par §bFarmer§c fera perdre toute votre progression en Mineur.
[KjobsUltimate] Tapez §e/jobs confirmer§c pour confirmer ou §e/jobs annuler§c pour annuler.
```
- La progression (level + XP) est **définitivement perdue** si le joueur confirme le changement.
- Cette règle est configurable (`warn_on_job_change: true` dans config.yml).

---

## 1. Concept

Une **place de métier** (`job_slot` en code) est un emplacement actif qu'un joueur peut remplir avec un métier.

| Scénario | Comportement |
|---|---|
| `slots_enabled: false` | Tous les métiers actifs dès le join (0 contrainte) |
| `slots_enabled: true` | Le joueur commence avec N places, il en débloque d'autres en progressant |
| Place libre | Le joueur peut la remplir en sélectionnant un métier depuis le GUI `/jobs` |
| Place occupée | Le joueur peut la changer via GUI avec confirmation (perte de progression) |

**Sur SparrowMC :** `slots_enabled: true`, `default_slots: 1`, déblocage de la 2ème place au niveau 5 du métier principal.

---

## 2. Configuration (config.yml — section job_slots)

```yaml
job_slots:
  # false = tous les jobs actifs par défaut (mode sans restriction)
  enabled: true

  # Nombre de slots débloqués dès la première connexion
  default_slots: 1

  # Condition de déblocage des slots supplémentaires
  # unlock_condition: MAIN_JOB_LEVEL  → niveau du job dans le slot 1
  #                   HIGHEST_JOB_LEVEL → niveau le plus haut parmi tous les jobs
  #                   TOTAL_LEVEL       → somme de tous les niveaux des jobs
  unlock_condition: MAIN_JOB_LEVEL

  # Déblocage des slots : slot_numero: niveau_requis
  # Le slot 1 est toujours disponible (default_slots: 1)
  unlock_thresholds:
    2: 5     # Slot 2 débloqué quand job principal atteint level 5
    3: 15    # Slot 3 débloqué quand job principal atteint level 15
    4: 30
    5: 50

  # Nombre maximum de slots débloquables (= nombre total de jobs)
  max_slots: 5

  # Quand un slot est débloqué, notifier le joueur ?
  notify_unlock: true
  notify_message: "§a§lNOUVEAU SLOT ! §7Tu peux maintenant pratiquer un second métier !"

  # Autoriser le joueur à changer de job dans un slot ?
  # true = changement libre | false = job assigné permanent
  allow_job_change: true

  # Si allow_job_change: true, délai entre deux changements (secondes, 0 = pas de délai)
  change_cooldown: 0
```

---

## 3. Flux de Déblocage

```
[Joueur level up son job principal → niveau 5]
        │
        ▼
[PlayerData.getJobLevel("mineur") == 5]
        │
        ▼
[JobSlotManager.checkUnlock(player, "mineur", 5)]
  ├─ Lire unlock_condition → MAIN_JOB_LEVEL
  ├─ Slot 1 = "mineur" (c'est le job principal)
  ├─ unlock_thresholds[2] = 5 → level atteint !
  ├─ PlayerData.unlockedSlots = 1 → passer à 2
  ├─ Sauvegarder
  ├─ Notifier le joueur (message + son)
  └─ [Joueur peut maintenant aller dans /jobs et assigner un 2ème job]
```

---

## 4. Modèle de Données

```java
// Dans PlayerData.java
private int unlockedSlots = 1;           // Nombre de slots débloqués
private Map<Integer, String> slotJobs;   // slot → jobId
// Exemple : {1: "mineur", 2: "farmer"}
// Slot vide = absent de la map ou null

private String displayJob;               // Job affiché dans la bossbar (par défaut = slot 1)
```

### Règles métier

- `slotJobs.get(1)` = job principal (toujours rempli après la sélection initiale)
- `displayJob` = job dont la bossbar est affichée. **Automatique** — toujours le **dernier jobId ayant produit de l’XP** (mis à jour dans le flux XP). Aucune sélection manuelle.
- Si `slots_enabled: false` → `slotJobs` contient tous les jobs, `unlockedSlots` = 5

---

## 6. Sélection du Premier Job (Join)

Quand `default_all_jobs: false` (valeur par défaut sur SparrowMC) :

```
[Premier join du joueur]
        │
        ▼
[Slot 1 vide → ouvrir automatiquement GUI de sélection du premier job]
        │
  Le joueur clique sur un job dans le GUI
        │
        ▼
[PlayerData.slotJobs.put(1, jobId)]
[PlayerData.displayJob = jobId]
[Sauvegarder]
[Message de bienvenue + bossbar initialisée]
```

**Note :** Si le joueur quitte AVANT d'avoir choisi → slot 1 reste vide, GUI rouvre au prochain join.

---

## 7. JobSlotManager.java — Responsabilités

```java
public class JobSlotManager {
    // Vérifier si un slot est débloqué pour un joueur
    boolean isSlotUnlocked(PlayerData data, int slotNumber);

    // Assigner un job à un slot
    void assignJob(PlayerData data, int slot, String jobId);

    // Retirer un job d'un slot (quand le joueur change)
    void removeJob(PlayerData data, int slot);

    // Vérifier et déclencher le déblocage de nouveaux slots après un level up
    void checkAndUnlockSlots(Player player, PlayerData data, String jobId, int newLevel);

    // Obtenir le job actuellement affiché dans la bossbar
    String getDisplayJob(PlayerData data);  // peut retourner null si aucun job sélectionné

    // Obtenir tous les jobs actifs du joueur (peu importe le slot)
    List<String> getActiveJobs(PlayerData data);
}
```

---

## 8. Interactions avec d'autres systèmes

| Système | Impact des slots |
|---|---|
| **BossBar** | Affiche `displayJob` uniquement — 1 seule bossbar |
| **ActionBar** | Affiche le gain XP du job qui vient d'être utilisé |
| **Tab footer** | Affiche TOUS les jobs actifs (slots remplis) avec leur niveau |
| **Listeners XP** | Donnent de l'XP à N'IMPORTE quel job actif (tous les slots) |
| **Quêtes** | Progressent pour tous les jobs actifs |
| **GUI overview** | Affiche tous les jobs ; ceux dans un slot = encadrement visible |
