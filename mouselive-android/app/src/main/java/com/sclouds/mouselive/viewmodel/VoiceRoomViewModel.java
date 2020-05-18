package com.sclouds.mouselive.viewmodel;

import android.annotation.SuppressLint;
import android.app.Application;

import com.sclouds.basedroid.LogUtils;
import com.sclouds.datasource.bean.Room;
import com.sclouds.datasource.bean.RoomUser;
import com.sclouds.datasource.business.pkg.ChatPacket;
import com.sclouds.datasource.flyservice.funws.FunWSSvc;
import com.sclouds.datasource.thunder.ThunderSvc;
import com.sclouds.datasource.thunder.mode.ThunderConfig;
import com.sclouds.datasource.thunder.mode.VoiceConfig;
import com.sclouds.mouselive.R;
import com.sclouds.mouselive.utils.FileUtil;
import com.sclouds.mouselive.utils.SampleSingleObserver;
import com.sclouds.mouselive.view.IRoomView;
import com.sclouds.mouselive.views.dialog.RoomLianMaiDialog;
import com.sclouds.mouselive.views.dialog.VoiceChangerDialog;
import com.thunder.livesdk.ThunderRtcConstant;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * 聊天室，逻辑成代码
 *
 * @author chenhengfei@yy.com
 * @since 2020/03/01
 */
public class VoiceRoomViewModel extends BaseRoomViewModel<IRoomView> {

    private List<RoomUser> chatingMembers = new CopyOnWriteArrayList<>();

    public VoiceRoomViewModel(@NonNull Application application,
                              @NonNull IRoomView mView, @NonNull Room room) {
        super(application, mView, room);

        String desPath = FileUtil.getMusic(getApplication());
        FileUtil.copyRawFile(getApplication(), desPath, "music.mp3", R.raw.music);

        VoiceChangerDialog.isEnableEar = false;
        ThunderSvc.getInstance().setEnableInEarMonitor(VoiceChangerDialog.isEnableEar);

        VoiceChangerDialog.isVoiceChanged = false;
        ThunderSvc.getInstance().setVoiceChanger(
                ThunderRtcConstant.VoiceChangerMode.THUNDER_VOICE_CHANGER_MODE_NONE);
    }

    @Override
    protected void resetRoomInfo() {
        super.resetRoomInfo();
        if (chatingMembers != null && !chatingMembers.isEmpty()) {
            for (RoomUser chatingMember : chatingMembers) {
                onMemberChatStop(chatingMember);
            }
        }
    }

    @Override
    public boolean isInChating() {
        return chatingMembers.contains(getMine());
    }

    @Override
    public boolean isInChating(@NonNull RoomUser user) {
        return chatingMembers.contains(user);
    }

    @Nullable
    @Override
    public RoomUser getChatingMember(long userId) {
        for (RoomUser chatingMember : chatingMembers) {
            if (chatingMember.getUid() == userId) {
                return chatingMember;
            }
        }
        return null;
    }

    @Override
    protected void onJoinThunderSuccess() {
        if (isRoomOwner()) {
            if (reJoinRoom == false) {
                startPublish();
            }
        }
        super.onJoinThunderSuccess();
    }

    /**
     * 对于聊天室而言，因为有背景音乐的存在，所以不能单纯的关闭和打开本地麦克风
     *
     * @param isEnable
     */
    @Override
    protected void toggleThunderMic(boolean isEnable) {
        if (isRoomOwner()) {
            //只有房主需要考虑背景音乐
            ThunderSvc.getInstance().toggleMicWithMusicEnable(isEnable);
        } else {
            ThunderSvc.getInstance().toggleMicEnable(isEnable);
        }
    }

    /**
     * 开推
     */
    public void startPublish() {
        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        ThunderSvc.getInstance().publishAudioStream();
        mine.setSelfMicEnable(true);
        onAudioStart(getMine());
    }

