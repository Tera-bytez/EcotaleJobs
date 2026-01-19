package com.ecotalejobs.util;

import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.config.AttitudeGroup;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * EcotaleJobs NPC Classification System V2
 * 
 * Sistema de clasificación multi-variable profesional para determinar
 * el tier económico de NPCs basado en múltiples factores.
 * 
 * ════════════════════════════════════════════════════════════════════════════════
 * 
 * VARIABLES UTILIZADAS:
 * ┌────────────────────────┬──────────────────────────────────────────────────────┐
 * │ Variable               │ Fuente & Propósito                                   │
 * ├────────────────────────┼──────────────────────────────────────────────────────┤
 * │ HP Base                │ role.getInitialMaxHealth() - Vida máxima definida    │
 * │ Attitude Group         │ worldSupport.getAttitudeGroup() - Comportamiento AI  │
 * │ Drop List              │ role.getDropListId() - Si tiene loot configurable    │
 * │ Combat Support         │ role.getCombatSupport() - Capacidades de combate     │
 * │ Interaction Vars       │ role.getInteractionVars() - Vars de daño/ataque      │
 * │ Role Name              │ npc.getRoleName() - Nombre del template de rol       │
 * └────────────────────────┴──────────────────────────────────────────────────────┘
 * 
 * ALGORITMO DE SCORING:
 * 
 *   ThreatScore = (HP × 1.0) + (EstimatedDamage × 4.0) + (AggressivenessBonus)
 *   
 *   Donde:
 *   - HP contribuye linealmente
 *   - Daño tiene peso 4x (un mob de alto daño es más peligroso)
 *   - Aggressiveness multiplica ×1.3 si es hostil
 *   
 *   Final tier determinado por ThreatScore + contexto del AttitudeGroup
 * 
 * ════════════════════════════════════════════════════════════════════════════════
 */
public class NPCClassifier {
    
    // =========================================================================
    // PATTERNS PARA ANÁLISIS DE NOMBRES
    // =========================================================================
    
    private static final Pattern VALID_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    
    private static final Pattern BOSS_PATTERNS = Pattern.compile(
        "(?i).*(boss|titan|dragon|colossus|overlord|king|queen|lord|ancient|primal|supreme|mega|ultra).*"
    );
    
    private static final Pattern ELITE_PATTERNS = Pattern.compile(
        "(?i).*(elite|alpha|werewolf|ghoul|aberrant|corrupted|void|shadow|dark_|elder).*"
    );
    
    private static final Pattern DIALOGUE_PATTERNS = Pattern.compile(
        "(?i).*(npc|villager|merchant|trader|quest|shopkeeper|civilian|citizen|" +
        "innkeeper|bartender|blacksmith|farmer|peasant|guide|helper|dummy|target|" +
        "mannequin|scarecrow|training).*"
    );
    
    private static final Pattern PASSIVE_PATTERNS = Pattern.compile(
        "(?i).*(sheep|cow|pig|chicken|deer|rabbit|bunny|fish|bird|frog|crab|" +
        "butterfly|bee|snail|turtle|livestock|animal|pet|companion).*"
    );
    
    private static final Pattern CRITTER_PATTERNS = Pattern.compile(
        "(?i).*(baby|cub|chick|pup|small|tiny|mini|young|juvenile|hatchling|spawn|rat|mouse|bug|insect).*"
    );
    
    // =========================================================================
    // ATTITUDE GROUPS CONOCIDOS
    // Estos son extraídos del análisis del servidor y npc_stats.json
    // =========================================================================
    
    private static final String[] AGGRESSIVE_GROUPS = {
        "Predator", "PredatorsBig", "PredatorsSmall", "Hostile", "Undead",
        "Aberrant", "Monster", "Trork", "Scarak", "Void", "Shadow"
    };
    
    private static final String[] PASSIVE_GROUPS = {
        "Passive", "Livestock", "Critter", "Animal", "Neutral", "Prey",
        "Wildlife", "Docile"
    };
    
    private static final String[] FRIENDLY_GROUPS = {
        "Friendly", "Civilian", "Player", "NPC", "Villager", "Merchant",
        "Guard"
    };
    
    // =========================================================================
    // RESULTADO DE CLASIFICACIÓN
    // =========================================================================
    
