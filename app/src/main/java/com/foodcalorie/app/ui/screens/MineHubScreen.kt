package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.foodcalorie.app.ui.utils.overScrollVertical

@Composable
fun MineHubScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onApi: () -> Unit,
    onTarget: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(
                text = "基本设置",
                insideMargin = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = "识别 API",
                        summary = "Base URL、API Key 与模型",
                        onClick = onApi,
                    )
                    ArrowPreference(
                        title = "每日目标",
                        summary = "热量目标 (kcal)",
                        onClick = onTarget,
                    )
                }
            }
        }
    }
}
