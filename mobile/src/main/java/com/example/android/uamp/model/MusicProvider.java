/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.model;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static com.example.android.uamp.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 * 音乐曲目的简单数据提供者。
 * 实际的元数据源被委托给由此类的构造函数参数定义的音乐提供程序源。
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    /**接口,实现类为RemoteJSONSource,拉取远程json音乐资源并解析生成,元数据音乐列表迭代器*/
    private MusicProviderSource mSource;

    /**Categorized caches for music track data:音乐曲目数据的分类缓存,键--字符串,值--MediaMetadataCompat得list集合*/
    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByGenre;
    /**键--字符串,值--音乐元数据的id封装*/
    private final ConcurrentMap<String, MutableMediaMetadata> mMusicListById;

    /**最喜欢的曲目集合*/
    private final Set<String> mFavoriteTracks;

    /**未初始化,初始化中,初始化结束*/
    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    /**当前初始化状态*/
    private volatile State mCurrentState = State.NON_INITIALIZED;

    /**音乐目录是够已准备好的回调*/
    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }

    public MusicProvider() {
        this(new RemoteJSONSource());
    }
    public MusicProvider(MusicProviderSource source) {
        mSource = source;//远程资源
        mMusicListByGenre = new ConcurrentHashMap<>();//通过流派分类的云月列表
        mMusicListById = new ConcurrentHashMap<>();//通过id分类的音乐列表
        mFavoriteTracks = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * Get an iterator over the list of genres
     *获取流派列表上的迭代器
     * @return genres 流派
     */
    public Iterable<String> getGenres() {
        if (mCurrentState != State.INITIALIZED) {//未初始化结束返回空集合
            return Collections.emptyList();
        }
        return mMusicListByGenre.keySet();//按流派分类的音乐map的键的集合
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     * 获取所有打乱的歌曲集合的迭代器
     */
    public Iterable<MediaMetadataCompat> getShuffledMusic() {
        if (mCurrentState != State.INITIALIZED) {//初始化状态
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(mMusicListById.size());
        //取出mMusicListById中所有的值存储到集合shuffled中
        for (MutableMediaMetadata mutableMetadata: mMusicListById.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        //将数据随机打乱
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /**
     * Get music tracks of the given genre
     *获取给定流派的音乐曲目
     */
    public List<MediaMetadataCompat> getMusicsByGenre(String genre) {
        //初始化未完成或者不包含给定的键
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre.containsKey(genre)) {
            return Collections.emptyList();
        }
        //获取给定音乐流派的音乐列表
        return mMusicListByGenre.get(genre);
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *搜索的非常基本的实现，用包含给定查询的标题过滤音乐曲目
     * 根据音乐标题查找音乐曲目集合
     */
    public List<MediaMetadataCompat> searchMusicBySongTitle(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     *非常基本的搜索过滤器，用包含给定查询的专辑过滤音乐
     */
    public List<MediaMetadataCompat> searchMusicByAlbum(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     *非常基本的搜索过滤器，用包含给定查询的艺术家过滤音乐
     */
    public List<MediaMetadataCompat> searchMusicByArtist(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with a genre containing
     * the given query.
     *搜索的非常基本的实现，用包含给定查询的类型过滤音乐
     */
    public List<MediaMetadataCompat> searchMusicByGenre(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_GENRE, query);
    }

    /**根据类型键值查询曲目,返回符合条件的MediaMetadataCompat的list集合*/
    private List<MediaMetadataCompat> searchMusic(String metadataField, String query) {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
        query = query.toLowerCase(Locale.US);//英文全部转化为小写
        //根据音乐id封装列表查询符合条件的音乐
        for (MutableMediaMetadata track : mMusicListById.values()) {
            if (track.metadata.getString(metadataField).toLowerCase(Locale.US)
                .contains(query)) {
                result.add(track.metadata);
            }
        }
        return result;
    }


    /**
     * Return the MediaMetadataCompat for the given musicID.
     *返回给定音乐ID的MediaMetadataCompat
     * @param musicId The unique, non-hierarchical music ID.
     */
    public MediaMetadataCompat getMusic(String musicId) {
        return mMusicListById.containsKey(musicId) ? mMusicListById.get(musicId).metadata : null;
    }

    /**跟新音乐艺术,更新mMusicListById列表中指定id的metadata数据*/
    public synchronized void updateMusicArt(String musicId, Bitmap albumArt, Bitmap icon) {
        MediaMetadataCompat metadata = getMusic(musicId);//获取符合条件的MediaMetadataCompat
        metadata = new MediaMetadataCompat.Builder(metadata)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
        //在METADATA KEY ALBUM ART中设置高分辨率位图。 例如，当媒体会话处于活动状态时，它用于锁屏背景。
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary/在DISPLAY ICON中设置小版本的专辑封面。 这用于媒体描述
                // ，因此如果需要，它应该很小，以便序列化
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build();

        MutableMediaMetadata mutableMetadata = mMusicListById.get(musicId);
        if (mutableMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }

        mutableMetadata.metadata = metadata;
    }

    /**设置喜欢的音乐,true添加,false移除*/
    public void setFavorite(String musicId, boolean favorite) {
        if (favorite) {
            mFavoriteTracks.add(musicId);
        } else {
            mFavoriteTracks.remove(musicId);
        }
    }

    /**初始化结束*/
    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    /**给定音乐id是否包含在喜欢的音乐的set集合中*/
    public boolean isFavorite(String musicId) {
        return mFavoriteTracks.contains(musicId);
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     * 从服务器获取音乐曲目列表并缓存曲目信息以供将来参考，按音乐ID键入曲目并按流派分组。
     */
    @SuppressLint("StaticFieldLeak")
    public void retrieveMediaAsync(final Callback callback) {
        LogHelper.d(TAG, "retrieveMediaAsync called");
        if (mCurrentState == State.INITIALIZED) {//初始化已完成
            if (callback != null) {
                // Nothing to do, execute callback immediately
                //立即执行回调,音乐曲目已准备好
                callback.onMusicCatalogReady(true);
            }
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        //在单独的线程中异步加载音乐目录
        new AsyncTask<Void, Void, State>() {
            @Override
            protected State doInBackground(Void... params) {
                retrieveMedia();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onMusicCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

    /**根据流派构建列表*/
    private synchronized void buildListsByGenre() {
        //新的流派列表map
        ConcurrentMap<String, List<MediaMetadataCompat>> newMusicListByGenre = new ConcurrentHashMap<>();

        //迭代音乐元数据id列表
        for (MutableMediaMetadata m : mMusicListById.values()) {
            //获取流派
            String genre = m.metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
            //获取流派列表
            List<MediaMetadataCompat> list = newMusicListByGenre.get(genre);
            //如果流派列表不存在则新建并存储
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByGenre.put(genre, list);
            }
            //曲目添加到list中
            list.add(m.metadata);
        }
        //更新mMusicListByGenre
        mMusicListByGenre = newMusicListByGenre;
    }

    /**检索音乐资源*/
    private synchronized void retrieveMedia() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {//未初始化
                mCurrentState = State.INITIALIZING;//初始化中

                Iterator<MediaMetadataCompat> tracks = mSource.iterator();//获取音乐资源迭代器
                while (tracks.hasNext()) {//遍历音乐资源
                    MediaMetadataCompat item = tracks.next();
                    //musicId来自hash码
                    String musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                    //构建id元数据
                    mMusicListById.put(musicId, new MutableMediaMetadata(musicId, item));
                }
                //根据流派构建列表
                buildListsByGenre();
                //初始化结束
                mCurrentState = State.INITIALIZED;
            }
        } finally {
            if (mCurrentState != State.INITIALIZED) {//未初始化完成异常,则标记为未初始化
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                //发生了一些不好的事情，所以我们将状态重置为NON INITIALIZED以允许重试（
                // 例如，如果网络连接暂时不可用）
                mCurrentState = State.NON_INITIALIZED;
            }
        }
    }


    /**根据mediaId获取媒体资源集合*/
    public List<MediaBrowserCompat.MediaItem> getChildren(String mediaId, Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        //mediaId中|的位置大于0,包含|,直接返回空集合
        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems;
        }

        //"__ROOT__"等于mediaId
        if (MEDIA_ID_ROOT.equals(mediaId)) {//为Root创建可浏览媒体项
            mediaItems.add(createBrowsableMediaItemForRoot(resources));
        } else if (MEDIA_ID_MUSICS_BY_GENRE.equals(mediaId)) {//"__BY_GENRE__"
            for (String genre : getGenres()) {
                mediaItems.add(createBrowsableMediaItemForGenre(genre, resources));
            }

        } else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {//"__BY_GENRE__"开头
            String genre = MediaIDHelper.getHierarchy(mediaId)[1];
            for (MediaMetadataCompat metadata : getMusicsByGenre(genre)) {
                mediaItems.add(createMediaItem(metadata));
            }

        } else {//跳过不匹配的媒体ID
            mediaItems.add(createNowPlaying(resources));
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        return mediaItems;
    }

    /**为Root创建可浏览媒体项 */
    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForRoot(Resources resources) {
        //构建媒体描述
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_BY_GENRE)//根据流派
                .setTitle(resources.getString(R.string.browse_genres))//标题,流派
                .setSubtitle(resources.getString(R.string.browse_genre_subtitle))//子标题,歌曲来自流派
                .setIconUri(Uri.parse("android.resource://" +
                        "com.example.android.uamp/drawable/ic_by_genre"))//头像图片
                .build();
        //标志：表示该项目具有自己的子项
        //创建新的媒体项以用于浏览媒体
        //静态内部类  MediaBrowserCompat.MediaItem 包含用于浏览/搜索媒体的单个媒体项的信息的类。
        // 媒体项目取决于应用程序，因此我们无法保证它们包含正确的值
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);//有子项的
    }

    /**为Root创建可浏览媒体项 */
    private MediaBrowserCompat.MediaItem createNowPlaying(Resources resources) {
        //构建媒体描述
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_BY_GENRE)//根据流派
                .setTitle("忘情水")//标题,流派
                .setSubtitle("刘德华的歌曲")//子标题,歌曲来自流派
                .setIconUri(Uri.parse("android.resource://" +
                        "com.example.android.uamp/drawable/ic_by_genre"))//头像图片
                .build();
        //标志：表示该项目具有自己的子项
        //创建新的媒体项以用于浏览媒体
        //静态内部类  MediaBrowserCompat.MediaItem 包含用于浏览/搜索媒体的单个媒体项的信息的类。
        // 媒体项目取决于应用程序，因此我们无法保证它们包含正确的值
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);//有子项的
    }

    /**为流派创建可浏览媒体项目 */
    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForGenre(String genre,
                                                                    Resources resources) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, MEDIA_ID_MUSICS_BY_GENRE, genre))
                .setTitle(genre)//标题--具体流派
                .setSubtitle(resources.getString(
                        R.string.browse_musics_by_genre_subtitle, genre))//来自哪个流派
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);//有子项的
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        //由于媒体元数据字段是不可变的，我们需要创建一个副本，因此我们可以设置一个层次感知媒体ID。
        // 当我们获得Play From Music ID调用时，我们需要知道媒体层次结构，
        // 因此我们可以根据音乐的选择位置创建正确的队列（按艺术家，按流派，随机等）
        String genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
        //层次结构id
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_GENRE, genre);
        MediaMetadataCompat copy = new MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();
        return new MediaBrowserCompat.MediaItem(copy.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);//可播放的

    }

}
