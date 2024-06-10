package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.ItemSnapshotList
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.PullToSwitchArticlePreference
import me.ash.reader.ui.motion.materialSharedAxisY


private const val UPWARD = 1
private const val DOWNWARD = -1

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalViewer(
    initCursor: Int,
    totalCount: Int,
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

        HorizontalPager(state = pagerState) {
            content(it)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun CommonViewer(
    readerState: ReadingState,
    readingViewModel: ReadingViewModel,
    useVerticalSwipe: Boolean,
    pagingItems: ItemSnapshotList<ArticleFlowItem>,
    content: @Composable (ArticleWithFeed) -> Unit = {}
) {


    var articleWithFeed: ArticleWithFeed? by remember { mutableStateOf(null) }

    LaunchedEffect(readerState.articleId) {
        if (readerState.articleId != null) {
            articleWithFeed = readingViewModel.getArticle(readerState.articleId)
        }
    }

    LaunchedEffect(pagingItems.isNotEmpty(), readerState.articleId) {
        if (readerState.articleId != null && pagingItems.isNotEmpty()) {
            readingViewModel.insertArticleRelations(
                pagingItems,
                articleId = readerState.articleId,
                cursor = readerState.pagerCursor
            )
        }
    }

    if (articleWithFeed != null) {
        // Content
        AnimatedContent(
            targetState = readerState,
            contentKey = { it.articleId + it.content.text },
            transitionSpec = {
                val direction = when {
                    readingViewModel.getArticleIdOffset(1) == targetState.articleId -> UPWARD
                    readingViewModel.getArticleIdOffset(-1) == targetState.articleId -> DOWNWARD
                    initialState.articleId == targetState.articleId -> {
                        when (targetState.content) {
                            is ContentState.Description -> DOWNWARD
                            else -> UPWARD
                        }
                    }

                    else -> UPWARD
                }
                materialSharedAxisY(
                    initialOffsetY = { (it * 0.1f * direction).toInt() },
                    targetOffsetY = { (it * -0.1f * direction).toInt() },
                    durationMillis = 400
                )
            }, label = ""
        ) {
            remember { it }.run {
                content(articleWithFeed!!)
            }
        }

    }
}

@Composable
fun ReadingViewer(
    readerState: ReadingState,
    readingViewModel: ReadingViewModel,
    pagingItems: ItemSnapshotList<ArticleFlowItem>,
    content: @Composable (ArticleWithFeed) -> Unit = {}
) {
    val swipeMethod = LocalPullToSwitchArticle.current

    if (!swipeMethod.equals(PullToSwitchArticlePreference.HorizontalSwitch)) {
        CommonViewer(
            readerState,
            readingViewModel,
            swipeMethod.equals(PullToSwitchArticlePreference.VerticalSwitch),
            pagingItems
        ) {
            content(it)
        }
    } else {
        HorizontalViewer(
            initCursor = readerState.pagerInitCursor,
            totalCount = readerState.pagerTotal,
            currentPage = {
                val articleId = readingViewModel.getArticleId(it);
                if (articleId != null) {
                    readingViewModel.updateArticleIdAndCursor(articleId, it)
                }
            }
        ) {
            val articleId = readingViewModel.getArticleId(it);

            val articleWithFeed by produceState<ArticleWithFeed?>(null) {
                // readingViewModel.setLoading()
                value = readingViewModel.getArticle(articleId)
            }

            LaunchedEffect(pagingItems.isNotEmpty(), articleId) {
                if (articleId != null && pagingItems.isNotEmpty()) {
                    readingViewModel.insertArticleRelations(
                        pagingItems,
                        articleId = articleId,
                        cursor = it
                    )
                }
            }

            if (articleWithFeed != null) {
                content(articleWithFeed!!)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}