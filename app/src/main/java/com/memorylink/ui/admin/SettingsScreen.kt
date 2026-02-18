package com.memorylink.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Settings screen for display configuration.
 *
 * Shows current settings and allows editing:
 * - Wake time (fixed or solar-based)
 * - Sleep time (fixed or solar-based)
 * - Brightness
 * - Time format (12/24 hour)
 * - Show year in date
 *
 * Settings can be changed here or via [CONFIG] calendar events - last write wins.
 */
@Composable
fun SettingsScreen(
        settingsState: SettingsState,
        onWakeTimeChange: (LocalTime?) -> Unit,
        onSleepTimeChange: (LocalTime?) -> Unit,
        onWakeSolarTimeChange: (String, Int) -> Unit,
        onSleepSolarTimeChange: (String, Int) -> Unit,
        onBrightnessChange: (Int?) -> Unit,
        onTimeFormatChange: (Boolean?) -> Unit,
        onShowYearChange: (Boolean?) -> Unit,
        onBackClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        // Determine effective time format for display
        val use24Hour = settingsState.use24HourFormat ?: false

        Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // Header with back button
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                TextButton(onClick = onBackClick) {
                                        Text(text = "← Back", fontSize = 16.sp, color = AccentBlue)
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                                text = "Display Settings",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Wake Time
                        TimeSettingItem(
                                title = "Wake Time",
                                description = "When display enters full brightness mode",
                                currentTime = settingsState.wakeTime,
                                currentSolarRef = settingsState.wakeSolarRef,
                                currentSolarOffset = settingsState.wakeSolarOffset,
                                resolvedTime = settingsState.resolvedWakeTime,
                                use24HourFormat = use24Hour,
                                solarOptions = SolarTimeOption.WAKE_OPTIONS,
                                onTimeSelected = onWakeTimeChange,
                                onSolarTimeSelected = onWakeSolarTimeChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Sleep Time
                        TimeSettingItem(
                                title = "Sleep Time",
                                description = "When display enters dimmed mode",
                                currentTime = settingsState.sleepTime,
                                currentSolarRef = settingsState.sleepSolarRef,
                                currentSolarOffset = settingsState.sleepSolarOffset,
                                resolvedTime = settingsState.resolvedSleepTime,
                                use24HourFormat = use24Hour,
                                solarOptions = SolarTimeOption.SLEEP_OPTIONS,
                                onTimeSelected = onSleepTimeChange,
                                onSolarTimeSelected = onSleepSolarTimeChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Brightness
                        BrightnessSettingItem(
                                currentValue = settingsState.brightness ?: 100,
                                onValueChange = onBrightnessChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Time Format
                        TimeFormatSettingItem(
                                use24Hour = use24Hour,
                                onFormatChange = onTimeFormatChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Show Year
                        ShowYearSettingItem(
                                showYear = settingsState.showYearInDate ?: true,
                                onShowYearChange = onShowYearChange
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                }
        }
}

/** Time setting item that supports both fixed time and solar-based options. */
@Composable
private fun TimeSettingItem(
        title: String,
        description: String,
        currentTime: LocalTime?,
        currentSolarRef: String?,
        currentSolarOffset: Int,
        resolvedTime: LocalTime,
        use24HourFormat: Boolean,
        solarOptions: List<SolarTimeOption>,
        onTimeSelected: (LocalTime?) -> Unit,
        onSolarTimeSelected: (String, Int) -> Unit
) {
        var showTimePicker by remember { mutableStateOf(false) }
        var showSolarPicker by remember { mutableStateOf(false) }

        val timePattern = if (use24HourFormat) "HH:mm" else "h:mm a"
        val isSolarTime = currentSolarRef != null

        // Display value: show solar label if using solar, otherwise show resolved time
        val displayValue: String =
                if (isSolarTime) {
                        SolarTimeOption(currentSolarRef!!, currentSolarOffset).displayLabel
                } else {
                        resolvedTime.format(DateTimeFormatter.ofPattern(timePattern))
                }

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(text = description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(12.dp))

                // Current value display
                Column {
                        Text(
                                text = displayValue,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                        )
                        if (isSolarTime) {
                                // Show resolved time for solar
                                Text(
                                        text =
                                                "Today: ${resolvedTime.format(DateTimeFormatter.ofPattern(timePattern))}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons row
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Button(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (!isSolarTime)
                                                                AccentBlue.copy(alpha = 0.3f)
                                                        else Color(0xFF2A2A2A)
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(text = "Fixed Time", fontSize = 14.sp, color = Color.White) }

                        Button(
                                onClick = { showSolarPicker = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (isSolarTime)
                                                                AccentBlue.copy(alpha = 0.3f)
                                                        else Color(0xFF2A2A2A)
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(text = "Solar Time", fontSize = 14.sp, color = Color.White) }
                }
        }

        if (showTimePicker) {
                SimpleTimePickerDialog(
                        initialTime = currentTime ?: resolvedTime,
                        use24HourFormat = use24HourFormat,
                        onTimeSelected = {
                                onTimeSelected(it)
                                showTimePicker = false
                        },
                        onDismiss = { showTimePicker = false }
                )
        }

        if (showSolarPicker) {
                SolarTimePickerDialog(
                        options = solarOptions,
                        selectedRef = currentSolarRef,
                        selectedOffset = currentSolarOffset,
                        onOptionSelected = { option ->
                                onSolarTimeSelected(option.solarRef, option.offsetMinutes)
                                showSolarPicker = false
                        },
                        onDismiss = { showSolarPicker = false }
                )
        }
}

@Composable
private fun SolarTimePickerDialog(
        options: List<SolarTimeOption>,
        selectedRef: String?,
        selectedOffset: Int,
        onOptionSelected: (SolarTimeOption) -> Unit,
        onDismiss: () -> Unit
) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
        ) {
                Column(
                        modifier =
                                Modifier.clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable(enabled = false) {}
                                        .padding(24.dp)
                                        .width(280.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                                text = "Select Solar Time",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                options.forEach { option ->
                                        val isSelected =
                                                selectedRef == option.solarRef &&
                                                        selectedOffset == option.offsetMinutes

                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                        if (isSelected)
                                                                                AccentBlue.copy(
                                                                                        alpha = 0.3f
                                                                                )
                                                                        else Color(0xFF2A2A2A)
                                                                )
                                                                .clickable {
                                                                        onOptionSelected(option)
                                                                }
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 12.dp
                                                                )
                                        ) {
                                                Text(
                                                        text = option.displayLabel,
                                                        fontSize = 16.sp,
                                                        color =
                                                                if (isSelected) AccentBlue
                                                                else Color.White
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(onClick = onDismiss) {
                                Text(
                                        "Cancel",
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                )
                        }
                }
        }
}

@Composable
private fun SimpleTimePickerDialog(
        initialTime: LocalTime,
        use24HourFormat: Boolean,
        onTimeSelected: (LocalTime) -> Unit,
        onDismiss: () -> Unit
) {
        var hour24 by remember { mutableIntStateOf(initialTime.hour) }
        var minute by remember { mutableIntStateOf(initialTime.minute) }

        val displayHour =
                if (use24HourFormat) hour24
                else
                        when (hour24) {
                                0 -> 12
                                in 1..12 -> hour24
                                else -> hour24 - 12
                        }
        val isAm = hour24 < 12

        fun updateHour24(newDisplayHour: Int, newIsAm: Boolean) {
                hour24 =
                        if (use24HourFormat) newDisplayHour
                        else
                                when {
                                        newDisplayHour == 12 && newIsAm -> 0
                                        newDisplayHour == 12 && !newIsAm -> 12
                                        newIsAm -> newDisplayHour
                                        else -> newDisplayHour + 12
                                }
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
        ) {
                Column(
                        modifier =
                                Modifier.clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable(enabled = false) {}
                                        .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                                text = "Select Time",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Hour
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        TextButton(
                                                onClick = {
                                                        if (use24HourFormat) hour24 = (hour24 + 1) % 24
                                                        else {
                                                                val newHour =
                                                                        if (displayHour == 12) 1
                                                                        else displayHour + 1
                                                                updateHour24(newHour, isAm)
                                                        }
                                                }
                                        ) { Text("▲", fontSize = 24.sp, color = AccentBlue) }
                                        Text(
                                                text =
                                                        if (use24HourFormat)
                                                                hour24.toString().padStart(2, '0')
                                                        else displayHour.toString(),
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                        TextButton(
                                                onClick = {
                                                        if (use24HourFormat)
                                                                hour24 = if (hour24 > 0) hour24 - 1 else 23
                                                        else {
                                                                val newHour =
                                                                        if (displayHour == 1) 12
                                                                        else displayHour - 1
                                                                updateHour24(newHour, isAm)
                                                        }
                                                }
                                        ) { Text("▼", fontSize = 24.sp, color = AccentBlue) }
                                }

                                Text(
                                        text = ":",
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                // Minute
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        TextButton(onClick = { minute = (minute + 5) % 60 }) {
                                                Text("▲", fontSize = 24.sp, color = AccentBlue)
                                        }
                                        Text(
                                                text = minute.toString().padStart(2, '0'),
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                        TextButton(
                                                onClick = {
                                                        minute = if (minute >= 5) minute - 5 else 55
                                                }
                                        ) { Text("▼", fontSize = 24.sp, color = AccentBlue) }
                                }

                                // AM/PM
                                if (!use24HourFormat) {
                                        Spacer(modifier = Modifier.padding(start = 16.dp))
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                TextButton(
                                                        onClick = { updateHour24(displayHour, true) },
                                                        modifier =
                                                                Modifier.clip(RoundedCornerShape(8.dp))
                                                                        .background(
                                                                                if (isAm)
                                                                                        AccentBlue.copy(
                                                                                                alpha = 0.3f
                                                                                        )
                                                                                else Color.Transparent
                                                                        )
                                                ) {
                                                        Text(
                                                                "AM",
                                                                fontSize = 20.sp,
                                                                fontWeight =
                                                                        if (isAm) FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                color =
                                                                        if (isAm) AccentBlue
                                                                        else Color.White.copy(alpha = 0.5f)
                                                        )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                TextButton(
                                                        onClick = { updateHour24(displayHour, false) },
                                                        modifier =
                                                                Modifier.clip(RoundedCornerShape(8.dp))
                                                                        .background(
                                                                                if (!isAm)
                                                                                        AccentBlue.copy(
                                                                                                alpha = 0.3f
                                                                                        )
                                                                                else Color.Transparent
                                                                        )
                                                ) {
                                                        Text(
                                                                "PM",
                                                                fontSize = 20.sp,
                                                                fontWeight =
                                                                        if (!isAm) FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                color =
                                                                        if (!isAm) AccentBlue
                                                                        else Color.White.copy(alpha = 0.5f)
                                                        )
                                                }
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                                TextButton(onClick = onDismiss) {
                                        Text(
                                                "Cancel",
                                                fontSize = 16.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                        )
                                }
                                Button(
                                        onClick = { onTimeSelected(LocalTime.of(hour24, minute)) },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = AccentBlue
                                                )
                                ) { Text("Set Time", fontSize = 16.sp, color = Color.White) }
                        }
                }
        }
}

@Composable
private fun BrightnessSettingItem(currentValue: Int, onValueChange: (Int?) -> Unit) {
        var sliderValue by remember(currentValue) { mutableIntStateOf(currentValue) }

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Text(
                        text = "Brightness",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = "Screen brightness during wake hours",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = "$currentValue%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = { sliderValue = it.toInt() },
                        onValueChangeFinished = { onValueChange(sliderValue) },
                        valueRange = 10f..100f,
                        steps = 8,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = AccentBlue,
                                        activeTrackColor = AccentBlue,
                                        inactiveTrackColor = Color(0xFF3A3A3A)
                                )
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text("10%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("100%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
        }
}

@Composable
private fun TimeFormatSettingItem(use24Hour: Boolean, onFormatChange: (Boolean?) -> Unit) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Text(
                        text = "Time Format",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = "Clock display format",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = if (use24Hour) "24-hour (14:30)" else "12-hour (2:30 PM)",
                                fontSize = 16.sp,
                                color = AccentBlue
                        )

                        Switch(
                                checked = use24Hour,
                                onCheckedChange = { onFormatChange(it) },
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = AccentBlue,
                                                checkedTrackColor = AccentBlue.copy(alpha = 0.5f),
                                                uncheckedThumbColor = Color(0xFF3A3A3A),
                                                uncheckedTrackColor = Color(0xFF2A2A2A)
                                        )
                        )
                }
        }
}

@Composable
private fun ShowYearSettingItem(showYear: Boolean, onShowYearChange: (Boolean?) -> Unit) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Text(
                        text = "Show Year",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = "Display year in date",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text =
                                        if (showYear) "February 11, 2026"
                                        else "February 11",
                                fontSize = 16.sp,
                                color = AccentBlue
                        )

                        Switch(
                                checked = showYear,
                                onCheckedChange = { onShowYearChange(it) },
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = AccentBlue,
                                                checkedTrackColor = AccentBlue.copy(alpha = 0.5f),
                                                uncheckedThumbColor = Color(0xFF3A3A3A),
                                                uncheckedTrackColor = Color(0xFF2A2A2A)
                                        )
                        )
                }
        }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 400, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
        MemoryLinkTheme {
                SettingsScreen(
                        settingsState =
                                SettingsState(
                                        wakeTime = LocalTime.of(7, 0),
                                        sleepTime = LocalTime.of(21, 30),
                                        brightness = 80,
                                        use24HourFormat = false
                                ),
                        onWakeTimeChange = {},
                        onSleepTimeChange = {},
                        onWakeSolarTimeChange = { _, _ -> },
                        onSleepSolarTimeChange = { _, _ -> },
                        onBrightnessChange = {},
                        onTimeFormatChange = {},
                        onShowYearChange = {},
                        onBackClick = {}
                )
        }
}
