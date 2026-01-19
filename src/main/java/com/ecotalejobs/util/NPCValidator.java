package com.ecotalejobs.util;

import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * NPC Validation & Classification System
 * 
 * @deprecated Use {@link NPCClassifier} instead for multi-variable classification.
 *             This class is kept for backwards compatibility.
 * 
 * MIGRATION:
 *   OLD: NPCValidator.validate(mobId, npc)
 *   NEW: NPCClassifier.classify(mobId, npc)
 * 
 * El nuevo NPCClassifier usa múltiples variables:
 * - HP base (role.getInitialMaxHealth())
 * - Attitude group (hostil/pasivo/amigable)
 * - Combat support (si puede atacar)
 * - Drop list (si tiene loot)
 * - Patrones de nombre
 * - Cálculo de Threat Score
 */
@Deprecated
public class NPCValidator {
    
    // =========================================================================
    // RESULTADO DE VALIDACIÓN (mantenido por compatibilidad)
    // =========================================================================
    
    public static class ValidationResult {
        /** Nombre sanitizado (null si inválido) */
        public final String name;
        
        /** HP máximo base del mob */
        public final int baseHP;
        
        /** Tier sugerido basado en análisis */
        public final String suggestedTier;
        
        /** Confianza en la clasificación (0.0 - 1.0) */
        public final float confidence;
        
        /** ¿Es válido para dar recompensas? */
        public final boolean isRewardable;
        
        /** Razón si no es rewardable */
        public final String reason;
        
        /** ¿Es probablemente un NPC de diálogo? */
        public final boolean isLikelyDialogueNPC;
        
        /** ¿El nombre sugiere boss pero stats no coinciden? */
        public final boolean isMislabeled;
        
        private ValidationResult(String name, int baseHP, String suggestedTier, 
                                  float confidence, boolean isRewardable, String reason,
                                  boolean isLikelyDialogueNPC, boolean isMislabeled) {
            this.name = name;
            this.baseHP = baseHP;
            this.suggestedTier = suggestedTier;
            this.confidence = confidence;
            this.isRewardable = isRewardable;
            this.reason = reason;
            this.isLikelyDialogueNPC = isLikelyDialogueNPC;
            this.isMislabeled = isMislabeled;
        }
        
        /**
         * Convierte desde NPCClassifier.ClassificationResult.
         */
        static ValidationResult fromClassification(NPCClassifier.ClassificationResult cr) {
            boolean isDialogue = cr.reason != null && cr.reason.contains("DIALOGUE");
            boolean isMislabeled = cr.reason != null && cr.reason.contains("ADJUSTED");
            
            return new ValidationResult(
                cr.mobName,
                cr.rawData.baseHP,
                cr.tier,
                cr.confidence,
                cr.isRewardEligible,
                cr.reason,
                isDialogue,
                isMislabeled
            );
        }
    }
    
    // =========================================================================
    // API PRINCIPAL (delega a NPCClassifier)
    // =========================================================================
    
    /**
     * Valida un NPC y determina su clasificación.
     * 
     * @deprecated Use {@link NPCClassifier#classify(String, NPCEntity)} instead.
     */
    @Deprecated
    public static ValidationResult validate(String mobId, NPCEntity npc) {
        NPCClassifier.ClassificationResult cr = NPCClassifier.classify(mobId, npc);
        return ValidationResult.fromClassification(cr);
    }
    
    /**
     * Validación rápida solo por nombre (para startup scan sin entidades).
     * 
     * @deprecated Use {@link NPCClassifier#classifyByName(String)} instead.
     */
    @Deprecated
    public static ValidationResult validateNameOnly(String mobId) {
        NPCClassifier.ClassificationResult cr = NPCClassifier.classifyByName(mobId);
        return ValidationResult.fromClassification(cr);
    }
    
    // =========================================================================
    // MÉTODOS UTILITARIOS (delegados)
    // =========================================================================
    
    /**
     * Limpia un nombre de mob para que sea usable.
     * 
     * @deprecated Use {@link NPCClassifier#sanitizeName(String)} instead.
     */
    @Deprecated
    public static String sanitizeName(String raw) {
        return NPCClassifier.sanitizeName(raw);
    }
    
    /**
     * Extrae el HP BASE del NPC.
     */
    public static int extractBaseHP(NPCEntity npc) {
        if (npc == null) return 0;
        NPCClassifier.ClassificationResult cr = NPCClassifier.classify(null, npc);
        return cr.rawData.baseHP;
    }
    
    /**
     * Tier basado puramente en HP.
     * 
     * @deprecated Use {@link NPCClassifier#classify(String, NPCEntity)} for full analysis.
     */
    @Deprecated
    public static String getTierFromHP(int hp) {
        // Keep the old thresholds for backwards compatibility
        if (hp <= 0) return "UNKNOWN";
        if (hp <= 50) return "CRITTER";
        if (hp <= 100) return "PASSIVE";
        if (hp <= 200) return "HOSTILE";
        if (hp <= 350) return "ELITE";
        if (hp <= 600) return "MINIBOSS";
        if (hp <= 1500) return "BOSS";
        return "WORLDBOSS";
    }
    
    /**
     * Verifica si un mob debería dar recompensas (sin entidad).
     */
    public static boolean shouldReward(String mobId) {
        return NPCClassifier.classifyByName(mobId).isRewardEligible;
    }
}
