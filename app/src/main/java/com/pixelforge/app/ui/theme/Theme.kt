package com.pixelforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Tokens
object ColorTokens {
    // Brand colors
    val Primary = Color(0xFF4C8DFF)
    val PrimaryContainer = Color(0xFFD6E8FF)
    val OnPrimary = Color.White
    val OnPrimaryContainer = Color(0xFF001E3D)
    
    val Secondary = Color(0xFF7C5CFF)
    val SecondaryContainer = Color(0xFFE8E0FF)
    val OnSecondary = Color.White
    val OnSecondaryContainer = Color(0xFF2C1A5C)
    
    val Tertiary = Color(0xFF00C896)
    val TertiaryContainer = Color(0xFFA7F3D9)
    val OnTertiary = Color.White
    val OnTertiaryContainer = Color(0xFF003827)
    
    // Surface colors
    val Surface = Color(0xFF141419)
    val SurfaceVariant = Color(0xFF1E1E26)
    val SurfaceContainer = Color(0xFF24242C)
    val SurfaceContainerHigh = Color(0xFF2E2E38)
    val OnSurface = Color.White
    val OnSurfaceVariant = Color(0xFFC4C4CC)
    
    // Background
    val Background = Color(0xFF0B0B0F)
    val OnBackground = Color.White
    
    // Error
    val Error = Color(0xFFFF6B6B)
    val ErrorContainer = Color(0xFF4C0000)
    val OnError = Color.White
    val OnErrorContainer = Color(0xFFFFDADA)
    
    // Outline
    val Outline = Color(0xFF3A3A42)
    val OutlineVariant = Color(0xFF2A2A32)
    
    // Inverse
    val InverseSurface = Color(0xFFE8E8ED)
    val InverseOnSurface = Color(0xFF1A1A1F)
    val InversePrimary = Color(0xFF0056A8)
    
    // Shadow
    val Shadow = Color(0xFF000000)
    val Scrim = Color(0xFF000000)
    
    // Light theme variants
    val LightPrimary = Color(0xFF0056A8)
    val LightPrimaryContainer = Color(0xFFD6E8FF)
    val LightOnPrimary = Color.White
    val LightOnPrimaryContainer = Color(0xFF001E3D)
    
    val LightSecondary = Color(0xFF5A3FC8)
    val LightSecondaryContainer = Color(0xFFE8E0FF)
    val LightOnSecondary = Color.White
    val LightOnSecondaryContainer = Color(0xFF2C1A5C)
    
    val LightTertiary = Color(0xFF006B50)
    val LightTertiaryContainer = Color(0xFFA7F3D9)
    val LightOnTertiary = Color.White
    val LightOnTertiaryContainer = Color(0xFF003827)
    
    val LightSurface = Color(0xFFFAFAFF)
    val LightSurfaceVariant = Color(0xFFE8E8EF)
    val LightSurfaceContainer = Color(0xFFF2F2F9)
    val LightSurfaceContainerHigh = Color(0xFFE8E8EF)
    val LightOnSurface = Color(0xFF1A1A1F)
    val LightOnSurfaceVariant = Color(0xFF44444E)
    
    val LightBackground = Color(0xFFFAFAFF)
    val LightOnBackground = Color(0xFF1A1A1F)
    
    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFF9DEDC)
    val LightOnError = Color.White
    val LightOnErrorContainer = Color(0xFF410002)
    
    val LightOutline = Color(0xFF73737C)
    val LightOutlineVariant = Color(0xFFC4C4CD)
    
    val LightInverseSurface = Color(0xFF2E2E35)
    val LightInverseOnSurface = Color(0xFFFAFAFF)
    val LightInversePrimary = Color(0xFF94C4FF)
}