    /**
     * 停止推
     */
    public void stopPublish() {
        ThunderSvc.getInstance().stopPublishAudioStream();

        RoomUser mine = getMine();
        if (mine != null) {
            mine.setSelfMicEnable(false);
            onAudioStop(mine);
        }
    }

    @Override
    protected void onBasicInfoChanged(Room room) {
        if (isRoomOwner() == false && isInChating()) {
            //房间开麦和闭麦的改变，需要对自己的麦克风进行控制
            RoomUser mine = getMine();
            if (mine == null) {
                return;
            }

            if (mine.isSelfMicEnable()) {
                //如果本地是打开状态，才需要开启或者关闭thunder
                toggleThunderMic(mine.isMicEnable());
            }
        }
        super.onBasicInfoChanged(room);
    }

    @SuppressLint("CheckResult")
    public void acceptChat(RoomUser user, RoomLianMaiDialog dialog) {
        FunWSSvc.getInstance()
                .acceptChat(user.getUid(), user.getRoomId())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(aBoolean -> {
                    dialog.dismiss();

                    onMemberChatStart(user);

                    if (isFullChating()) {
                        clearAllRequest();
                    } else {
                        doNextReuqest();
                    }
                });
    }

    @SuppressLint("CheckResult")
    public void refuseChat(RoomUser user, RoomLianMaiDialog dialog) {
        FunWSSvc.getInstance()
                .rejectChat(user.getUid(), user.getRoomId())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(aBoolean -> {
                    dialog.dismiss();
                    doNextReuqest();
                });
    }

    @Override
    public void onChatRev(ChatPacket chatPkg) {
        LogUtils.d(TAG, "onChatRev() called with: chatPkg = [" + chatPkg + "]");
        if (isFullChating()) {
            LogUtils.d(TAG, "onChatRev() isFullChating()");
            return;
        }

        getUserSync(chatPkg.Body.SrcUid)
                .compose(bindToLifecycle())
                .subscribe(new SampleSingleObserver<RoomUser>() {
                    @Override
                    public void onSuccess(RoomUser user) {
                        mRoomQueueAction.onChatRequestMeesage(user);
                    }
                });
    }

    @Override
    public void onChatCanel(ChatPacket chatPkg) {
        LogUtils.d(TAG, "onChatCanel() called with: chatPkg = [" + chatPkg + "]");
        long targetId = chatPkg.Body.SrcUid;
        long targetRoomId = chatPkg.Body.SrcUid;
        mRoomQueueAction.onCancel(targetId);
    }

    /**
     * 被对方断开
     *
     * @param chatPkg
     */
    @Override
    public void onChatHangup(ChatPacket chatPkg) {
        LogUtils.d(TAG, "onChatHangup() called with: chatPkg = [" + chatPkg + "]");
        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        long targetId = chatPkg.Body.SrcUid;
        long targetRoomId = chatPkg.Body.SrcUid;

        if (isRoomOwner()) {
            //对方是观众，他自己挂断了
            getUserSync(targetRoomId, targetId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe(new SampleSingleObserver<RoomUser>() {
                        @Override
                        public void onSuccess(RoomUser user) {
                            onMemberChatStop(user);
                        }
                    });

        } else {
            //我是观众，我被主播挂断了
            Completable.create(new CompletableOnSubscribe() {
                @Override
                public void subscribe(CompletableEmitter emitter) throws Exception {
                    emitter.onComplete();
                }
            }).observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe(new CompletableObserver() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onComplete() {
                            //onCloseChating()只处理观众
                            onMemberChatStop(mine);
                        }

                        @Override
                        public void onError(Throwable e) {

                        }
                    });
        }
    }

