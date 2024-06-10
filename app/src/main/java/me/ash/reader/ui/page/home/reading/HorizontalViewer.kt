package me.ash.reader.ui.page.home.reading


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalViewer(
    initCursor: Int,
    totalCount: Int,
    key: ((index: Int) -> Any)? = null,
    currentPage: ((index: Int) -> Unit) = {},
    content: @Composable (Int) -> Unit = {}
) {
    if (totalCount > 0) {
        val pagerState = rememberPagerState(initialPage = initCursor) {
            totalCount
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage >= 0) {
                currentPage(pagerState.currentPage)
            }

        }

        HorizontalPager(state = pagerState, key = key) {
            content(it)
        }
    } else {

    }
}