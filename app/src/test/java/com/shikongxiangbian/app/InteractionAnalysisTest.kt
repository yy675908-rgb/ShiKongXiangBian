package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class InteractionAnalysisTest {
    private val ready = EnergyAvailability(true, true, true, false, "display only")
    private fun basis(roots: List<RootEvidence>, month: String = "子", season: String = "当令") =
        EndpointBasis("子", month, "当令", season, roots, emptySet(), emptyList())
    private fun node(label: String, gan: String, element: String, b: EndpointBasis? = null) =
        EnergyEndpoint("$label:$gan:STEM", label, gan, element, ready, b)
    private val root = RootEvidence("月柱", "子", "癸", true, true)
    private val water = node("月柱", "壬", "水", basis(listOf(root)))
    private val metal = node("年柱", "庚", "金")
    private val wood = node("日柱", "甲", "木")
    private fun field(vararg nodes: EnergyEndpoint) = EnergyConfigurationInterpreter.analyze("木", nodes.toList())

    @Test fun sameBooleanFlagsCannotHideDifferentRootsOrSeason() {
        val after = water.copy(basis = basis(listOf(root.copy(clashes = setOf("流时丙午"))), "午", "逆时制约，需有根承接"))
        assertEquals(water.state, after.state)
        val result = CombinedImageInterpreter.assess(field(metal, after, wood), field(metal, water, wood))
            .single { it.configuration.elements == listOf("金", "水", "木") }
        assertTrue(result.changed)
        assertTrue(result.changes.any { it.contains("受冲方由无变为流时丙午") })
        assertTrue(result.changes.any { it.contains("子月当令 → 午月") })
        assertFalse(CombinedImageInterpreter.assess(field(metal, after, wood), field(metal, after, wood)).any { it.changed })
    }

    @Test fun everyConcurrentChangeIsRetained() {
        val after = water.copy(state = ready.copy(rooted = false, available = false, restricted = true),
            basis = basis(emptyList()))
        val changes = InteractionAnalysis.changes(listOf(water), listOf(after))
        assertTrue(changes.any { it.contains("本层受牵制") })
        assertTrue(changes.any { it.contains("作用条件不足") })
        assertTrue(changes.any { it.contains("失去根气") })
        assertTrue(changes.any { it.contains("失去月柱子") })
    }

    @Test fun aGeneratingChainDoesNotEraseDirectControl() {
        val f = field(metal, water, wood)
        val review = InteractionAnalysis.review(ConfigurationBinding(listOf(metal, water, wood)), f)
        assertTrue(review.conclusion.contains("年柱庚既克日柱甲"))
        assertTrue(review.conclusion.contains("经月柱壬生日柱甲"))
        assertFalse(review.conclusion.contains("已经缓解"))
    }

    @Test fun anotherReadyWaterStemCannotReleaseTheRestrictedBridge() {
        val tied = water.copy(state = ready.copy(restricted = true))
        val other = node("流年", "癸", "水")
        val review = InteractionAnalysis.review(ConfigurationBinding(listOf(metal, tied, wood)), field(metal, tied, wood, other))
        assertTrue(review.conclusion.contains("月柱壬受冲合牵制"))
        val pair = InteractionAnalysis.review(ConfigurationBinding(listOf(metal, wood)), field(metal, tied, wood, other))
        assertTrue(pair.conclusion.contains("经流年癸生日柱甲"))
        assertFalse(pair.conclusion.contains("经月柱壬"))
    }

    @Test fun outgoingDrainAndAttackOnAnExternalSourceAreBothRetained() {
        val fire = node("时柱", "丙", "火")
        val earth = node("流年", "戊", "土")
        val f = field(metal, water, wood, fire, earth)
        val context = InteractionAnalysis.context(f, setOf(metal.key, wood.key))
        assertTrue(context.any { it.source.key == wood.key && it.target.key == fire.key && it.relation == EnergyRelation.GENERATES })
        assertTrue(context.any { it.source.key == earth.key && it.target.key == water.key && it.relation == EnergyRelation.CONTROLS })
    }

    @Test fun temporaryRootsAreNotReportedAsPermanentFoundations() {
        val transient = water.copy(basis = basis(listOf(root.copy(label = "流时", natal = false))))
        val review = InteractionAnalysis.review(ConfigurationBinding(listOf(metal, transient, wood)), field(metal, transient, wood))
        assertTrue(review.dependencies.any { it.contains("根均来自流时") })
        val natal = InteractionAnalysis.review(ConfigurationBinding(listOf(metal, water, wood)), field(metal, water, wood))
        assertFalse(natal.dependencies.any { it.contains("根均来自流时") })
    }

    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    @Test fun productionPipelineCarriesRootEvidenceWithoutFutureLeakage() {
        val s = AnalysisSnapshot(listOf("年柱" to "庚申", "月柱" to "壬子", "日柱" to "甲寅", "时柱" to "乙卯")
            .map { pillar(it.first, it.second) }, listOf(pillar("流年", "丙午")), "甲", null, emptyList())
        val t = LocalDateTime.of(2026, 10, 16, 12, 0)
        val early = V5AnalysisEngine.analyze(s, t, "")
        val full = V5AnalysisEngine.analyze(s.copy(dynamic = s.dynamic + pillar("流月", "丁酉")), t, "")
        assertEquals(early.natal, full.natal)
        assertEquals(early.layers, full.layers.take(1))
        val b = early.field.endpoints.single { it.label == "月柱" }.basis!!
        assertTrue(b.roots.any { it.branch == "子" && "流年丙午" in it.clashes })
        assertTrue(b.roots.any { it.branch == "申" && !it.main })
        assertEquals("子", b.natalMonth)
        assertTrue(b.phases.any { it.label == "月柱" && it.phase == GanZhiEngine.changSheng("壬", "子") })
        val later = full.field.endpoints.single { it.label == "月柱" }.basis!!
        assertEquals("酉", later.currentMonth)
        assertEquals("子", later.natalMonth)
    }
}
