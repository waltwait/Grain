package tw.luma.camera.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import tw.luma.camera.R

/** One variable font file serves every weight: each weight is the same file at a different axis value. */
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Interface text. Characters outside the bundled subset fall back to the system font. */
val NotoSansTc = FontFamily(
    variable(R.font.noto_sans_tc, FontWeight.Normal), variable(R.font.noto_sans_tc, FontWeight.Medium),
    variable(R.font.noto_sans_tc, FontWeight.SemiBold), variable(R.font.noto_sans_tc, FontWeight.Bold),
)

/** The "Grain" brand title. */
val NewsreaderBrand = FontFamily(variable(R.font.newsreader, FontWeight.Normal), variable(R.font.newsreader, FontWeight.Bold))

private fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

val GrainTypography: Typography = Typography().withFontFamily(NotoSansTc)
