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

 package com.example.android.uamp;

 import android.app.PendingIntent;
 import android.content.BroadcastReceiver;
 import android.content.Context;
 import android.content.Intent;
 import android.content.IntentFilter;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.Message;
 import android.os.RemoteException;
 import android.support.annotation.NonNull;
 import android.support.v4.media.MediaBrowserCompat.MediaItem;
 import android.support.v4.media.MediaBrowserServiceCompat;
 import android.support.v4.media.MediaMetadataCompat;
 import android.support.v4.media.session.MediaButtonReceiver;
 import android.support.v4.media.session.MediaSessionCompat;
 import android.support.v4.media.session.PlaybackStateCompat;
 import android.support.v7.media.MediaRouter;
 import android.util.Log;

 import com.example.android.uamp.model.MusicProvider;
 import com.example.android.uamp.playback.CastPlayback;
 import com.example.android.uamp.playback.LocalPlayback;
 import com.example.android.uamp.playback.Playback;
 import com.example.android.uamp.playback.PlaybackManager;
 import com.example.android.uamp.playback.QueueManager;
 import com.example.android.uamp.ui.NowPlayingActivity;
 import com.example.android.uamp.utils.CarHelper;
 import com.example.android.uamp.utils.LogHelper;
 import com.example.android.uamp.utils.TvHelper;
 import com.example.android.uamp.utils.WearHelper;
 import com.google.android.gms.cast.framework.CastContext;
 import com.google.android.gms.cast.framework.CastSession;
 import com.google.android.gms.cast.framework.SessionManager;
 import com.google.android.gms.cast.framework.SessionManagerListener;
 import com.google.android.gms.common.ConnectionResult;
 import com.google.android.gms.common.GoogleApiAvailability;

 import java.lang.ref.WeakReference;
 import java.util.ArrayList;
 import java.util.List;

 import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_EMPTY_ROOT;
 import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;

 /**
  * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
  * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
  * exposes it through its MediaSession.Token, which allows the client to create a MediaController
  * that connects to and send control commands to the MediaSession remotely. This is useful for
  * user interfaces that need to interact with your media session, like Android Auto. You can
  * (should) also use the same service from your app's UI, which gives a seamless playback
  * experience to the user.
  * 此类通过服务提供媒体浏览器。通过onGetRoot和onLoadChildren方法将媒体库公开给浏览客户端。
  * 它还创建了一个MediaSession，并通过其MediaSession.Token公开它。MediaSession.Token允许客
  * 户端创建连接到远程媒体会话并向其发送控制命令的媒体控制器。 这对于需要与您的媒体会话进行交互的用户界面非常有用，
  * 例如Android Auto。 您也可以（应该）在应用程序的UI中使用相同的服务，从而为用户提供无缝的播放体验
  * To implement a MediaBrowserService, you need to:
  *要实现媒体浏览器服务，您需要：
  * <ul>
  *
  * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
  * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
  * {@link android.service.media.MediaBrowserService#onLoadChildren};
  * 扩展android.service.media.MediaBrowserService,实现媒体浏览相关方法
  * android.service.media.MediaBrowserService#onGetRoot和
  * android.service.media.MediaBrowserService#onLoadChildren
  * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
  * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
  *在onCreate方法中创建一个新的android.media.session.MediaSession,并且用session's token通知他的父容器
  * <li> Set a callback on the,设置一个回调
  * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
  * The callback will receive all the user's actions, like play, pause, etc;
  *回调将接收所有用户的操作，如播放，暂停等;
  * <li> Handle all the actual music playing using any method your app prefers (for example,
  * {@link android.media.MediaPlayer})
  *使用您的应用程序喜欢的任何方法处理所有实际播放的音乐（例如
  * <li> Update playbackState, "now playing" metadata and queue,
  * 更新播放状态，“正在播放”元数据和队列using MediaSession proper methods,使用MediaSession的适当方法
  * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
  * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
  * {@link android.media.session.MediaSession#setQueue(java.util.List)})
  *
  * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
  * android.media.browse.MediaBrowserService
  * 在AndroidManifest中声明和设置service的出口使用一个意图接收动作android.media.browse.MediaBrowserService
  * </ul>
  * <p>
  * To make your app compatible with Android Auto, you also need to:
  *要使您的应用与Android Auto兼容，您还需要
  * <ul>
  *
  * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
  * with a &lt;automotiveApp&gt; root element. For a media app, this must include
  * an &lt;uses name="media"/&gt; element as a child.
  * For example, in AndroidManifest.xml:
  * &lt;meta-data android:name="com.google.android.gms.car.application"
  * android:resource="@xml/automotive_app_desc"/&gt;
  * And in res/values/automotive_app_desc.xml:
  * &lt;automotiveApp&gt;
  * &lt;uses name="media"/&gt;
  * &lt;/automotiveApp&gt;
  *
  * </ul>
  *
  * @see <a href="README.md">README.md</a> for more details.
  */
 public class MusicService extends MediaBrowserServiceCompat implements
         PlaybackManager.PlaybackServiceCallback {

     private static final String TAG = LogHelper.makeLogTag(MusicService.class);

     // Extra on MediaSession that contains the Cast device name currently connected to
     //包含当前连接到的Cast设备名称的Media Session上的Extra
     public static final String EXTRA_CONNECTED_CAST = "com.example.android.uamp.CAST_NAME";
     // The action of the incoming Intent indicating that it contains a command
     // to be executed (see {@link #onStartCommand})
     //传入的Intent的操作，指示它包含要执行的命令
     public static final String ACTION_CMD = "com.example.android.uamp.ACTION_CMD";
     // The key in the extras of the incoming Intent indicating the command that
     // should be executed (see {@link #onStartCommand})
     //传入Intent的额外内容中的键，指示应执行的命令
     public static final String CMD_NAME = "CMD_NAME";
     // A value of a CMD_NAME key in the extras of the incoming Intent that
     // indicates that the music playback should be paused (see {@link #onStartCommand})
     //传入Intent附加内容中CMD NAME键的值，表示应暂停音乐播放
     public static final String CMD_PAUSE = "CMD_PAUSE";
     // A value of a CMD_NAME key that indicates that the music playback should switch
     // to local playback from cast playback.
     //CMD NAME键的值，表示音乐播放应从播放播放切换到本地播放
     public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";
     // Delay stopSelf by using a handler.
     //使用handler延迟停止服务的时间
     private static final int STOP_DELAY = 30000;

     //音乐资源提供者
     private MusicProvider mMusicProvider;
     /**
      * 播放管理者
      */
     private PlaybackManager mPlaybackManager;
    /**媒体会话*/
     private MediaSessionCompat mSession;
     /**媒体通知管理*/
     private MediaNotificationManager mMediaNotificationManager;
     private Bundle mSessionExtras;
     private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
     /**媒体路由*/
     private MediaRouter mMediaRouter;
     /**包名验证,验证其他App是否有权限从本服务中获取音乐数据*/
     private PackageValidator mPackageValidator;
     /**会话管理*/
     private SessionManager mCastSessionManager;
     private SessionManagerListener<CastSession> mCastSessionManagerListener;

     private boolean mIsConnectedToCar;
     /**广播接收器*/
     private BroadcastReceiver mCarConnectionReceiver;

     /*
      * (non-Javadoc)
      * @see android.app.Service#onCreate()
      */
     @Override
     public void onCreate() {
         super.onCreate();
         LogHelper.d(TAG, "onCreate");

         mMusicProvider = new MusicProvider();

         // To make the app more responsive, fetch and cache catalog information now.
         // This can help improve the response time in the method
        // 要使应用程序更具响应性，请立即获取并缓存目录信息。 这可以帮助改善方法中的响应时间
         // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
         mMusicProvider.retrieveMediaAsync(null /* Callback */);//创建时开始缓存资源

         mPackageValidator = new PackageValidator(this);

         //队列管理器,设置到播放管理者,管理播放数据的改变
         QueueManager queueManager = new QueueManager(mMusicProvider, getResources(),
                 new QueueManager.MetadataUpdateListener() {
                     @Override
                     public void onMetadataChanged(MediaMetadataCompat metadata) {
                         //设置音乐数据
                         mSession.setMetadata(metadata);

                     }

                     @Override
                     public void onMetadataRetrieveError() {
                         mPlaybackManager.updatePlaybackState(
                                 getString(R.string.error_no_metadata));
                     }

                     @Override
                     public void onCurrentQueueIndexUpdated(int queueIndex) {
                         mPlaybackManager.handlePlayRequest();
                     }

                     @Override
                     public void onQueueUpdated(String title,
                                                List<MediaSessionCompat.QueueItem> newQueue) {
                         mSession.setQueue(newQueue);
                         mSession.setQueueTitle(title);
                     }
                 });

         //本地播放器
         LocalPlayback playback = new LocalPlayback(this, mMusicProvider);

         //播放管理
         mPlaybackManager = new PlaybackManager(this, getResources(), mMusicProvider, queueManager,
                 playback);

         // Start a new MediaSession
         mSession = new MediaSessionCompat(this, "MusicService");
         setSessionToken(mSession.getSessionToken());
         mSession.setCallback(mPlaybackManager.getMediaSessionCallback());
         mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                 MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

         Context context = getApplicationContext();
         Intent intent = new Intent(context, NowPlayingActivity.class);
         PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                 intent, PendingIntent.FLAG_UPDATE_CURRENT);
         mSession.setSessionActivity(pi);

         mSessionExtras = new Bundle();
         CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
         WearHelper.setSlotReservationFlags(mSessionExtras, true, true);
         WearHelper.setUseBackgroundFromTheme(mSessionExtras, true);
         mSession.setExtras(mSessionExtras);

         mPlaybackManager.updatePlaybackState(null);

         try {
             mMediaNotificationManager = new MediaNotificationManager(this);
         } catch (RemoteException e) {
             throw new IllegalStateException("Could not create a MediaNotificationManager", e);
         }

         int playServicesAvailable =
                 GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);

         if (!TvHelper.isTvUiMode(this) && playServicesAvailable == ConnectionResult.SUCCESS) {
             mCastSessionManager = CastContext.getSharedInstance(this).getSessionManager();
             mCastSessionManagerListener = new CastSessionManagerListener();
             mCastSessionManager.addSessionManagerListener(mCastSessionManagerListener,
                     CastSession.class);
         }

         mMediaRouter = MediaRouter.getInstance(getApplicationContext());

         registerCarConnectionReceiver();
     }

     /**
      * (non-Javadoc)
      *
      * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
      */
     @Override
     public int onStartCommand(Intent startIntent, int flags, int startId) {
         Log.e("RunTest4","-------服务被启动------???"+startIntent.getStringExtra("qwer"));
         if (startIntent != null) {
             String action = startIntent.getAction();
             String command = startIntent.getStringExtra(CMD_NAME);
             if (ACTION_CMD.equals(action)) {//包含执行命令
                 if (CMD_PAUSE.equals(command)) {//暂停音乐播放
                     mPlaybackManager.handlePauseRequest();
                 } else if (CMD_STOP_CASTING.equals(command)) {//切换播放模式
                     CastContext.getSharedInstance(this).getSessionManager().endCurrentSession(true);
                 }
             } else {//不包含需要执行的命令
                 // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                 //尝试将意图作为媒体按钮接收器包装的媒体按钮事件来处理
                 MediaButtonReceiver.handleIntent(mSession, startIntent);
             }
         }
         // Reset the delay handler to enqueue a message to stop the service if
         // nothing is playing.如果没有播放任何内容，请重置延迟处理程序以使消息入队以停止服务。
         mDelayedStopHandler.removeCallbacksAndMessages(null);
         mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
         return START_STICKY;
     }

     /*
      * Handle case when user swipes the app away from the recents apps list by
      * stopping the service (and any ongoing playback).
      * 当用户通过停止服务（以及任何正在进行的播放）将应用程序从最近的应用程序列表中滑出时，处理案例。
      */
     @Override
     public void onTaskRemoved(Intent rootIntent) {
         super.onTaskRemoved(rootIntent);
         stopSelf();//停止服务
     }

     /**
      * (non-Javadoc)
      *
      * @see android.app.Service#onDestroy()
      */
     @Override
     public void onDestroy() {
         LogHelper.d(TAG, "onDestroy");
         unregisterCarConnectionReceiver();
         // Service is being killed, so make sure we release our resources
         mPlaybackManager.handleStopRequest(null);//停止播放更新状态
         mMediaNotificationManager.stopNotification();//取消播放通知

         if (mCastSessionManager != null) {//移除会话管理监听
             mCastSessionManager.removeSessionManagerListener(mCastSessionManagerListener,
                     CastSession.class);
         }

         mDelayedStopHandler.removeCallbacksAndMessages(null);//移除所有消息
         mSession.release();//释放资源
     }

     /**
      * 浏览器服务，提供onGetRoot（控制客户端媒体浏览器的连接请求，通过返回值决定是否允许该客户
      * 端连接服务）和onLoadChildren（媒体浏览器向Service发送数据订阅时调用，一
      * 般在这执行异步获取数据的操作，最后将数据发送至媒体浏览器的回调接口中）两个抽象方法
      */
     @Override
     public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                  Bundle rootHints) {
         LogHelper.e("RunTestT", "OnGetRoot: clientPackageName=" + clientPackageName,
                 "; clientUid=" + clientUid + " ; rootHints=", rootHints);
         // To ensure you are not allowing any arbitrary app to browse your app's contents, you
         // need to check the origin:
         //确定你不是允许任意的app去浏览你的App的内容,你需要检查起源
         //检查连接服务的app是否允许被连接
         Log.e("RunTestT","-----------请求连接的包名--???"+clientPackageName);
         if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
             // If the request comes from an untrusted package, return an empty browser root.
             // If you return null, then the media browser will not be able to connect and
             // no further calls will be made to other media browsing methods.
             //如果请求来自不被信任的包,返回一个空的浏览器根目录,如果返回null,
             //那么媒体浏览器将无法连接，也不会再对其他媒体浏览方法进行调用。
             LogHelper.e("RunTestT", "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                     + "Returning empty browser root so all apps can use MediaController."
                     + clientPackageName);
             return new MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null);
         }
         //noinspection StatementWithEmptyBody
         if (CarHelper.isValidCarPackage(clientPackageName)) {
             // Optional: if your app needs to adapt the music library to show a different subset
             // when connected to the car, this is where you should handle it.
             // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
             // that should be different on cars, you should instead use the boolean flag
             // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
             //可选：如果您的应用需要调整音乐库以在连接到汽车时显示不同的子集，
             // 那么您应该在此处理它。 如果您想调整其他运行时行为，
             // 例如调整广告或更改汽车上应该有所不同的某些行为，
             // 则应使用广播接收器设置的布尔标志（汽车连接接收器）（m连接到汽车）。
         }
         //noinspection StatementWithEmptyBody
         if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
             // Optional: if your app needs to adapt the music library for when browsing from a
             // Wear device, you should return a different MEDIA ROOT here, and then,
             // on onLoadChildren, handle it accordingly.
            // 可选：如果您的应用需要在从Wear设备浏览时调整音乐库，则应在此处返回不同的MEDIA
             //ROOT，然后在Load Children上相应地处理它
         }

         return new BrowserRoot(MEDIA_ID_ROOT, null);
     }

     @Override
     public void onLoadChildren(@NonNull final String parentMediaId,
                                @NonNull final Result<List<MediaItem>> result) {
//         给订阅的客户端发回音乐数据,parentMediaId为查询条件,具体的数据由mMusicProvider提供
         //客户端浏览器mMediaBrowser.subscribe()发起订阅后回调用该方法
         Log.e("RunTestT","------查询数据-------???");
         LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);
         if (MEDIA_ID_EMPTY_ROOT.equals(parentMediaId)) {//不允许连接,返回空目录
             result.sendResult(new ArrayList<MediaItem>());
         } else if (mMusicProvider.isInitialized()) {//媒体资源初始化完成
             // if music library is ready, return immediately  资源准备完毕,直接返回媒体资源
             result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
         } else {
             // otherwise, only return results when the music library is retrieved
             //否则，仅在检索音乐库时返回结果
             result.detach();//延迟返回结果
             mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {//加载资源,加载结束后返回结果
                 @Override
                 public void onMusicCatalogReady(boolean success) {
                     result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
                 }
             });
         }
     }

     /**
      * Callback method called from PlaybackManager whenever the music is about to play.
      */
     @Override
     public void onPlaybackStart() {//开始播放
         mSession.setActive(true);//设置活动状态,用于媒体按钮显示控制

         mDelayedStopHandler.removeCallbacksAndMessages(null);

         // The service needs to continue running even after the bound client (usually a
         // MediaController) disconnects, otherwise the music playback will stop.
         // Calling startService(Intent) will keep the service running until it is explicitly killed.
         //即使绑定客户端（通常是媒体控制器）断开连接，服务也需要继续运行，否则音乐播放将停止。
         // 调用启动服务（Intent）将使服务保持运行，直到它被明确终止
         startService(new Intent(getApplicationContext(), MusicService.class));
     }


     /**
      * Callback method called from PlaybackManager whenever the music stops playing.
      */
     @Override
     public void onPlaybackStop() {
         mSession.setActive(false);//隐藏控制按钮
         // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
         // potentially stopping the service.//重置延迟停止,延迟时间到达后将重新执行,服务可能被停止
         mDelayedStopHandler.removeCallbacksAndMessages(null);
         mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
         stopForeground(true);//移除通知
     }

     @Override
     public void onNotificationRequired() {//发送通知
         mMediaNotificationManager.startNotification();
     }

     @Override
     public void onPlaybackStateUpdated(PlaybackStateCompat newState) {//播放状态更新
         mSession.setPlaybackState(newState);//更新MediaSessionCompat的播放状态
     }

     private void registerCarConnectionReceiver() {//注册链接服务广播
         IntentFilter filter = new IntentFilter(CarHelper.ACTION_MEDIA_STATUS);//com.google.android.gms.car.media.STATUS
         mCarConnectionReceiver = new BroadcastReceiver() {
             @Override
             public void onReceive(Context context, Intent intent) {
                 String connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS);
                 mIsConnectedToCar = CarHelper.MEDIA_CONNECTED.equals(connectionEvent);
                 Log.e("RunTest","Connection event to Android Auto: "+ connectionEvent+
                         " isConnectedToCar="+mIsConnectedToCar);
                 //是否正在连接服务器???
             }
         };
         registerReceiver(mCarConnectionReceiver, filter);
     }

     private void unregisterCarConnectionReceiver() {
         unregisterReceiver(mCarConnectionReceiver);//解除广播接收
     }

     /**
      * A simple handler that stops the service if playback is not active (playing)
      */
     private static class DelayedStopHandler extends Handler {//延迟停止服务handler
         private final WeakReference<MusicService> mWeakReference;

         private DelayedStopHandler(MusicService service) {
             mWeakReference = new WeakReference<>(service);
         }

         @Override
         public void handleMessage(Message msg) {
             MusicService service = mWeakReference.get();
             if (service != null && service.mPlaybackManager.getPlayback() != null) {
                 if (service.mPlaybackManager.getPlayback().isPlaying()) {//正在播放
                     LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                     return;
                 }
                 LogHelper.d(TAG, "Stopping service with delay handler.");
                 service.stopSelf();//30s后无播放停止服务
             }
         }
     }

     /**
      * Session Manager Listener responsible for switching the Playback instances
      * depending on whether it is connected to a remote player.
      * 会话管理器监听器负责根据播放器实例是否连接到远程播放器来切换播放实例
      */
     private class CastSessionManagerListener implements SessionManagerListener<CastSession> {

         @Override
         public void onSessionEnded(CastSession session, int error) {//服务连接完成??会话结束
             LogHelper.d(TAG, "onSessionEnded");
             mSessionExtras.remove(EXTRA_CONNECTED_CAST);
             mSession.setExtras(mSessionExtras);
             //初始化本地播放器
             Playback playback = new LocalPlayback(MusicService.this, mMusicProvider);
             //清空,用addRemoteControlClient替代
             mMediaRouter.setMediaSessionCompat(null);
             //切换播放实例,尽可能保持原有状态
             mPlaybackManager.switchToPlayback(playback, false);
         }

         @Override
         public void onSessionResumed(CastSession session, boolean wasSuspended) {//会话恢复
         }

         @Override
         public void onSessionStarted(CastSession session, String sessionId) {//会话开始
             // In case we are casting, send the device name as an extra on MediaSession metadata.
             //如果我们正在进行投射，请将设备名称作为媒体会话元数据的额外信息发送。
             mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                     session.getCastDevice().getFriendlyName());
             mSession.setExtras(mSessionExtras);
             // Now we can switch to CastPlayback  切换到CastPlayback
             Playback playback = new CastPlayback(mMusicProvider, MusicService.this);
             mMediaRouter.setMediaSessionCompat(mSession);
             mPlaybackManager.switchToPlayback(playback, true);
         }

         @Override
         public void onSessionStarting(CastSession session) {
         }

         @Override
         public void onSessionStartFailed(CastSession session, int error) {
         }

         @Override
         public void onSessionEnding(CastSession session) {//会话正在结束
             // This is our final chance to update the underlying stream position
             // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
             // is disconnected and hence we update our local value of stream position
             // to the latest position.
             //这是我们更新基础流位置的最后机会onSessionEnded（）上，基础CastPlayback#mRemoteMediaClient
             // 远程媒体客户端断开连接，因此我们将流位置的本地值更新到最新位置。
             //更新当前播放的最新位置,以供接下来的会话使用
             mPlaybackManager.getPlayback().updateLastKnownStreamPosition();
         }

         @Override
         public void onSessionResuming(CastSession session, String sessionId) {
         }

         @Override
         public void onSessionResumeFailed(CastSession session, int error) {
         }

         @Override
         public void onSessionSuspended(CastSession session, int reason) {
         }
     }
 }
