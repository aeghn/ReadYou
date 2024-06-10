package me.ash.reader.ui.page.home.reading

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.home.HomeViewModel


private const val UPWARD = 1
private const val DOWNWARD = -1

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalMaterialApi::class
)
@Composable
fun ReadingPage(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    readingViewModel: ReadingViewModel = hiltViewModel(),
) {
    val tonalElevation = LocalReadingPageTonalElevation.current
    val context = LocalContext.current
    val isPullToSwitchArticleEnabled = LocalPullToSwitchArticle.current.value
    val readingUiState = readingViewModel.readingUiState.collectAsStateValue()

    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    var currentImageData by remember { mutableStateOf(ImageData()) }

    val isShowToolBar = if (LocalReadingAutoHideToolbar.current.value) {
        readingUiState.articleId != null && !isReaderScrollingDown
    } else {
        true
    }

    val pagingItems = homeUiState.pagingData.collectAsLazyPagingItems().itemSnapshotList

    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect {
            val articleId = it.arguments?.getString("articleId")
            val cursor = it.arguments?.getInt("cursor") ?: 0
            val total = it.arguments?.getInt("total") ?: 0

            if (articleId != null) {
                readingViewModel.initReadingState(articleId, total, cursor)
                readingViewModel.insertArticleRelations(
                    pagingItems,
                    articleId = articleId,
                    cursor = cursor
                )
            }
        }
    }

    LaunchedEffect(readingUiState.articleId) {
        if (readingUiState.articleId != null && readingUiState.isUnread) {
            readingViewModel.markAsRead()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
//        topBarTonalElevation = tonalElevation.value.dp,
//        containerTonalElevation = tonalElevation.value.dp,
        content = { paddings ->
            Log.i("RLog", "TopBar: recomposition")

            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopBar(
                    navController = navController,
                    isShow = isShowToolBar,
                    windowInsets = WindowInsets(top = paddings.calculateTopPadding()),
                    title = readingUiState.title ?: "",
                    link = readingUiState.link ?: "",
                    onClose = {
                        navController.popBackStack()
                    },
                )

                HorizontalViewer(
                    initCursor = readingUiState.pagerInitCursor,
                    totalCount = readingUiState.pagerTotal,
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
                        val article = articleWithFeed!!.article;

                        remember { article }.run {
                            val content: ContentState = ContentState.Description(
                                content = article.fullContent
                                    ?: article.rawDescription ?: ""
                            )

                            val listState = rememberSaveable(
                                inputs = arrayOf(content),
                                saver = LazyListState.Saver
                            ) { LazyListState() }


                            CompositionLocalProvider(
                                LocalOverscrollConfiguration provides
                                        if (isPullToSwitchArticleEnabled) null else LocalOverscrollConfiguration.current,
                                LocalTextStyle provides LocalTextStyle.current.run {
                                    merge(lineHeight = if (lineHeight.isSpecified) (lineHeight.value * LocalReadingTextLineHeight.current).sp else TextUnit.Unspecified)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Content(
                                        modifier = Modifier
                                            .padding(paddings),
                                        content = content.text ?: "",
                                        feedName = articleWithFeed?.feed?.name ?: "",
                                        title = title,
                                        author = author,
                                        link = link,
                                        publishedDate = article.date,
                                        isLoading = content is ContentState.Loading,
                                        listState = listState,
                                        onImageClick = { imgUrl, altText ->
                                            currentImageData = ImageData(imgUrl, altText)
                                            showFullScreenImageViewer = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                // Bottom Bar
                BottomBar(
                    isShow = isShowToolBar,
                    isUnread = readingUiState.isUnread,
                    isStarred = readingUiState.isStarred,
                    isNextArticleAvailable = false,
                    isFullContent = readingUiState.content is ContentState.FullContent,
                    onUnread = {
                        readingViewModel.updateReadStatus(it)
                    },
                    onStarred = {
                        readingViewModel.updateStarredStatus(it)
                    },
                    onNextArticle = {
                    },
                    onFullContent = {
                        if (it) readingViewModel.renderFullContent()
                        else readingViewModel.renderDescriptionContent()
                    },
                    pagerTotalSize = readingUiState.pagerTotal,
                    pagerCursor = readingUiState.pagerCursor
                )
            }
        }
    )
    if (showFullScreenImageViewer) {
        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = {
                readingViewModel.downloadImage(
                    it,
                    onSuccess = { context.showToast(context.getString(R.string.image_saved)) },
                    onFailure = {
                        // FIXME: crash the app for error report
                            th ->
                        throw th
                    }
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false }
        )
    }
}
