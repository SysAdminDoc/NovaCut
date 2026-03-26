package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.ui.theme.Mocha

@Composable
fun NoiseReductionPanel(
    isAnalyzing: Boolean,
    analysisResult: String? = null,
    onAnalyze: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Noise Reduction", color = Mocha.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Analyzes audio to detect noise type (hiss, hum, broadband) and automatically applies the best DSP filters.",
            color = Mocha.Subtext0,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAnalyze,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve, contentColor = Mocha.Base)
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Mocha.Base, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing noise profile...")
            } else {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze & Fix Noise")
            }
        }

        // Show analysis result when available
        if (analysisResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, "Done", tint = Mocha.Green, modifier = Modifier.size(16.dp))
                Text(analysisResult, color = Mocha.Text, fontSize = 12.sp)
            }
        }
    }
}
