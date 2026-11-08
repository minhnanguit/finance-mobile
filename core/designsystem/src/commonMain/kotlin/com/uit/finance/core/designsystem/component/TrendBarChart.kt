package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/** Cách tô một cột của [TrendBarChart]. */
enum class TrendBarStyle {
    /** Kỳ đang xem: màu đậm. */
    Selected,

    /** Kỳ khác: màu nhạt. */
    Normal,

    /** Kỳ chưa hết (tháng hiện tại khi đang xem tháng khác): nhạt hơn nữa, để không bị so như kỳ đủ. */
    Partial,
}

/**
 * @param actionLabel khác `null` thì cột chạm được (gọi `onSelect`) và trình đọc màn hình đọc cột là một nút mang nhãn
 *   này; `null` = cột không chọn được (kỳ đang xem, tháng tương lai)
 */
@Immutable
data class TrendBar(
    val label: String,
    val value: Double,
    val style: TrendBarStyle = TrendBarStyle.Normal,
    val actionLabel: String? = null,
)

/**
 * Cột xu hướng một dãy (docs/REPORTS-PLAN.md V5): mỗi kỳ một cột, kỳ đang xem tô đậm. Chạm cột gọi [onSelect] với vị
 * trí cột. Trục dọc số rút gọn ([axisLabel], V6), không cuộn, không zoom. Vico chỉ được dùng trong file này (L2, L3).
 *
 * Chạm cột KHÔNG đi qua marker của Vico: Vico lưu vị trí marker (rememberSaveable) và gọi lại `onShown` mỗi khi biểu đồ
 * dựng lại với dữ liệu mới, nên đổi kỳ / cuộn màn sẽ tự "chạm" lại cột cũ. Thay vào đó là một hàng ô trong suốt phủ
 * đúng vùng cột: trục dọc rộng cố định [AXIS_WIDTH], `Zoom.Content` dàn đều các cột trên phần còn lại.
 */
@Composable
fun TrendBarChart(
    bars: List<TrendBar>,
    color: Color,
    axisLabel: (Double) -> String,
    contentDescription: String,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    // Giá trị, nhãn, kiểu cột đi CÙNG một transaction (ExtraStore của model): Vico cập nhật model bất đồng bộ, nếu nhãn
    // đọc thẳng từ `bars` thì lúc đổi 12 cột (Năm) → 6 cột (Tháng) trục hỏi nhãn cột 7–12 chưa có và Vico ném lỗi.
    LaunchedEffect(bars) {
        if (bars.isNotEmpty()) {
            producer.runTransaction {
                columnSeries { series(bars.map { it.value }) }
                extras { store ->
                    store[LabelsKey] = bars.map { it.label }
                    store[StylesKey] = bars.map { it.style }
                }
            }
        }
    }

    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
    val selected = rememberLineComponent(Fill(color), thickness = COLUMN_WIDTH.dp, shape = shape)
    val normal = rememberLineComponent(Fill(color.copy(alpha = 0.35f)), thickness = COLUMN_WIDTH.dp, shape = shape)
    val partial = rememberLineComponent(Fill(color.copy(alpha = 0.18f)), thickness = COLUMN_WIDTH.dp, shape = shape)
    val columnProvider = remember(selected, normal, partial) {
        StyledColumns { style ->
            when (style) {
                TrendBarStyle.Selected -> selected
                TrendBarStyle.Normal -> normal
                TrendBarStyle.Partial -> partial
            }
        }
    }

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val currentAxisLabel = rememberUpdatedState(axisLabel)

    Box(modifier) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columnProvider,
                columnCollectionSpacing = 12.dp,
                rangeProvider = remember { ZeroBasedRange },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                label = rememberTextComponent(style = labelStyle),
                guideline = rememberLineComponent(Fill(MaterialTheme.colorScheme.outline), thickness = 1.dp),
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentAxisLabel.value(value) } },
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ AXIS_LABEL_COUNT }) },
                size = remember { BaseAxis.Size.Fixed(AXIS_WIDTH) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                tick = null,
                guideline = null,
                label = rememberTextComponent(style = labelStyle),
                valueFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        context.model.extraStore.getOrNull(LabelsKey)?.getOrNull(x.toInt())?.takeIf { it.isNotBlank() }
                            ?: "${x.toInt() + 1}" // Vico cấm nhãn rỗng
                    }
                },
            ),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxSize().semantics { this.contentDescription = contentDescription },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
    BarTapTargets(bars, onSelect)
    }
}

/** Mỗi cột một ô chạm cao hết biểu đồ; không giữ trạng thái gì, nên dữ liệu đổi không bao giờ tự chọn cột. */
@Composable
private fun BarTapTargets(bars: List<TrendBar>, onSelect: (index: Int) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Spacer(Modifier.width(AXIS_WIDTH))
        bars.forEachIndexed { index, bar ->
            val label = bar.actionLabel
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (label == null) {
                            Modifier.clearAndSetSemantics {}
                        } else {
                            Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = label,
                                    role = Role.Button,
                                    onClick = { onSelect(index) },
                                )
                                .semantics { contentDescription = label }
                        },
                    ),
            )
        }
    }
}

private val LabelsKey = ExtraStore.Key<List<String>>()
private val StylesKey = ExtraStore.Key<List<TrendBarStyle>>()

/** Cột thứ x lấy kiểu đi kèm model (cùng transaction với giá trị). */
private class StyledColumns(
    private val component: (TrendBarStyle) -> LineComponent,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore): LineComponent =
        component(extraStore.getOrNull(StylesKey)?.getOrNull(entry.x.toInt()) ?: TrendBarStyle.Normal)

    override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore): LineComponent =
        component(TrendBarStyle.Selected)
}

/** Trục dọc luôn từ 0; toàn số 0 vẫn có thang để vẽ. */
private object ZeroBasedRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = 0.0
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = if (maxY > 0.0) maxY else 1.0
}

private const val COLUMN_WIDTH = 18
private const val AXIS_LABEL_COUNT = 3

/** Bề rộng cố định của trục dọc: đủ cho nhãn dài nhất (`1,2 tỷ`, `999,9tr`), để ô chạm khớp vùng cột. */
private val AXIS_WIDTH = 44.dp
