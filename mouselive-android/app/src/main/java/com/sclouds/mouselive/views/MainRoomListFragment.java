package com.sclouds.mouselive.views;

import android.os.Bundle;
import android.view.View;

import com.sclouds.basedroid.BaseMVVMFragment;
import com.sclouds.basedroid.ToastUtil;
import com.sclouds.basedroid.Tools;
import com.sclouds.datasource.bean.Room;
import com.sclouds.datasource.bean.RoomUser;
import com.sclouds.datasource.bean.User;
import com.sclouds.datasource.database.DatabaseSvc;
import com.sclouds.datasource.flyservice.http.FlyHttpSvc;
import com.sclouds.datasource.flyservice.http.bean.GetRoomInfo;
import com.sclouds.datasource.flyservice.http.network.BaseObserver;
import com.sclouds.datasource.flyservice.http.network.CustomThrowable;
import com.sclouds.datasource.flyservice.http.network.model.HttpResponse;
import com.sclouds.mouselive.R;
import com.sclouds.mouselive.adapters.RoomsAdapter;
import com.sclouds.mouselive.databinding.FragmentMainRoomsBinding;
import com.sclouds.mouselive.event.EventDeleteRoom;
import com.sclouds.mouselive.viewmodel.RoomListModel;
import com.trello.rxlifecycle3.android.FragmentEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * 首页房间列表
 *
 * @author chenhengfei@yy.com
 * @since 2020年2月19日
 */
public class MainRoomListFragment extends BaseMVVMFragment<FragmentMainRoomsBinding, RoomListModel>
        implements OnRefreshListener {

    private static final String TAG_ROOM_TYPE = "roomType";

    @SuppressWarnings("unused")
    private static final int PAGE_SIZE = 200;
    private int roomType = Room.ROOM_TYPE_LIVE;

    private RoomsAdapter mAdapter;

    public static MainRoomListFragment newInstance(int roomType) {
        Bundle args = new Bundle();
        args.putInt(TAG_ROOM_TYPE, roomType);

        MainRoomListFragment fragment = new MainRoomListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void initBundle(@Nullable Bundle bundle) {
        super.initBundle(bundle);
        assert bundle != null;
        roomType = bundle.getInt(TAG_ROOM_TYPE);
    }

    @Override
    public void initView(View view) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        initSwipeRefreshLayout();

        iniRecycleView();
    }

    private void initSwipeRefreshLayout() {
        if (null == getContext()) {
            return;
        }
        mBinding.swipeRefresh
                .setColorSchemeColors(ContextCompat.getColor(getContext(), R.color.color_scheme1),
                        ContextCompat.getColor(getContext(), R.color.color_scheme2),
                        ContextCompat.getColor(getContext(), R.color.color_scheme3));
        mBinding.swipeRefresh.setOnRefreshListener(this);
    }

    private void iniRecycleView() {
        mBinding.recyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        mAdapter = new RoomsAdapter(getContext());
        mAdapter.setOnItemClickListener((v, p) -> {
            if (!Tools.networkConnected()) {
                ToastUtil.showToast(getContext(), R.string.network_fail);
                return;
            }

            User user = DatabaseSvc.getIntance().getUser();
            if (user == null) {
                return;
            }

            Room currentRoom = mAdapter.getDataAtPosition(p);
            showLoading();
            FlyHttpSvc.getInstance()
                    .getRoomInfo(user.getUid(), currentRoom.getRoomId(), roomType)
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindUntilEvent(FragmentEvent.DESTROY))
                    .subscribe(new BaseObserver<HttpResponse<GetRoomInfo>>(getContext()) {
                        @Override
                        public void handleSuccess(HttpResponse<GetRoomInfo> response) {
                            hideLoading();

                            if (response.Data == null || response.Data.getRoomInfo() == null) {
                                ToastUtil.showToast(getContext(), R.string.room_list_dimiss);
                                mAdapter.deleteItem(p);
                                return;
                            }

                            List<RoomUser> members = response.Data.getUserList();
                            Room room = response.Data.getRoomInfo();
                            if (members != null) {
                                room.setRCount(members.size());
                                room.setMembers(members);
                            }
                            if (room.getRType() != roomType) {
                                ToastUtil.showToast(getContext(), R.string.room_list_dimiss);
                                mAdapter.deleteItem(p);
                                return;
                            }

                            PermissionActivity.startPermissionActivity(getContext(), room);
                        }

                        @Override
                        public void handleError(CustomThrowable e) {
                            super.handleError(e);
                            hideLoading();
                            ToastUtil.showToast(getContext(), R.string.room_list_dimiss);
                            mAdapter.deleteItem(p);
                        }
                    });
        });
        mBinding.recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void initData() {
        super.initData();
        observeRoomList();

        if (roomType >= Room.ROOM_TYPE_KTV) {
            mBinding.swipeRefresh.setEnabled(false);
            mBinding.recyclerView.setEnabled(false);
        } else {
            DatabaseSvc.getIntance().mLiveData.observe(this, user -> {
                if (user != null) {
                    mBinding.swipeRefresh.setRefreshing(true);
                    onRefresh();
                }
            });
        }
    }

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_main_rooms;
    }

    private void observeRoomList() {
        mViewModel.observeRoomList(this, resp -> {
            mAdapter.setData(resp);
            mBinding.swipeRefresh.setRefreshing(false);
        });
    }

    @Override
    public void onRefresh() {
        mViewModel.getRoomList();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (!hidden) {
            mBinding.swipeRefresh.setRefreshing(true);
            onRefresh();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventDeleteRoom(EventDeleteRoom event) {
        Room room = event.getRoom();
        if (room.getRType() == roomType) {
            mAdapter.deleteItem(room);
        }
    }

    @Override
    public RoomListModel iniViewModel() {
        return new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T) new RoomListModel(Objects.requireNonNull(getActivity()).getApplication(),
                        MainRoomListFragment.this, roomType);
            }
        }).get(RoomListModel.class);
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onDestroy();
    }
}