@Composable
fun PixelForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ColorTokens.Primary,
            primaryContainer = ColorTokens.PrimaryContainer,
            onPrimary = ColorTokens.OnPrimary,
            onPrimaryContainer = ColorTokens.OnPrimaryContainer,
            
            secondary = ColorTokens.Secondary,
            secondaryContainer = ColorTokens.SecondaryContainer,
            onSecondary = ColorTokens.OnSecondary,
            onSecondaryContainer = ColorTokens.OnSecondaryContainer,
            
            tertiary = ColorTokens.Tertiary,
            tertiaryContainer = ColorTokens.TertiaryContainer,
            onTertiary = ColorTokens.OnTertiary,
            onTertiaryContainer = ColorTokens.OnTertiaryContainer,
            
            surface = ColorTokens.Surface,
            surfaceVariant = ColorTokens.SurfaceVariant,
            surfaceContainer = ColorTokens.SurfaceContainer,
            surfaceContainerHigh = ColorTokens.SurfaceContainerHigh,
            onSurface = ColorTokens.OnSurface,
            onSurfaceVariant = ColorTokens.OnSurfaceVariant,
            
            background = ColorTokens.Background,
            onBackground = ColorTokens.OnBackground,
            
            error = ColorTokens.Error,
            errorContainer = ColorTokens.ErrorContainer,
            onError = ColorTokens.OnError,
            onErrorContainer = ColorTokens.OnErrorContainer,
            
            outline = ColorTokens.Outline,
            outlineVariant = ColorTokens.OutlineVariant,
            
            inverseSurface = ColorTokens.InverseSurface,
            inverseOnSurface = ColorTokens.InverseOnSurface,
            inversePrimary = ColorTokens.InversePrimary,
            
            shadow = ColorTokens.Shadow,
            scrim = ColorTokens.Scrim,
        )
    } else {
        lightColorScheme(
            primary = ColorTokens.LightPrimary,
            primaryContainer = ColorTokens.LightPrimaryContainer,
            onPrimary = ColorTokens.LightOnPrimary,
            onPrimaryContainer = ColorTokens.LightOnPrimaryContainer,
            
            secondary = ColorTokens.LightSecondary,
            secondaryContainer = ColorTokens.LightSecondaryContainer,
            onSecondary = ColorTokens.LightOnSecondary,
            onSecondaryContainer = ColorTokens.LightOnSecondaryContainer,
            
            tertiary = ColorTokens.LightTertiary,
            tertiaryContainer = ColorTokens.LightTertiaryContainer,
            onTertiary = ColorTokens.LightOnTertiary,
            onTertiaryContainer = ColorTokens.LightOnTertiaryContainer,
            
            surface = ColorTokens.LightSurface,
            surfaceVariant = ColorTokens.LightSurfaceVariant,
            surfaceContainer = ColorTokens.LightSurfaceContainer,
            surfaceContainerHigh = ColorTokens.LightSurfaceContainerHigh,
            onSurface = ColorTokens.LightOnSurface,
            onSurfaceVariant = ColorTokens.LightOnSurfaceVariant,
            
            background = ColorTokens.LightBackground,
            onBackground = ColorTokens.LightOnBackground,
            
            error = ColorTokens.LightError,
            errorContainer = ColorTokens.LightErrorContainer,
            onError = ColorTokens.LightOnError,
            onErrorContainer = ColorTokens.LightOnErrorContainer,
            
            outline = ColorTokens.LightOutline,
            outlineVariant = ColorTokens.LightOutlineVariant,
            
            inverseSurface = ColorTokens.LightInverseSurface,
            inverseOnSurface = ColorTokens.LightInverseOnSurface,
            inversePrimary = ColorTokens.LightInversePrimary,
            
            shadow = ColorTokens.Shadow,
            scrim = ColorTokens.Scrim,
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

// Typography Tokens
object Typography {
    val DisplayLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = -0.25.sp,
    )
    
    val DisplayMedium = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    )
    
    val DisplaySmall = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    )
    
    val HeadlineLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    )
    
    val HeadlineMedium = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    )
    
    val HeadlineSmall = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    )
    
    val TitleLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    
    val TitleMedium = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    )
    
    val TitleSmall = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )
    
    val BodyLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
    
    val BodyMedium = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )
    
    val BodySmall = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )
    
    val LabelLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )
    
    val LabelMedium = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
    
    val LabelSmall = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
}

// Shape Tokens
object Shapes {
    val ExtraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val Small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val Medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val Large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val ExtraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val Full = androidx.compose.foundation.shape.RoundedCornerShape(9999.dp)
}

// Spacing Tokens
object Spacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 16.dp
    val LG = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
}

// Elevation Tokens
object Elevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 8.dp
    val Level5 = 12.dp
}