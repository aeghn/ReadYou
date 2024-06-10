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
import androidx.compose.material.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.PullToSwitchArticlePreference
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.home.HomeViewModel
import kotlin.math.abs


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

    val context = LocalContext.current

    val readingState = readingViewModel.readingState.collectAsStateValue()

    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()
    val tonalElevation = LocalReadingPageTonalElevation.current

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    val swipeMethod = LocalPullToSwitchArticle.current
    val useVerticalSwipe = swipeMethod.equals(PullToSwitchArticlePreference.VerticalSwitch)

    var currentImageData by remember { mutableStateOf(ImageData()) }

    val isShowToolBar = if (LocalReadingAutoHideToolbar.current.value) {
        readingState.articleId != null && !isReaderScrollingDown
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

    LaunchedEffect(readingState.articleId) {
        if (readingState.articleId != null && readingState.isUnread) {
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
                    title = readingState.title ?: "",
                    link = readingState.link ?: "",
                    onClose = {
                        navController.popBackStack()
                    },
                )

                ReadingViewer(
                    readerState = readingState,
                    readingViewModel = readingViewModel,
                    pagingItems = pagingItems,
                ) { articleWithFeed ->
                    val article = articleWithFeed.article;

                    remember { article }.run {
                        val content: ContentState = if (article.id != readingState.articleId) {
                            ContentState.Description(
                                content = article.fullContent
                                    ?: article.rawDescription
                            )
                        } else {
                            readingState.content
                        }
                        
                        val pullToSwitchState =
                            rememberPullToLoadState(
                                key = content,
                                onLoadNext = {
                                    readingViewModel.setArticleIdOffset(1)
                                },
                                onLoadPrevious = {
                                    readingViewModel.setArticleIdOffset(-1)
                                }
                            )


                        val listState = rememberSaveable(
                            inputs = arrayOf(content),
                            saver = LazyListState.Saver
                        ) { LazyListState() }


                        CompositionLocalProvider(
                            LocalOverscrollConfiguration provides
                                    if (useVerticalSwipe) null else LocalOverscrollConfiguration.current,
                            LocalTextStyle provides LocalTextStyle.current.run {
                                merge(lineHeight = if (lineHeight.isSpecified) (lineHeight.value * LocalReadingTextLineHeight.current).sp else TextUnit.Unspecified)
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pullToLoad(
                                        state = pullToSwitchState,
                                        onScroll = { f ->
                                            if (abs(f) > 2f)
                                                isReaderScrollingDown = f < 0f
                                        },
                                        enabled = useVerticalSwipe
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Content(
                                    modifier = Modifier.padding(paddings),
                                    content = content.text ?: "",
                                    feedName = articleWithFeed.feed.name ?: "",
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
                                if (swipeMethod.equals(PullToSwitchArticlePreference.VerticalSwitch)) {
                                    PullToLoadIndicator(
                                        state = pullToSwitchState,
                                        canLoadPrevious = readingViewModel.getArticleIdOffset(-1) != null,
                                        canLoadNext = readingViewModel.getArticleIdOffset(1) != null
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Bar
                BottomBar(
                    isShow = isShowToolBar,
                    isUnread = readingState.isUnread,
                    isStarred = readingState.isStarred,
                    isNextArticleAvailable = false,
                    isFullContent = readingState.content is ContentState.FullContent,
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
                    pagerTotalSize = readingState.pagerTotal,
                    pagerCursor = readingState.pagerCursor
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
