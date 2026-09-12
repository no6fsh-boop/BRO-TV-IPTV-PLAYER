package com.brotv.iptv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotv.iptv.ui.theme.BroTvColors

/**
 * Single reusable BRO TV IPTV PLAYER brand mark.
 * The layout mirrors the owner's reference: gold BRO, white TV,
 * gold outlined play mark, with IPTV PLAYER underneath.
 */
@Composable
fun BroTvBrandLogo(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val titleSize = if (compact) 24.sp else 42.sp
    val subtitleSize = if (compact) 7.sp else 10.sp
    val playSize = if (compact) 27.dp else 42.dp

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "BRO",
                color = BroTvColors.Gold,
                fontSize = titleSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(if (compact) 3.dp else 5.dp))
            Text(
                text = "TV",
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(if (compact) 5.dp else 8.dp))
            Canvas(Modifier.size(playSize)) {
                val outer = Path().apply {
                    moveTo(size.width * .18f, size.height * .08f)
                    lineTo(size.width * .86f, size.height * .50f)
                    lineTo(size.width * .18f, size.height * .92f)
                    close()
                }
                drawPath(
                    path = outer,
                    color = BroTvColors.Gold,
                    style = Stroke(
                        width = size.minDimension * .14f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                val play = Path().apply {
                    moveTo(size.width * .36f, size.height * .30f)
                    lineTo(size.width * .70f, size.height * .50f)
                    lineTo(size.width * .36f, size.height * .70f)
                    close()
                }
                drawPath(play, BroTvColors.Gold)
            }
        }
        Text(
            text = "I P T V   P L A Y E R",
            color = Color.White.copy(alpha = .78f),
            fontSize = subtitleSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = if (compact) .5.sp else 1.sp,
        )
    }
}
