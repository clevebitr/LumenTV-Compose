package com.corner.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corner.catvodcore.bean.Vod
import com.corner.ui.scene.AutoSizeImageWithLoading
import org.jetbrains.compose.resources.painterResource
import lumentv_compose.composeapp.generated.resources.Res
import lumentv_compose.composeapp.generated.resources.no_img

@Suppress("unused")
@Composable
fun HorizontalItem(modifier: Modifier, vod:Vod, onClick:(Vod)->Unit){
    Box(modifier.padding(8.dp)
        .background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(8.dp))
        .clip(RoundedCornerShape(8.dp))
    ){
        Row(Modifier.padding(8.dp).fillMaxSize()) {
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                AutoSizeImageWithLoading(
                    url = vod.vodPic ?: "",
                    contentDescription = vod.vodName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    errorPainter = { painterResource(Res.drawable.no_img) },
                    loadingIndicator = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.Center),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                )
            }
            Spacer(Modifier.size(15.dp))
            Text(vod.vodName ?: "", modifier = Modifier
                .align(Alignment.CenterVertically)
                .fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
