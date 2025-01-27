package it.fast4x.rimusic.ui.screens.artist

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.valentinilk.shimmer.shimmer
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.compose.persist.persist
import it.fast4x.compose.routing.RouteHandler
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.itemsPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.SearchQuery
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.query
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.Header
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.HeaderPlaceholder
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.Scaffold
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.ui.components.themed.SmartToast
import it.fast4x.rimusic.ui.components.themed.adaptiveThumbnailContent
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import it.fast4x.rimusic.ui.items.SongItem
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.screens.albumRoute
import it.fast4x.rimusic.ui.screens.globalRoutes
import it.fast4x.rimusic.ui.screens.homeRoute
import it.fast4x.rimusic.ui.screens.search.SearchScreen
import it.fast4x.rimusic.ui.screens.searchResultRoute
import it.fast4x.rimusic.ui.screens.searchRoute
import it.fast4x.rimusic.ui.screens.searchresult.ItemsPage
import it.fast4x.rimusic.ui.screens.searchresult.SearchResultScreen
import it.fast4x.rimusic.ui.screens.settings.SettingsScreen
import it.fast4x.rimusic.ui.screens.settingsRoute
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.UiTypeKey
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.artistScreenTabIndexKey
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.downloadedStateMedia
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.pauseSearchHistoryKey
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun ArtistScreen(
    navController: NavController,
    browseId: String,
    playerEssential: @Composable () -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    //var tabIndex by rememberPreference(artistScreenTabIndexKey, defaultValue = 0)

    val binder = LocalPlayerServiceBinder.current

    var tabIndex by remember {
        mutableStateOf(0)
    }

    PersistMapCleanup(tagPrefix = "artist/$browseId/")

    var artist by persist<Artist?>("artist/$browseId/artist")

    var artistPage by persist<Innertube.ArtistPage?>("artist/$browseId/artistPage")

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
    val context = LocalContext.current

    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )
    var changeShape by remember {
        mutableStateOf(false)
    }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        Database
            .artist(browseId)
            .combine(snapshotFlow { tabIndex }.map { it != 4 }) { artist, mustFetch -> artist to mustFetch }
            .distinctUntilChanged()
            .collect { (currentArtist, mustFetch) ->
                artist = currentArtist

                if (artistPage == null && (currentArtist?.timestamp == null || mustFetch)) {
                    withContext(Dispatchers.IO) {
                        Innertube.artistPage(BrowseBody(browseId = browseId))
                            ?.onSuccess { currentArtistPage ->
                                artistPage = currentArtistPage

                                Database.upsert(
                                    Artist(
                                        id = browseId,
                                        name = currentArtistPage.name,
                                        thumbnailUrl = currentArtistPage.thumbnail?.url,
                                        timestamp = System.currentTimeMillis(),
                                        bookmarkedAt = currentArtist?.bookmarkedAt
                                    )
                                )
                            }
                    }
                }
            }
    }

    val listMediaItems = remember { mutableListOf<MediaItem>() }

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()
/*
        settingsRoute {
            SettingsScreen(
                navController = navController,
            )
        }

        searchResultRoute { query ->
            SearchResultScreen(
                navController = navController,
                query = query,
                onSearchAgain = {
                    searchRoute(query)
                }
            )
        }

        searchRoute { initialTextInput ->
            SearchScreen(
                navController = navController,
                initialTextInput = initialTextInput,
                onSearch = { query ->
                    pop()
                    searchResultRoute(query)

                    if (!context.preferences.getBoolean(pauseSearchHistoryKey, false)) {
                        query {
                            Database.insert(SearchQuery(query = query))
                        }
                    }
                },
                onViewPlaylist = {}, //onPlaylistUrl,
                onDismiss = { homeRoute::global }
            )
        }
*/
        host {
            val thumbnailContent =
                adaptiveThumbnailContent(
                    artist?.timestamp == null,
                    artist?.thumbnailUrl,
                    //CircleShape
                    onClick = { changeShape = !changeShape },
                    shape = if (changeShape) CircleShape else thumbnailRoundness.shape(),
                )

            val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit =
                { textButton ->
                    if (artist?.timestamp == null) {
                        HeaderPlaceholder(
                            modifier = Modifier
                                .shimmer()
                        )
                    } else {
                        val (colorPalette) = LocalAppearance.current
                        val context = LocalContext.current

                        Header(title = artist?.name ?: "Unknown") {

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 50.dp)
                                    .padding(horizontal = 12.dp)
                            ) {
                                textButton?.invoke()

                                Spacer(
                                    modifier = Modifier
                                        .weight(0.2f)
                                )

                                SecondaryTextButton(
                                    text = if (artist?.bookmarkedAt == null) stringResource(R.string.follow) else stringResource(
                                        R.string.following
                                    ),
                                    onClick = {
                                        val bookmarkedAt =
                                            if (artist?.bookmarkedAt == null) System.currentTimeMillis() else null

                                        query {
                                            artist
                                                ?.copy(bookmarkedAt = bookmarkedAt)
                                                ?.let(Database::update)
                                        }
                                    },
                                    alternative = if (artist?.bookmarkedAt == null) true else false
                                )

                                /*
                            HeaderIconButton(
                                icon = if (artist?.bookmarkedAt == null) {
                                    R.drawable.bookmark_outline
                                } else {
                                    R.drawable.bookmark
                                },
                                color = colorPalette.accent,
                                onClick = {
                                    val bookmarkedAt =
                                        if (artist?.bookmarkedAt == null) System.currentTimeMillis() else null

                                    query {
                                        artist
                                            ?.copy(bookmarkedAt = bookmarkedAt)
                                            ?.let(Database::update)
                                    }
                                }
                            )
                             */

                                HeaderIconButton(
                                    icon = R.drawable.share_social,
                                    color = colorPalette.text,
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "https://music.youtube.com/channel/$browseId"
                                            )
                                        }

                                        context.startActivity(
                                            Intent.createChooser(
                                                sendIntent,
                                                null
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

            val localHeaderContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit =
                { textButton ->
                    if (artist?.timestamp == null) {
                        HeaderPlaceholder(
                            modifier = Modifier
                                .shimmer()
                        )
                    } else {
                        val (colorPalette) = LocalAppearance.current
                        val context = LocalContext.current

                        Header(title = artist?.name ?: "Unknown") {
                            textButton?.invoke()


                            listMediaItems.let { songs ->
                                HeaderIconButton(
                                    icon = R.drawable.enqueue,
                                    enabled = true,
                                    color = colorPalette.text,
                                    onClick = {},
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                binder?.player?.enqueue(songs, context)
                                            },
                                            onLongClick = {
                                                SmartToast(context.getString(R.string.info_enqueue_songs))
                                            }
                                        )
                                )
                            }

                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                            )

                            SecondaryTextButton(
                                text = if (artist?.bookmarkedAt == null) stringResource(R.string.follow) else stringResource(
                                    R.string.following
                                ),
                                onClick = {
                                    val bookmarkedAt =
                                        if (artist?.bookmarkedAt == null) System.currentTimeMillis() else null

                                    query {
                                        artist
                                            ?.copy(bookmarkedAt = bookmarkedAt)
                                            ?.let(Database::update)
                                    }
                                },
                                alternative = if (artist?.bookmarkedAt == null) true else false
                            )

                            /*
                            HeaderIconButton(
                                icon = if (artist?.bookmarkedAt == null) {
                                    R.drawable.bookmark_outline
                                } else {
                                    R.drawable.bookmark
                                },
                                color = colorPalette.accent,
                                onClick = {
                                    val bookmarkedAt =
                                        if (artist?.bookmarkedAt == null) System.currentTimeMillis() else null

                                    query {
                                        artist
                                            ?.copy(bookmarkedAt = bookmarkedAt)
                                            ?.let(Database::update)
                                    }
                                }
                            )
                             */

                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette.text,
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/channel/$browseId"
                                        )
                                    }

                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                            )
                        }
                    }
                }

            val uiType  by rememberPreference(UiTypeKey, UiType.RiMusic)

            Scaffold(
                navController = navController,
                playerEssential = playerEssential,
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                showButton1 = if(uiType == UiType.RiMusic) false else true,
                topIconButton2Id = R.drawable.chevron_back,
                onTopIconButton2Click = pop,
                showButton2 = false,
                tabIndex = tabIndex,
                onHomeClick = {
                    //homeRoute()
                    navController.navigate(NavRoutes.home.name)
                },
                onTabChanged = { tabIndex = it },
                tabColumnContent = { Item ->
                    Item(0, stringResource(R.string.overview), R.drawable.artist)
                    Item(1, stringResource(R.string.songs), R.drawable.musical_notes)
                    Item(2, stringResource(R.string.albums), R.drawable.album)
                    Item(3, stringResource(R.string.singles), R.drawable.disc)
                    Item(4, stringResource(R.string.library), R.drawable.library)
                },
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> {
                            ArtistOverviewModern(
                                navController = navController,
                                browseId = browseId,
                                youtubeArtistPage = artistPage,
                                thumbnailContent = thumbnailContent,
                                headerContent = headerContent,
                                onAlbumClick = {
                                    //albumRoute(it)
                                    navController.navigate(route = "${NavRoutes.album.name}/$it")
                                },
                                onViewAllSongsClick = { tabIndex = 1 },
                                onViewAllAlbumsClick = { tabIndex = 2 },
                                onViewAllSinglesClick = { tabIndex = 3 },
                                onSearchClick = {
                                    //searchRoute("")
                                    navController.navigate(NavRoutes.search.name)
                                },
                                onSettingsClick = {
                                    //settingsRoute()
                                    navController.navigate(NavRoutes.settings.name)
                                }
                            )
                        }

                        1 -> {
                            val binder = LocalPlayerServiceBinder.current
                            val menuState = LocalMenuState.current
                            val thumbnailSizeDp = Dimensions.thumbnails.song
                            val thumbnailSizePx = thumbnailSizeDp.px
                            //val listMediaItems = remember { mutableListOf<MediaItem>() }
                            ItemsPage(
                                tag = "artist/$browseId/songs",
                                headerContent = headerContent,
                                itemsPageProvider = artistPage?.let {
                                    ({ continuation ->
                                        continuation?.let {
                                            Innertube.itemsPage(
                                                body = ContinuationBody(continuation = continuation),
                                                fromMusicResponsiveListItemRenderer = Innertube.SongItem::from,
                                            )
                                        } ?: artistPage
                                            ?.songsEndpoint
                                            ?.takeIf { it.browseId != null }
                                            ?.let { endpoint ->
                                                Innertube.itemsPage(
                                                    body = BrowseBody(
                                                        browseId = endpoint.browseId!!,
                                                        params = endpoint.params,
                                                    ),
                                                    fromMusicResponsiveListItemRenderer = Innertube.SongItem::from,
                                                )
                                            }
                                        ?: Result.success(
                                            Innertube.ItemsPage(
                                                items = artistPage?.songs,
                                                continuation = null
                                            )
                                        )
                                    })
                                },
                                itemContent = { song ->
                                    listMediaItems.add(song.asMediaItem)
                                    downloadState = getDownloadState(song.asMediaItem.mediaId)
                                    val isDownloaded = downloadedStateMedia(song.asMediaItem.mediaId)
                                    SwipeablePlaylistItem(
                                        mediaItem = song.asMediaItem,
                                        onSwipeToRight = {
                                            binder?.player?.addNext(song.asMediaItem)
                                        }
                                    ) {
                                        SongItem(
                                            song = song,
                                            isDownloaded = isDownloaded,
                                            onDownloadClick = {
                                                binder?.cache?.removeResource(song.asMediaItem.mediaId)
                                                query {
                                                    Database.insert(
                                                        Song(
                                                            id = song.asMediaItem.mediaId,
                                                            title = song.asMediaItem.mediaMetadata.title.toString(),
                                                            artistsText = song.asMediaItem.mediaMetadata.artist.toString(),
                                                            thumbnailUrl = song.thumbnail?.url,
                                                            durationText = null
                                                        )
                                                    )
                                                }

                                                manageDownload(
                                                    context = context,
                                                    songId = song.asMediaItem.mediaId,
                                                    songTitle = song.asMediaItem.mediaMetadata.title.toString(),
                                                    downloadState = isDownloaded
                                                )
                                            },
                                            downloadState = downloadState,
                                            thumbnailSizeDp = thumbnailSizeDp,
                                            thumbnailSizePx = thumbnailSizePx,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onLongClick = {
                                                        menuState.display {
                                                            NonQueuedMediaItemMenu(
                                                                navController = navController,
                                                                onDismiss = menuState::hide,
                                                                mediaItem = song.asMediaItem,
                                                            )
                                                        };
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    },
                                                    onClick = {
                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlayAtIndex(
                                                            listMediaItems.distinct(),
                                                            listMediaItems.distinct()
                                                                .indexOf(song.asMediaItem)
                                                        )
                                                        /*
                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlay(song.asMediaItem)
                                                    binder?.setupRadio(song.info?.endpoint)
                                                     */
                                                    }
                                                )
                                        )
                                    }
                                },
                                itemPlaceholderContent = {
                                    SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                }
                            )
                        }

                        2 -> {
                            val thumbnailSizeDp = 108.dp
                            val thumbnailSizePx = thumbnailSizeDp.px

                            ItemsPage(
                                tag = "artist/$browseId/albums",
                                headerContent = headerContent,
                                emptyItemsText = stringResource(R.string.artist_no_release_album),
                                itemsPageProvider = artistPage?.let {
                                    ({ continuation ->
                                        continuation?.let {
                                            Innertube.itemsPage(
                                                body = ContinuationBody(continuation = continuation),
                                                fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                            )
                                        } ?: artistPage
                                            ?.albumsEndpoint
                                            ?.takeIf { it.browseId != null }
                                            ?.let { endpoint ->
                                                Innertube.itemsPage(
                                                    body = BrowseBody(
                                                        browseId = endpoint.browseId!!,
                                                        params = endpoint.params,
                                                    ),
                                                    fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                                )
                                            }
                                        ?: Result.success(
                                            Innertube.ItemsPage(
                                                items = artistPage?.albums,
                                                continuation = null
                                            )
                                        )
                                    })
                                },
                                itemContent = { album ->
                                    AlbumItem(
                                        album = album,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        modifier = Modifier
                                            .clickable(onClick = {
                                                //albumRoute(album.key)
                                                navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                            }),
                                        yearCentered = false
                                    )
                                },
                                itemPlaceholderContent = {
                                    AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                }
                            )
                        }

                        3 -> {
                            val thumbnailSizeDp = 108.dp
                            val thumbnailSizePx = thumbnailSizeDp.px

                            ItemsPage(
                                tag = "artist/$browseId/singles",
                                headerContent = headerContent,
                                emptyItemsText = stringResource(R.string.artist_no_release_single),
                                itemsPageProvider = artistPage?.let {
                                    ({ continuation ->
                                        continuation?.let {
                                            Innertube.itemsPage(
                                                body = ContinuationBody(continuation = continuation),
                                                fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                            )
                                        } ?: artistPage
                                            ?.singlesEndpoint
                                            ?.takeIf { it.browseId != null }
                                            ?.let { endpoint ->
                                                Innertube.itemsPage(
                                                    body = BrowseBody(
                                                        browseId = endpoint.browseId!!,
                                                        params = endpoint.params,
                                                    ),
                                                    fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                                )
                                            }
                                        ?: Result.success(
                                            Innertube.ItemsPage(
                                                items = artistPage?.singles,
                                                continuation = null
                                            )
                                        )
                                    })
                                },
                                itemContent = { album ->
                                    AlbumItem(
                                        album = album,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        modifier = Modifier
                                            .clickable(onClick = {
                                                //albumRoute(album.key)
                                                navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                            }),
                                        yearCentered = false
                                    )
                                },
                                itemPlaceholderContent = {
                                    AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                }
                            )
                        }

                        4 -> {
                            ArtistLocalSongs(
                                navController = navController,
                                browseId = browseId,
                                headerContent = headerContent,
                                thumbnailContent = thumbnailContent,
                                onSearchClick = {
                                    //searchRoute("")
                                    navController.navigate(NavRoutes.search.name)
                                },
                                onSettingsClick = {
                                    //settingsRoute()
                                    navController.navigate(NavRoutes.settings.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
