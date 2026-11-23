package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredAnalysisEngineTest {
    private val target = LocalDateTime.of(2027, 6, 1, 12, 0)

    @Test
    fun dingWeiKeepsEveryNatalPalaceRelation() {
        val reading = analyze("丁未")
        val year = reading.layers.single { it.label == "流年" }

        assertFalse(year.stemRelations.any { it.directConflict })
        assertTrue(year.synthesis.contains("天干主线没有直接冲突"))
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("月柱") && it.relation.contains("冲") && it.relation.contains("刑") })
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("日柱") && it.relation.contains("冲") && it.relation.contains("刑") })
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("时柱") && it.relation.contains("刑") && it.relation.contains("破") })
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("年柱") && it.relation.contains("半合木") })
        assertTrue(year.structures.any { it.relation == "丑未戌三刑齐" })
        assertTrue(year.structures.any { it.relation == "辰戌丑未四墓库齐" })
        assertTrue(year.structures.any { it.relation == "一支多冲" && it.targetLabels.containsAll(listOf("月柱", "日柱")) })
    }

    @Test
    fun jiaChenSeparatesVisibleConflictFromBranchActivation() {
        val year = analyze("甲辰").layers.single { it.label == "流年" }

        assertTrue(year.stemRelations.any { it.targetLabels == listOf("大运") && it.directConflict })
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("大运") && it.relation.contains("自刑") })
        assertTrue(year.branchRelations.any { it.targetLabels == listOf("时柱") && it.relation.contains("冲") })
    }

    @Test
    fun sameWeiBranchDoesNotEraseDifferentVisibleTheme() {
        val ding = analyze("丁未").layers.single { it.label == "流年" }
        val gui = analyze("癸未").layers.single { it.label == "流年" }

        assertTrue(ding.visibleTheme.contains("偏印"))
        assertTrue(gui.visibleTheme.contains("偏财"))
        assertEquals(
            ding.branchRelations.map { it.targetLabels to it.relation },
            gui.branchRelations.map { it.targetLabels to it.relation }
        )
    }

    @Test
    fun yearScopeCannotBeOverwrittenByFinerLayers() {
        val snapshot = snapshot("丁未", listOf("癸未", "甲子", "乙丑"))
        val reading = StructuredAnalysisEngine.analyze(snapshot, target, AnalysisScope.YEAR, "")

        assertTrue(reading.layers.any { it.label == "流年" })
        assertFalse(reading.layers.any { it.label == "流月" || it.label == "流日" || it.label == "流时" })
    }

    @Test
    fun actualEventCanKeepSeveralDomainsWithoutPostHocStudyLabel() {
        val domains = StructuredCalibrationEngine.classifyActual("毕业后暑假出去旅行，吃饭玩得很开心")

        assertTrue(domains.contains("出行变动"))
        assertTrue(domains.contains("休闲享受"))
        assertFalse(domains.contains("学业工作"))
    }

    private fun analyze(year: String): StructuredReading = StructuredAnalysisEngine.analyze(
        snapshot(year), target, AnalysisScope.YEAR, ""
    )

    private fun snapshot(year: String, finer: List<String> = emptyList()): AnalysisSnapshot {
        val dynamic = buildList {
            add(pillar("流年", year))
            finer.getOrNull(0)?.let { add(pillar("流月", it)) }
            finer.getOrNull(1)?.let { add(pillar("流日", it)) }
            finer.getOrNull(2)?.let { add(pillar("流时", it)) }
        }
        return AnalysisSnapshot(
            natal = listOf(
                pillar("年柱", "己卯"),
                pillar("月柱", "丁丑"),
                pillar("日柱", "己丑"),
                pillar("时柱", "甲戌")
            ),
            dynamic = dynamic,
            dayMaster = "己",
            gender = 0,
            daYun = DaYunView("庚辰", 2022, 2031, 30, 39),
            relations = emptyList()
        )
    }

    private fun pillar(label: String, ganZhi: String): PillarView {
        val gan = ganZhi.substring(0, 1)
        val zhi = ganZhi.substring(1, 2)
        val hidden = hiddenGan[zhi].orEmpty()
        return PillarView(
            label = label,
            ganZhi = ganZhi,
            gan = gan,
            zhi = zhi,
            hiddenGan = hidden,
            tenGod = GanZhiEngine.tenGod("己", gan),
            hiddenTenGod = hidden.map { GanZhiEngine.tenGod("己", it) },
            changSheng = GanZhiEngine.changSheng("己", zhi)
        )
    }

    private val hiddenGan = mapOf(
        "子" to listOf("癸"), "丑" to listOf("己", "癸", "辛"), "寅" to listOf("甲", "丙", "戊"),
        "卯" to listOf("乙"), "辰" to listOf("戊", "乙", "癸"), "巳" to listOf("丙", "戊", "庚"),
        "午" to listOf("丁", "己"), "未" to listOf("己", "丁", "乙"), "申" to listOf("庚", "壬", "戊"),
        "酉" to listOf("辛"), "戌" to listOf("戊", "辛", "丁"), "亥" to listOf("壬", "甲")
    )
}