    public static class ClassificationResult {
        /** Nombre sanitizado del mob */
        public final String mobName;
        
        /** Tier calculado (CRITTER, PASSIVE, HOSTILE, ELITE, MINIBOSS, BOSS, WORLDBOSS) */
        public final String tier;
        
        /** Threat score calculado (útil para comparaciones) */
        public final double threatScore;
        
        /** Confianza en la clasificación (0.0 - 1.0) */
        public final float confidence;
        
        /** ¿Es elegible para recompensas? */
        public final boolean isRewardEligible;
        
        /** Razón de la clasificación */
        public final String reason;
        
        /** Datos crudos extraídos */
        public final NPCData rawData;
        
        public ClassificationResult(String mobName, String tier, double threatScore,
                                    float confidence, boolean isRewardEligible,
                                    String reason, NPCData rawData) {
            this.mobName = mobName;
            this.tier = tier;
            this.threatScore = threatScore;
            this.confidence = confidence;
            this.isRewardEligible = isRewardEligible;
            this.reason = reason;
            this.rawData = rawData;
        }
        
        @Override
        public String toString() {
            return String.format("Classification{mob=%s, tier=%s, threat=%.1f, conf=%.2f, reward=%s}",
                mobName, tier, threatScore, confidence, isRewardEligible);
        }
    }
    
    /**
     * Datos crudos extraídos del NPC.
     */
    public static class NPCData {
        public final int baseHP;
        public final int estimatedDamage;
        public final String attitudeGroup;
        public final boolean hasDropList;
        public final boolean hasCombatSupport;
        public final boolean isAggressive;
        
        public NPCData(int baseHP, int estimatedDamage, String attitudeGroup,
                      boolean hasDropList, boolean hasCombatSupport, boolean isAggressive) {
            this.baseHP = baseHP;
            this.estimatedDamage = estimatedDamage;
            this.attitudeGroup = attitudeGroup;
            this.hasDropList = hasDropList;
            this.hasCombatSupport = hasCombatSupport;
            this.isAggressive = isAggressive;
        }
    }
    
    // =========================================================================
    // API PRINCIPAL
    // =========================================================================
    