    @Override
    public void onMuiltCastChatHangup(ChatPacket chatPkg) {
        LogUtils.d(TAG, "onMuiltCastChatHangup() called with: chatPkg = [" + chatPkg + "]");
        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        if (chatPkg.Body.SrcUid == mine.getUid() || chatPkg.Body.DestUid == mine.getUid()) {
            //只有观众处理下面的逻辑
            return;
        }

        RoomUser owner = getOwnerUser();
        long targetId = chatPkg.Body.SrcUid;
        long targetRoomId = chatPkg.Body.SrcRoomId;
        if (targetId == owner.getUid()) {
            //排除我的房主，找到对方
            targetId = chatPkg.Body.DestUid;
            targetRoomId = chatPkg.Body.DestRoomId;
        }

        getUserSync(targetRoomId, targetId)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(new SampleSingleObserver<RoomUser>() {
                    @Override
                    public void onSuccess(RoomUser user) {
                        onMemberChatStop(user);
                    }
                });
    }

    @Override
    public void onMuiltCastChating(ChatPacket chatPkg) {
        LogUtils.d(TAG, "onMuiltCastChating() called with: chatPkg = [" + chatPkg + "]");
        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        if (chatPkg.Body.SrcUid == mine.getUid() || chatPkg.Body.DestUid == mine.getUid()) {
            //只有观众处理下面的逻辑
            return;
        }

        long targetUId = chatPkg.Body.SrcUid;
        long targetRoomId = chatPkg.Body.SrcRoomId;
        RoomUser owner = getOwnerUser();
        if (targetUId == owner.getUid()) {
            //排除我的房主，找到对方
            targetUId = chatPkg.Body.DestUid;
            targetRoomId = chatPkg.Body.DestRoomId;
        }

        getUserSync(targetRoomId, targetUId)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(new SampleSingleObserver<RoomUser>() {
                    @Override
                    public void onSuccess(RoomUser user) {
                        onMemberChatStart(user);
                    }
                });
    }

    @Override
    protected void onUserInChating(@NotNull RoomUser user) {
        if (ObjectsCompat.equals(user, getOwnerUser())) {
            //聊天室，不需要处理房主的，因为是多人连麦
            return;
        }

        long targetRoomID = getRoom().getRoomId();
        long targetUID = user.getUid();

        getUserSync(targetRoomID, targetUID)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(new SampleSingleObserver<RoomUser>() {
                    @Override
                    public void onSuccess(RoomUser user) {
                        onMemberChatStart(user);
                    }
                });
    }

    /**
     * 是否已经满员
     *
     * @return
     */
    public boolean isFullChating() {
        return chatingMembers.size() >= 8;
    }

    @Override
    public void onMemberChatStart(@NonNull RoomUser user) {
        if (chatingMembers.contains(user)) {
            //已经在连麦，就退出逻辑
            return;
        }

        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        Room room = getRoom();
        user.setMicEnable(room.getRMicEnable());

        if (ObjectsCompat.equals(user, mine)) {
            if (user.isMicEnable()) {
                startPublish();
            }
        }
        chatingMembers.add(user);
        super.onMemberChatStart(user);
    }

    @Override
    public void onMemberChatStop(@NonNull RoomUser user) {
        if (!chatingMembers.contains(user)) {
            //没在连麦，就退出逻辑
            return;
        }

        RoomUser mine = getMine();
        if (mine == null) {
            return;
        }

        Room room = getRoom();
        user.setMicEnable(room.getRMicEnable());

        if (ObjectsCompat.equals(user, mine)) {
            stopPublish();

            VoiceChangerDialog.isEnableEar = false;
            ThunderSvc.getInstance().setEnableInEarMonitor(VoiceChangerDialog.isEnableEar);
        }

        chatingMembers.remove(user);
        super.onMemberChatStop(user);
    }

    @Override
    protected ThunderConfig getThunderConfig() {
        return new VoiceConfig();
    }

    @Override
    protected void close() {
        if (isRoomOwner()) {
            stopPublish();
        } else {
            if (isInChating()) {
                stopPublish();
            }
        }

        chatingMembers.clear();
        super.close();
    }
}