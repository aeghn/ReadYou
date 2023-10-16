package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.ui.page.home.HomeViewModel

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
fun LazyListScope.ArticleList(
    homeViewModel: HomeViewModel,
    pagingItems: LazyPagingItems<ArticleFlowItem>,
    isFilterUnread: Boolean,
    isShowFeedIcon: Boolean,
    isShowStickyHeader: Boolean,
    articleListTonalElevation: Int,
    onClick: (ArticleWithFeed) -> Unit = {},
    onSwipeOut: (ArticleWithFeed) -> Unit = {},
) {
    var sn =0;
    for (index in 0 until pagingItems.itemCount) {
        when (val item = pagingItems.peek(index)) {
            is ArticleFlowItem.Article -> {
                val id = item.articleWithFeed.article.id
                if (id == homeViewModel.readingState.value.readingId) {
                    homeViewModel.changeReadingSn(sn)
                }
                sn++
                item(key = id) {
                    swipeToDismiss(
                        articleWithFeed = (pagingItems[index] as ArticleFlowItem.Article).articleWithFeed,
                        isFilterUnread = isFilterUnread,
                        onClick = { onClick(it) },
                        onSwipeOut = { onSwipeOut(it) }
                    )
                }
            }

            is ArticleFlowItem.Date -> {
                if (item.showSpacer) {
                    sn++
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
                sn++
                if (isShowStickyHeader) {
                    stickyHeader(key = item.date) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                    }
                } else {
                    item(key = item.date) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                    }
                }
            }

            else -> {}
        }
    }
}