    /**
     * Clasifica un NPC de forma completa utilizando todas las variables disponibles.
     * 
     * @param mobId El identificador del mob
     * @param npc La entidad NPC para extraer stats
     * @return Resultado completo de clasificación
     */
    @Nonnull
    public static ClassificationResult classify(@Nullable String mobId, @Nullable NPCEntity npc) {
        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Validar y sanitizar nombre
        // ─────────────────────────────────────────────────────────────────────
        
        String sanitizedName = sanitizeName(mobId);
        if (sanitizedName == null) {
            return new ClassificationResult(
                mobId, "UNKNOWN", 0, 0f, false,
                "INVALID_NAME", new NPCData(0, 0, null, false, false, false)
            );
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Extraer TODOS los datos disponibles
        // ─────────────────────────────────────────────────────────────────────
        
        NPCData data = extractNPCData(npc);
        
        // ─────────────────────────────────────────────────────────────────────
        // PASO 3: Detectar NPCs que NO deberían dar recompensas
        // ─────────────────────────────────────────────────────────────────────
        
        if (isNonCombatNPC(sanitizedName, data)) {
            return new ClassificationResult(
                sanitizedName, "NONE", 0, 0.95f, false,
                "NON_COMBAT_NPC", data
            );
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // PASO 4: Calcular Threat Score
        // ─────────────────────────────────────────────────────────────────────
        
        double threatScore = calculateThreatScore(sanitizedName, data);
        
        // ─────────────────────────────────────────────────────────────────────
        // PASO 5: Determinar tier y confianza
        // ─────────────────────────────────────────────────────────────────────
        
        TierDecision decision = determineTier(sanitizedName, data, threatScore);
        
        return new ClassificationResult(
            sanitizedName,
            decision.tier,
            threatScore,
            decision.confidence,
            true,
            decision.reason,
            data
        );
    }
    
    /**
     * Clasificación rápida solo por nombre (para pre-carga sin entidades).
     */
    @Nonnull
    public static ClassificationResult classifyByName(@Nullable String mobId) {
        String sanitizedName = sanitizeName(mobId);
        if (sanitizedName == null) {
            return new ClassificationResult(
                mobId, "UNKNOWN", 0, 0f, false,
                "INVALID_NAME", new NPCData(0, 0, null, false, false, false)
            );
        }
        
        // Sin datos de entidad, solo podemos usar patrones de nombre
        TierDecision decision = inferTierFromNameOnly(sanitizedName);
        
        NPCData emptyData = new NPCData(0, 0, null, false, false, 
            BOSS_PATTERNS.matcher(sanitizedName).matches() || 
            ELITE_PATTERNS.matcher(sanitizedName).matches());
        
        return new ClassificationResult(
            sanitizedName,
            decision.tier,
            0,
            decision.confidence * 0.6f,  // 40% menos confianza sin datos
            !DIALOGUE_PATTERNS.matcher(sanitizedName).matches(),
            decision.reason + "_NAME_ONLY",
            emptyData
        );
    }
    
    // =========================================================================
    // EXTRACCIÓN DE DATOS
    // =========================================================================
    
    /**
     * Extrae TODOS los datos disponibles de un NPC.
     */
    @Nonnull
    private static NPCData extractNPCData(@Nullable NPCEntity npc) {
        if (npc == null) {
            return new NPCData(0, 0, null, false, false, false);
        }
        
        int baseHP = 0;
        int estimatedDamage = 0;
        String attitudeGroup = null;
        boolean hasDropList = false;
        boolean hasCombatSupport = false;
        boolean isAggressive = false;
        
        try {
            Role role = npc.getRole();
            if (role != null) {
                // HP base
                baseHP = role.getInitialMaxHealth();
                
                // Drop list (indica que es matakable)
                String dropList = role.getDropListId();
                hasDropList = dropList != null && !dropList.isEmpty();
                
                // Combat support
                CombatSupport combat = role.getCombatSupport();
                hasCombatSupport = combat != null;
                
                // Attitude group
                WorldSupport world = role.getWorldSupport();
                if (world != null) {
                    int groupIndex = world.getAttitudeGroup();
                    if (groupIndex != Integer.MIN_VALUE) {
                        try {
                            AttitudeGroup group = AttitudeGroup.getAssetMap().getAsset(groupIndex);
                            if (group != null) {
                                attitudeGroup = group.getId();
                                isAggressive = isAggressiveGroup(attitudeGroup);
                            }
                        } catch (Exception e) {
                            // Si falla, intentar inferir del índice
                            isAggressive = groupIndex > 5;  // Heurística básica
                        }
                    }
                }
                
                // Daño estimado desde InteractionVars
                Map<String, String> interactionVars = role.getInteractionVars();
                if (interactionVars != null) {
                    estimatedDamage = extractDamageFromVars(interactionVars);
                }
            }
        } catch (Exception e) {
            JobsLogger.debug("[NPCClassifier] Error extracting NPC data: %s", e.getMessage());
        }
        
        return new NPCData(baseHP, estimatedDamage, attitudeGroup, hasDropList, hasCombatSupport, isAggressive);
    }
    
    /**
     * Intenta extraer el daño base de las variables de interacción.
     * 
     * Las InteractionVars pueden contener referencias a interacciones de daño
     * como "Melee_Damage" o "Ranged_Damage".
     */
    private static int extractDamageFromVars(@Nonnull Map<String, String> vars) {
        // Las vars son Map<String, String> donde el valor es el ID de la interacción
        // No podemos acceder al daño real directamente desde aquí sin cargar la interacción
        // Pero podemos inferir que si tiene estas keys, es un mob de combate
        
        int damageIndicator = 0;
        
        for (String key : vars.keySet()) {
            String lowerKey = key.toLowerCase();
            if (lowerKey.contains("damage") || lowerKey.contains("attack")) {
                // Tiene capacidad de daño
                damageIndicator += 20;  // Valor base
            }
            if (lowerKey.contains("melee")) {
                damageIndicator += 15;
            }
            if (lowerKey.contains("ranged")) {
                damageIndicator += 25;  // Ranged suele ser más peligroso
            }
            if (lowerKey.contains("special") || lowerKey.contains("ability")) {
                damageIndicator += 30;  // Habilidades especiales
            }
        }
        
        return damageIndicator;
    }
    
    // =========================================================================
    // DETECCIÓN DE NO-COMBAT NPCs
    // =========================================================================
    
    /**
     * Determina si un NPC NO debería dar recompensas.
     */
    private static boolean isNonCombatNPC(String name, NPCData data) {
        // 1. Patrón de nombre indica diálogo/quest NPC
        if (DIALOGUE_PATTERNS.matcher(name).matches()) {
            // Pero verificar que no tenga stats de combate altos
            if (data.baseHP < 100 && !data.hasCombatSupport) {
                return true;
            }
            // Si tiene HP alto, puede ser un guardia o similar - dar reward
        }
        
        // 2. Attitude group es friendly/civilian
        if (data.attitudeGroup != null) {
            for (String friendly : FRIENDLY_GROUPS) {
                if (data.attitudeGroup.toLowerCase().contains(friendly.toLowerCase())) {
                    // Es amigable - no dar reward a menos que tenga combate
                    if (!data.hasCombatSupport) {
                        return true;
                    }
                }
            }
        }
        
        // 3. HP = 0 o muy bajo sin drop list (probablemente decorativo)
        if (data.baseHP == 0 && !data.hasDropList) {
            return true;
        }
        
        // 4. HP muy bajo + sin combat support + sin drops
        if (data.baseHP < 10 && !data.hasCombatSupport && !data.hasDropList) {
            return true;
        }
        
        return false;
    }
    
    // =========================================================================
    // CÁLCULO DE THREAT SCORE
    // =========================================================================
    
    /**
     * Calcula el Threat Score del NPC.
     * 
     * Fórmula:
     *   ThreatScore = (HP × 1.0) + (DamageIndicator × 2.0) + bonuses
     *   
     *   Bonuses:
     *   - Aggressive attitude: ×1.3
     *   - Has combat support: +50
     *   - Elite/Boss name patterns: +100/+200
     */
    private static double calculateThreatScore(String name, NPCData data) {
        double score = 0;
        
        // Base: HP
        score += data.baseHP * 1.0;
        
        // Daño (con peso alto)
        score += data.estimatedDamage * 2.0;
        
        // Bonus por combat support
        if (data.hasCombatSupport) {
            score += 50;
        }
        
        // Bonus por drop list (indica que es un mob legítimo)
        if (data.hasDropList) {
            score += 25;
        }
        
        // Multiplicador por agresividad
        if (data.isAggressive) {
            score *= 1.3;
        }
        
        // Ajustes por patrones de nombre
        if (BOSS_PATTERNS.matcher(name).matches()) {
            // Nombre sugiere boss - pero verificar que HP corresponda
            if (data.baseHP >= 350) {
                score += 200;  // Confirmado boss
            } else if (data.baseHP > 0) {
                // Nombre dice boss pero stats no coinciden - no agregar bonus
                // El score ya refleja sus stats reales
            }
        } else if (ELITE_PATTERNS.matcher(name).matches()) {
            if (data.baseHP >= 150) {
                score += 100;  // Confirmado elite
            }
        }
        
        // Penalización por patrones pasivos
        if (PASSIVE_PATTERNS.matcher(name).matches() && !data.isAggressive) {
            score *= 0.7;  // Reducir score si parece pasivo
        }
        
        if (CRITTER_PATTERNS.matcher(name).matches()) {
            score *= 0.5;  // Critters tienen bajo threat
        }
        
        return score;
    }
    
    // =========================================================================
    // DETERMINACIÓN DE TIER
    // =========================================================================
    
    private static class TierDecision {
        String tier;
        float confidence;
        String reason;
        
        TierDecision(String tier, float confidence, String reason) {
            this.tier = tier;
            this.confidence = confidence;
            this.reason = reason;
        }
    }
    
    /**
     * Determina el tier final basado en todos los factores.
     */
    private static TierDecision determineTier(String name, NPCData data, double threatScore) {
        // ─────────────────────────────────────────────────────────────────────
        // CASO 1: Tenemos HP - usar como fuente principal
        // ─────────────────────────────────────────────────────────────────────
        
        if (data.baseHP > 0) {
            String hpTier = getTierFromHP(data.baseHP);
            String scoreTier = getTierFromScore(threatScore);
            
            // Si HP y Score coinciden, alta confianza
            if (hpTier.equals(scoreTier)) {
                return new TierDecision(hpTier, 0.95f, "HP_SCORE_MATCH");
            }
            
            // Si score sugiere tier más alto (por daño/agresividad), considerar
            int hpRank = tierRank(hpTier);
            int scoreRank = tierRank(scoreTier);
            
            if (scoreRank > hpRank) {
                // Score sugiere más peligroso - usar promedio ponderado
                // HP tiene más peso porque es dato duro
                if (scoreRank - hpRank == 1) {
                    // Solo 1 tier de diferencia - usar HP
                    return new TierDecision(hpTier, 0.85f, "HP_PRIMARY");
                } else {
                    // 2+ tiers de diferencia - algo especial, subir 1
                    String adjusted = tierByRank(hpRank + 1);
                    return new TierDecision(adjusted, 0.75f, "SCORE_ADJUSTED");
                }
            } else if (hpRank > scoreRank) {
                // HP más alto que score (HP tanky pero bajo daño)
                // Mantener HP tier pero notar baja amenaza
                return new TierDecision(hpTier, 0.80f, "HIGH_HP_LOW_THREAT");
            }
            
            return new TierDecision(hpTier, 0.90f, "HP_PRIMARY");
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // CASO 2: Sin HP - usar score y patrones
        // ─────────────────────────────────────────────────────────────────────
        
        if (threatScore > 0) {
            String scoreTier = getTierFromScore(threatScore);
            return new TierDecision(scoreTier, 0.70f, "SCORE_ONLY");
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // CASO 3: Sin datos - inferir del nombre
        // ─────────────────────────────────────────────────────────────────────
        
        return inferTierFromNameOnly(name);
    }
    
    /**
     * Tier basado en HP solamente.
     * 
     * Thresholds calibrados con npc_stats.json:
     * - Chicken: 7-27 HP
     * - Sheep: 81 HP
     * - Zombies: 100-150 HP
     * - Elite mobs: 200-300 HP
     * - Rex_Cave: 400 HP
     * - Dragons: 400 HP (pero con alto daño)
     */
    private static String getTierFromHP(int hp) {
        if (hp <= 0) return "UNKNOWN";
        if (hp <= 30) return "CRITTER";       // Chickens, rats, etc
        if (hp <= 100) return "PASSIVE";      // Sheep, basic animals
        if (hp <= 200) return "HOSTILE";      // Standard enemies
        if (hp <= 350) return "ELITE";        // Stronger variants
        if (hp <= 600) return "MINIBOSS";     // Rex, Aberrants
        if (hp <= 1500) return "BOSS";        // Dungeon bosses
        return "WORLDBOSS";                   // Dragons, world events
    }
    
    /**
     * Tier basado en Threat Score.
     */
    private static String getTierFromScore(double score) {
        if (score <= 0) return "UNKNOWN";
        if (score <= 50) return "CRITTER";
        if (score <= 150) return "PASSIVE";
        if (score <= 350) return "HOSTILE";
        if (score <= 600) return "ELITE";
        if (score <= 1000) return "MINIBOSS";
        if (score <= 2500) return "BOSS";
        return "WORLDBOSS";
    }
    
    /**
     * Inferir tier solo del nombre.
     */
    private static TierDecision inferTierFromNameOnly(String name) {
        // WORLDBOSS patterns
        if (name.toLowerCase().contains("dragon") || 
            name.toLowerCase().contains("titan") ||
            name.toLowerCase().contains("colossus")) {
            return new TierDecision("WORLDBOSS", 0.55f, "NAME_PATTERN_WORLDBOSS");
        }
        
        // BOSS patterns
        if (BOSS_PATTERNS.matcher(name).matches()) {
            return new TierDecision("BOSS", 0.45f, "NAME_PATTERN_BOSS");
        }
        
        // ELITE patterns
        if (ELITE_PATTERNS.matcher(name).matches()) {
            return new TierDecision("ELITE", 0.55f, "NAME_PATTERN_ELITE");
        }
        
        // CRITTER patterns
        if (CRITTER_PATTERNS.matcher(name).matches()) {
            return new TierDecision("CRITTER", 0.70f, "NAME_PATTERN_CRITTER");
        }
        
        // PASSIVE patterns
        if (PASSIVE_PATTERNS.matcher(name).matches()) {
            return new TierDecision("PASSIVE", 0.65f, "NAME_PATTERN_PASSIVE");
        }
        
        // Default: HOSTILE (safest assumption)
        return new TierDecision("HOSTILE", 0.40f, "DEFAULT_HOSTILE");
    }
    
    // =========================================================================
    // UTILIDADES
    // =========================================================================
    
    /**
     * Sanitiza un nombre de mob.
     */
    @Nullable
    public static String sanitizeName(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        
        String clean = VALID_CHARS.matcher(raw).replaceAll("");
        clean = clean.replaceFirst("^[0-9_]+", "");
        clean = clean.replaceFirst("_+$", "");
        
        if (clean.length() < 2) {
            return null;
        }
        
        if (clean.length() > 128) {
            clean = clean.substring(0, 128);
        }
        
        return clean;
    }
    
    /**
     * Verifica si un attitude group es agresivo.
     */
    private static boolean isAggressiveGroup(@Nullable String groupName) {
        if (groupName == null) return false;
        String lower = groupName.toLowerCase();
        for (String ag : AGGRESSIVE_GROUPS) {
            if (lower.contains(ag.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Convierte tier a ranking numérico.
     */
    private static int tierRank(String tier) {
        switch (tier) {
            case "CRITTER": return 1;
            case "PASSIVE": return 2;
            case "HOSTILE": return 3;
            case "ELITE": return 4;
            case "MINIBOSS": return 5;
            case "BOSS": return 6;
            case "WORLDBOSS": return 7;
            default: return 0;
        }
    }
    
    /**
     * Convierte ranking numérico a tier.
     */
    private static String tierByRank(int rank) {
        switch (rank) {
            case 1: return "CRITTER";
            case 2: return "PASSIVE";
            case 3: return "HOSTILE";
            case 4: return "ELITE";
            case 5: return "MINIBOSS";
            case 6: return "BOSS";
            case 7: return "WORLDBOSS";
            default: return "UNKNOWN";
        }
    }
    
    // =========================================================================
    // MÉTODOS DE CONVENIENCIA
    // =========================================================================
    
    /**
     * Método rápido para obtener solo el tier.
     */
    @Nonnull
    public static String getTier(@Nullable String mobId, @Nullable NPCEntity npc) {
        return classify(mobId, npc).tier;
    }
    
    /**
     * Método rápido para verificar si es elegible para rewards.
     */
    public static boolean isRewardEligible(@Nullable String mobId, @Nullable NPCEntity npc) {
        return classify(mobId, npc).isRewardEligible;
    }
    
    /**
     * Método para debug - imprime toda la info de clasificación.
     */
    public static void debugClassify(String mobId, NPCEntity npc) {
        ClassificationResult result = classify(mobId, npc);
        
        JobsLogger.info("═══════════════════════════════════════════════════════════");
        JobsLogger.info("NPC Classification: %s", mobId);
        JobsLogger.info("───────────────────────────────────────────────────────────");
        JobsLogger.info("  Sanitized Name: %s", result.mobName);
        JobsLogger.info("  Tier: %s", result.tier);
        JobsLogger.info("  Threat Score: %.1f", result.threatScore);
        JobsLogger.info("  Confidence: %.1f%%", result.confidence * 100);
        JobsLogger.info("  Reward Eligible: %s", result.isRewardEligible);
        JobsLogger.info("  Reason: %s", result.reason);
        JobsLogger.info("───────────────────────────────────────────────────────────");
        JobsLogger.info("  Raw Data:");
        JobsLogger.info("    HP: %d", result.rawData.baseHP);
        JobsLogger.info("    Est. Damage: %d", result.rawData.estimatedDamage);
        JobsLogger.info("    Attitude: %s", result.rawData.attitudeGroup);
        JobsLogger.info("    Has Drops: %s", result.rawData.hasDropList);
        JobsLogger.info("    Has Combat: %s", result.rawData.hasCombatSupport);
        JobsLogger.info("    Aggressive: %s", result.rawData.isAggressive);
        JobsLogger.info("═══════════════════════════════════════════════════════════");
    }
}
