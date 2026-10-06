package com.shikongxiangbian.app

import java.time.LocalDateTime

data class NatalAnalysisV5(
    val season: String,
    val dayMasterContext: String,
    val rootsAndHidden: String,
    val sourceAndOutlet: String,
    val climate: String,
    val energyFlow: String,
    val technical: List<String>,
    val coreInsight: String = "",
    val condition: String = ""
)

enum class EnergyRelation { SAME, GENERATES, GENERATED_BY, CONTROLS, CONTROLLED_BY, UNKNOWN }
enum class ChangeRole { SUPPLEMENT, REINFORCE, DRAIN, RESTRAIN, DISTURB, TIE, BACKGROUND }
enum class EvidenceChannel { STEM, BRANCH }

data class EnergyAvailability(
    val expressed: Boolean,
    val rooted: Boolean,
    val available: Boolean,
    val restricted: Boolean,
    val description: String
)

data class PathEnergy(
    val source: EnergyAvailability,
    val target: EnergyAvailability,
    val challenges: List<String> = emptyList()
)

/** One independently inspectable path; the summary's focal layer never filters these out. */
data class ImpactPath(
    val id: String,
    val order: Int,
    val sourceLabel: String,
    val sourceGanZhi: String,
    val sourceGan: String,
    val sourceElement: String,
    val targetLabel: String,
    val targetGanZhi: String,
    val targetGan: String,
    val targetElement: String,
    val targetNatal: Boolean,
    val channel: EvidenceChannel,
    val relation: EnergyRelation,
    val techniques: Set<String>,
    val natalAnchors: Set<String>,
    val inheritedPathIds: List<String>,
    val sourceAtEntry: EnergyAvailability,
    val targetAtEntry: EnergyAvailability,
    val evidence: String,
    val hiddenTarget: Boolean = false,
    val targetBefore: EnergyAvailability? = null
)

data class LayerAnalysisV5(
    val order: Int,
    val layer: String,
    val ganZhi: String,
    val energyChange: String,
    val fieldEffect: String,
    val focus: String,
    val technical: List<String>,
    val carryForward: String,
    val sourceElement: String,
    val targetElement: String,
    val mainRelation: String,
    val targetLabel: String,
    val repeatedTouch: Boolean,
    val priorState: String = "",
    val resultingState: String = "",
    val condition: String = "",
    val relationKind: EnergyRelation = EnergyRelation.UNKNOWN,
    val changeRole: ChangeRole = ChangeRole.BACKGROUND,
    val channel: EvidenceChannel = EvidenceChannel.STEM,
    val driverGan: String = "",
    val affectsCore: Boolean = false,
    val techniques: Set<String> = emptySet(),
    val evidence: List<String> = emptyList(),
    val sourceAvailable: Boolean = false,
    val sourceRestricted: Boolean = false,
    val inheritedFrom: List<String> = emptyList(),
    val paths: List<ImpactPath> = emptyList()
)

data class ReadingV5(
    val natal: NatalAnalysisV5,
    val layers: List<LayerAnalysisV5>,
    val qi: List<String>,
    val image: String,
    val bodyUse: List<String>,
    val tenGod: String,
    val tenGodMeaning: String,
    val judgment: LinkedHashMap<String, String>,
    val signature: String,
    val memoryBefore: String,
    val focalLayer: String? = null,
    val causalChain: String = "",
    val currentClimate: String = "",
    val triggerLayer: String? = null,
    val finalSourceAvailable: Boolean = false,
    val finalSourceRestricted: Boolean = false,
    val finalSourceState: String = "",
    val finalEnergy: Map<String, EnergyAvailability> = emptyMap(),
    val natalEnergy: Map<String, EnergyAvailability> = emptyMap(),
    val finalPathEnergy: Map<String, PathEnergy> = emptyMap()
) {
    fun focal(): LayerAnalysisV5? = layers.firstOrNull { it.layer == focalLayer }
        ?: layers.lastOrNull()
    fun allPaths(): List<ImpactPath> = layers.flatMap { it.paths }
    fun energyOf(path: ImpactPath): PathEnergy = finalPathEnergy[path.id] ?: PathEnergy(
        finalEnergy[path.sourceElement] ?: path.sourceAtEntry,
        finalEnergy[path.targetElement] ?: path.targetAtEntry
    )
}

data class CalibrationV5(
    val result: String,
    val actualDomain: String,
    val text: String,
    val memoryAfter: String,
    val memoryRaw: String
)

/** Retain the existing UI contract; all analysis now shares one sequential state model. */
object V5AnalysisEngine {
    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): ReadingV5 =
        SequentialAnalysisEngine.analyze(snapshot, target, memoryRaw)

    fun calibrate(reading: ReadingV5, actualEvent: String, memoryRaw: String): CalibrationV5 {
        val result = GroundedCalibrationEngine.calibrate(
            reading.signature,
            GroundedReading(reading.image, GroundedTenGod("", "", "", "", reading.tenGod, "", ""), reading.judgment),
            actualEvent, memoryRaw
        )
        return CalibrationV5(result.result, result.actualDomain, result.text, result.memoryAfter, result.memoryRaw)
    }
}
