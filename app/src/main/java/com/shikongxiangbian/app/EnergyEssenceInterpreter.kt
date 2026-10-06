package com.shikongxiangbian.app

enum class EnergyExchange { SHARE, TRANSFER_OUT, TRANSFER_IN, REGULATE_OUT, REGULATE_IN, UNRESOLVED }

data class EnergyProcess(
    val exchange: EnergyExchange,
    val origin: EnergyAvailability,
    val destination: EnergyAvailability,
    val mechanism: String,
    val qualification: String,
    val boundary: String
) {
    val originReady: Boolean get() = exchange != EnergyExchange.UNRESOLVED && origin.available && !origin.restricted
    fun describe(): String = "$mechanism；$qualification。$boundary"
}

/** Read the actual direction and cost before translating an endpoint into a ten-god domain. */
object EnergyEssenceInterpreter {
    fun process(path: ImpactPath, reading: ReadingV5): EnergyProcess {
        val state = reading.energyOf(path)
        return assess(path.relation, "${path.sourceLabel}${path.sourceGan}", "${path.targetLabel}${path.targetGan}", state)
    }

    fun assess(relation: EnergyRelation, sourceName: String, targetName: String, state: PathEnergy): EnergyProcess {
        val reversed = relation == EnergyRelation.GENERATED_BY || relation == EnergyRelation.CONTROLLED_BY
        val origin = if (reversed) state.target else state.source
        val destination = if (reversed) state.source else state.target
        val exchange = when (relation) {
            EnergyRelation.SAME -> EnergyExchange.SHARE
            EnergyRelation.GENERATES -> EnergyExchange.TRANSFER_OUT
            EnergyRelation.GENERATED_BY -> EnergyExchange.TRANSFER_IN
            EnergyRelation.CONTROLS -> EnergyExchange.REGULATE_OUT
            EnergyRelation.CONTROLLED_BY -> EnergyExchange.REGULATE_IN
            EnergyRelation.UNKNOWN -> EnergyExchange.UNRESOLVED
        }
        val mechanism = when (exchange) {
            EnergyExchange.SHARE -> "$sourceName 与 $targetName 同类相接，参与承载与分配"
            EnergyExchange.TRANSFER_OUT -> "$sourceName → $targetName：来源向作用端传递，来源同时有耗用"
            EnergyExchange.TRANSFER_IN -> "$targetName → $sourceName：来气取用既有端的补给，既有端同时有耗用"
            EnergyExchange.REGULATE_OUT -> "$sourceName 制约 $targetName：改变作用端的展开条件，施制方也有耗用"
            EnergyExchange.REGULATE_IN -> "$targetName 制约 $sourceName：来气受到既有端约束，不能按来气名称直接取事"
            EnergyExchange.UNRESOLVED -> "$sourceName 与 $targetName 的作用方向未定"
        }
        val actor = if (exchange == EnergyExchange.SHARE) "同类参与端" else "实际施生/施制端"
        val qualification = when {
            exchange == EnergyExchange.UNRESOLVED -> "方向待定"
            origin.restricted -> "${actor}受牵，通路待检"
            !origin.available -> "${actor}承载不足，关系已见而兑现待补"
            destination.restricted -> "${actor}有承载，但承受端另受牵，不能直接判结果"
            state.challenges.isNotEmpty() -> "来源另受后层作用，原有通路能否延续待检"
            else -> "作用端有承载；受方是否需要这股作用仍须结合原局"
        }
        val boundary = when (exchange) {
            EnergyExchange.TRANSFER_IN, EnergyExchange.TRANSFER_OUT -> "补给不等于获益，输出不等于成果；还看受方需要、供方续接与其他通路。"
            EnergyExchange.REGULATE_IN, EnergyExchange.REGULATE_OUT -> "制约可形成约束也可形成阻碍；双方相对效力未定，不直接断制住或受损。"
            EnergyExchange.SHARE -> "同类参与可协作也可分用，不能仅凭同类就定帮扶或竞争。"
            EnergyExchange.UNRESOLVED -> "先保留关系，不取确定事件。"
        }
        return EnergyProcess(exchange, origin, destination, mechanism, qualification, boundary)
    }
}
