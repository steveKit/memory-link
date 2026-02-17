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
 * Manual configuration screen for display settings.
 *
 * Allows manual override of:
 * - Wake time (fixed or solar-based)
 * - Sleep time (fixed or solar-based)
 * - Brightness
 * - Time format (12/24 hour)
 *
 * These override [CONFIG] calendar events.
 *
 * @param configState Current configuration state
 * @param onWakeTimeChange Update wake time (null to clear override)
 * @param onSleepTimeChange Update sleep time (null to clear override)
 * @param onWakeSolarTimeChange Update wake time to solar-based
 * @param onSleepSolarTimeChange Update sleep time to solar-based
 * @param onClearWakeTime Clear wake time override
 * @param onClearSleepTime Clear sleep time override
 * @param onBrightnessChange Update brightness (null to clear override)
 * @param onTimeFormatChange Update time format (null to clear override)
 * @param onShowYearChange Update show year in date (null to clear override)
 * @param onBackClick Navigate back to admin home
 * @param modifier Modifier for the screen
 */
@Composable
fun ManualConfigScreen(
        configState: ConfigState,
        onWakeTimeChange: (LocalTime?) -> Unit,
        onSleepTimeChange: (LocalTime?) -> Unit,
        onWakeSolarTimeChange: (String, Int) -> Unit = { _, _ -> },
        onSleepSolarTimeChange: (String, Int) -> Unit = { _, _ -> },
        onClearWakeTime: () -> Unit = {},
        onClearSleepTime: () -> Unit = {},
        onBrightnessChange: (Int?) -> Unit,
        onTimeFormatChange: (Boolean?) -> Unit,
        onShowYearChange: (Boolean?) -> Unit = {},
        onBackClick: () -> Unit,
        modifier: Modifier = Modifier
) {
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = "Manual overrides (takes priority over calendar configs)",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Wake Time
                        SolarTimeSettingItem(
                                title = "Wake Time",
                                description = "When display enters full brightness mode",
                                currentTime = configState.wakeTime,
                                currentSolarRef = configState.wakeSolarRef,
                                currentSolarOffset = configState.wakeSolarOffset,
                                defaultDescription = "Using calendar config or default (Sunrise)",
                                use24HourFormat = configState.use24HourFormat ?: false,
                                solarOptions = SolarTimeOption.WAKE_OPTIONS,
                                onTimeSelected = onWakeTimeChange,
                                onSolarTimeSelected = onWakeSolarTimeChange,
                                onClear = onClearWakeTime
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Sleep Time
                        SolarTimeSettingItem(
                                title = "Sleep Time",
                                description = "When display enters dimmed mode",
                                currentTime = configState.sleepTime,
                                currentSolarRef = configState.sleepSolarRef,
                                currentSolarOffset = configState.sleepSolarOffset,
                                defaultDescription =
                                        "Using calendar config or default (Sunset + 30 min)",
                                use24HourFormat = configState.use24HourFormat ?: false,
                                solarOptions = SolarTimeOption.SLEEP_OPTIONS,
                                onTimeSelected = onSleepTimeChange,
                                onSolarTimeSelected = onSleepSolarTimeChange,
                                onClear = onClearSleepTime
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Brightness
                        BrightnessSettingItem(
                                title = "Brightness",
                                description = "Screen brightness during wake hours",
                                currentValue = configState.brightness,
                                onValueChange = onBrightnessChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Time Format
                        TimeFormatSettingItem(
                                title = "Time Format",
                                description = "Clock display format",
                                use24Hour = configState.use24HourFormat,
                                onFormatChange = onTimeFormatChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Show Year in Date
                        ShowYearSettingItem(
                                title = "Show Year",
                                description = "Display year in date",
                                showYear = configState.showYearInDate,
                                onShowYearChange = onShowYearChange
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                }
        }
}

/** Time setting item that supports both fixed time and solar-based options. */
@Composable
private fun SolarTimeSettingItem(
        title: String,
        description: String,
        currentTime: LocalTime?,
        currentSolarRef: String?,
        currentSolarOffset: Int,
        defaultDescription: String,
        use24HourFormat: Boolean,
        solarOptions: List<SolarTimeOption>,
        onTimeSelected: (LocalTime?) -> Unit,
        onSolarTimeSelected: (String, Int) -> Unit,
        onClear: () -> Unit
) {
        var showTimePicker by remember { mutableStateOf(false) }
        var showSolarPicker by remember { mutableStateOf(false) }

        // Format pattern based on time format preference
        val timePattern = if (use24HourFormat) "HH:mm" else "h:mm a"

        // Determine current display value
        val hasValue = currentTime != null || currentSolarRef != null
        val displayValue: String =
                when {
                        currentTime != null ->
                                currentTime.format(DateTimeFormatter.ofPattern(timePattern))
                        currentSolarRef != null -> {
                                val option = SolarTimeOption(currentSolarRef, currentSolarOffset)
                                option.displayLabel
                        }
                        else -> ""
                }
        val isSolarTime = currentSolarRef != null

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

                if (hasValue) {
                        // Show current value
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                text = displayValue,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentBlue
                                        )
                                        if (isSolarTime) {
                                                Text(
                                                        text = "Solar-based",
                                                        fontSize = 12.sp,
                                                        color = Color.White.copy(alpha = 0.5f)
                                                )
                                        }
                                }
                                TextButton(onClick = onClear) {
                                        Text(
                                                text = "Clear",
                                                fontSize = 14.sp,
                                                color = Color(0xFFEF5350)
                                        )
                                }
                        }
                } else {
                        // Show default description
                        Text(
                                text = defaultDescription,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.5f)
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons row
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        // Fixed Time button
                        Button(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (!isSolarTime && currentTime != null)
                                                                AccentBlue.copy(alpha = 0.3f)
                                                        else Color(0xFF2A2A2A)
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(text = "Fixed Time", fontSize = 14.sp, color = Color.White) }

                        // Solar Time button
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

        // Fixed time picker dialog
        if (showTimePicker) {
                SimpleTimePickerDialog(
                        initialTime = currentTime ?: LocalTime.of(12, 0),
                        use24HourFormat = use24HourFormat,
                        onTimeSelected = {
                                onTimeSelected(it)
                                showTimePicker = false
                        },
                        onDismiss = { showTimePicker = false }
                )
        }

        // Solar time picker dialog
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

/** Dialog for selecting solar-based time options. */
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

                        Text(
                                text = "Choose relative to sunrise/sunset",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Options list
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
                                                                else Color.White,
                                                        fontWeight =
                                                                if (isSelected) FontWeight.Medium
                                                                else FontWeight.Normal
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
        // For 24-hour mode: hour is 0-23
        // For 12-hour mode: displayHour is 1-12, isAm tracks AM/PM
        var hour24 by remember { mutableIntStateOf(initialTime.hour) }
        var minute by remember { mutableIntStateOf(initialTime.minute) }

        // Derived state for 12-hour display
        val displayHour =
                if (use24HourFormat) {
                        hour24
                } else {
                        when (hour24) {
                                0 -> 12
                                in 1..12 -> hour24
                                else -> hour24 - 12
                        }
                }
        val isAm = hour24 < 12

        // Convert 12-hour display to 24-hour internal representation
        fun updateHour24(newDisplayHour: Int, newIsAm: Boolean) {
                hour24 =
                        if (use24HourFormat) {
                                newDisplayHour
                        } else {
                                when {
                                        newDisplayHour == 12 && newIsAm -> 0
                                        newDisplayHour == 12 && !newIsAm -> 12
                                        newIsAm -> newDisplayHour
                                        else -> newDisplayHour + 12
                                }
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
                                        .clickable(enabled = false) {} // Prevent click through
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
                                // Hour picker
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        TextButton(
                                                onClick = {
                                                        if (use24HourFormat) {
                                                                hour24 = (hour24 + 1) % 24
                                                        } else {
                                                                // In 12-hour mode, cycle 1-12
                                                                val newDisplayHour =
                                                                        if (displayHour == 12) 1
                                                                        else displayHour + 1
                                                                updateHour24(newDisplayHour, isAm)
                                                        }
                                                }
                                        ) { Text("▲", fontSize = 24.sp, color = AccentBlue) }
                                        Text(
                                                text =
                                                        if (use24HourFormat) {
                                                                hour24.toString().padStart(2, '0')
                                                        } else {
                                                                displayHour.toString()
                                                        },
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                        TextButton(
                                                onClick = {
                                                        if (use24HourFormat) {
                                                                hour24 =
                                                                        if (hour24 > 0) hour24 - 1
                                                                        else 23
                                                        } else {
                                                                // In 12-hour mode, cycle 12-1
                                                                val newDisplayHour =
                                                                        if (displayHour == 1) 12
                                                                        else displayHour - 1
                                                                updateHour24(newDisplayHour, isAm)
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

                                // Minute picker
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

                                // AM/PM selector (only in 12-hour mode)
                                if (!use24HourFormat) {
                                        Spacer(modifier = Modifier.padding(start = 16.dp))

                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                        ) {
                                                // AM button
                                                TextButton(
                                                        onClick = {
                                                                updateHour24(displayHour, true)
                                                        },
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                if (isAm)
                                                                                        AccentBlue
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent
                                                                        )
                                                ) {
                                                        Text(
                                                                text = "AM",
                                                                fontSize = 20.sp,
                                                                fontWeight =
                                                                        if (isAm) FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                color =
                                                                        if (isAm) AccentBlue
                                                                        else
                                                                                Color.White.copy(
                                                                                        alpha = 0.5f
                                                                                )
                                                        )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // PM button
                                                TextButton(
                                                        onClick = {
                                                                updateHour24(displayHour, false)
                                                        },
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                if (!isAm)
                                                                                        AccentBlue
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent
                                                                        )
                                                ) {
                                                        Text(
                                                                text = "PM",
                                                                fontSize = 20.sp,
                                                                fontWeight =
                                                                        if (!isAm) FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                color =
                                                                        if (!isAm) AccentBlue
                                                                        else
                                                                                Color.White.copy(
                                                                                        alpha = 0.5f
                                                                                )
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
private fun BrightnessSettingItem(
        title: String,
        description: String,
        currentValue: Int?,
        onValueChange: (Int?) -> Unit
) {
        var sliderValue by remember(currentValue) { mutableIntStateOf(currentValue ?: 100) }

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column {
                                Text(
                                        text = title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = description,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                )
                        }

                        if (currentValue != null) {
                                TextButton(onClick = { onValueChange(null) }) {
                                        Text(
                                                text = "Clear",
                                                fontSize = 14.sp,
                                                color = Color(0xFFEF5350)
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (currentValue != null) {
                        Text(
                                text = "$currentValue%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                        )
                } else {
                        Text(
                                text = "Using calendar config or default (100%)",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.5f)
                        )
                }

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
private fun TimeFormatSettingItem(
        title: String,
        description: String,
        use24Hour: Boolean?,
        onFormatChange: (Boolean?) -> Unit
) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = description,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                )
                        }

                        if (use24Hour != null) {
                                TextButton(onClick = { onFormatChange(null) }) {
                                        Text(
                                                text = "Clear",
                                                fontSize = 14.sp,
                                                color = Color(0xFFEF5350)
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text =
                                        when (use24Hour) {
                                                true -> "24-hour (14:30)"
                                                false -> "12-hour (2:30 PM)"
                                                null -> "Using calendar config or default (12-hour)"
                                        },
                                fontSize = 16.sp,
                                color =
                                        if (use24Hour != null) AccentBlue
                                        else Color.White.copy(alpha = 0.5f)
                        )

                        Switch(
                                checked = use24Hour ?: false,
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
private fun ShowYearSettingItem(
        title: String,
        description: String,
        showYear: Boolean?,
        onShowYearChange: (Boolean?) -> Unit
) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(16.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = description,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                )
                        }

                        if (showYear != null) {
                                TextButton(onClick = { onShowYearChange(null) }) {
                                        Text(
                                                text = "Clear",
                                                fontSize = 14.sp,
                                                color = Color(0xFFEF5350)
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text =
                                        when (showYear) {
                                                true -> "February 11, 2026"
                                                false -> "February 11"
                                                null -> "Using default (show year)"
                                        },
                                fontSize = 16.sp,
                                color =
                                        if (showYear != null) AccentBlue
                                        else Color.White.copy(alpha = 0.5f)
                        )

                        Switch(
                                checked = showYear ?: true,
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

// region Previews

@Preview(
        name = "Manual Config - With Values",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun ManualConfigWithValuesPreview() {
        MemoryLinkTheme {
                ManualConfigScreen(
                        configState =
                                ConfigState(
                                        wakeTime = LocalTime.of(7, 0),
                                        sleepTime = LocalTime.of(21, 30),
                                        brightness = 80,
                                        use24HourFormat = true
                                ),
                        onWakeTimeChange = {},
                        onSleepTimeChange = {},
                        onBrightnessChange = {},
                        onTimeFormatChange = {},
                        onBackClick = {}
                )
        }
}

@Preview(
        name = "Manual Config - Solar Time",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun ManualConfigSolarTimePreview() {
        MemoryLinkTheme {
                ManualConfigScreen(
                        configState =
                                ConfigState(
                                        wakeSolarRef = "SUNRISE",
                                        wakeSolarOffset = 30,
                                        sleepSolarRef = "SUNSET",
                                        sleepSolarOffset = -15,
                                        brightness = 90,
                                        use24HourFormat = false
                                ),
                        onWakeTimeChange = {},
                        onSleepTimeChange = {},
                        onBrightnessChange = {},
                        onTimeFormatChange = {},
                        onBackClick = {}
                )
        }
}

@Preview(
        name = "Manual Config - Defaults",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun ManualConfigDefaultsPreview() {
        MemoryLinkTheme {
                ManualConfigScreen(
                        configState = ConfigState(),
                        onWakeTimeChange = {},
                        onSleepTimeChange = {},
                        onBrightnessChange = {},
                        onTimeFormatChange = {},
                        onBackClick = {}
                )
        }
}

@Preview(
        name = "Manual Config - 12 Hour Mode",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun ManualConfig12HourPreview() {
        MemoryLinkTheme {
                ManualConfigScreen(
                        configState =
                                ConfigState(
                                        wakeTime = LocalTime.of(7, 30),
                                        sleepTime = LocalTime.of(21, 0),
                                        brightness = 90,
                                        use24HourFormat = false
                                ),
                        onWakeTimeChange = {},
                        onSleepTimeChange = {},
                        onBrightnessChange = {},
                        onTimeFormatChange = {},
                        onBackClick = {}
                )
        }
}

// endregion
