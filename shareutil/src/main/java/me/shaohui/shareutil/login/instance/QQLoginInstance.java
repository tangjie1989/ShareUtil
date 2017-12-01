package me.shaohui.shareutil.login.instance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.shaohui.shareutil.ShareLogger;
import me.shaohui.shareutil.ShareManager;
import me.shaohui.shareutil.login.LoginListener;
import me.shaohui.shareutil.login.LoginPlatform;
import me.shaohui.shareutil.login.LoginResult;
import me.shaohui.shareutil.login.result.BaseToken;
import me.shaohui.shareutil.login.result.QQToken;
import me.shaohui.shareutil.login.result.QQUser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static me.shaohui.shareutil.ShareLogger.INFO;

/**
 * Created by shaohui on 2016/12/1.
 */

public class QQLoginInstance extends LoginInstance {

    private static final String SCOPE = "get_simple_userinfo";

    private static final String URL = "https://graph.qq.com/user/get_user_info";

    private Tencent mTencent;

    private IUiListener mIUiListener;

    private LoginListener mLoginListener;

    public QQLoginInstance(Activity activity, final LoginListener listener,
            final boolean fetchUserInfo) {
        super(activity, listener, fetchUserInfo);
        mTencent = Tencent.createInstance(ShareManager.CONFIG.getQqId(),
                activity.getApplicationContext());
        mLoginListener = listener;
        mIUiListener = new IUiListener() {
            @Override
            public void onComplete(Object o) {
                ShareLogger.i(INFO.QQ_AUTH_SUCCESS);
                try {
                    QQToken token = QQToken.parse((JSONObject) o);
                    if (fetchUserInfo) {
                        listener.beforeFetchUserInfo(token);
                        fetchUserInfo(token);
                    } else {
                        listener.loginSuccess(new LoginResult(LoginPlatform.QQ, token));
                    }
                } catch (JSONException e) {
                    ShareLogger.i(INFO.ILLEGAL_TOKEN);
                    mLoginListener.loginFailure(e);
                }
            }

            @Override
            public void onError(UiError uiError) {
                ShareLogger.i(INFO.QQ_LOGIN_ERROR);
                listener.loginFailure(
                        new Exception("QQError: " + uiError.errorCode + uiError.errorDetail));
            }

            @Override
            public void onCancel() {
                ShareLogger.i(INFO.AUTH_CANCEL);
                listener.loginCancel();
            }
        };
    }

    @Override
    public void doLogin(Activity activity, final LoginListener listener, boolean fetchUserInfo) {
        mTencent.login(activity, SCOPE, mIUiListener);
    }

    @Override
    public void fetchUserInfo(final BaseToken token) {

        Flowable<QQUser> flowable = Flowable.create(new FlowableOnSubscribe<QQUser>() {
            @Override
            public void subscribe(FlowableEmitter<QQUser> qqUserEmitter) throws Exception {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(buildUserInfoUrl(token, URL)).build();
                try {
                    Response response = client.newCall(request).execute();
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    QQUser user = QQUser.parse(token.getOpenid(), jsonObject);
                    qqUserEmitter.onNext(user);
                } catch (IOException | JSONException e) {
                    ShareLogger.e(INFO.FETCH_USER_INOF_ERROR);
                    qqUserEmitter.onError(e);
                }
            }
        },BackpressureStrategy.DROP);

        flowable.subscribeOn(Schedulers.io());
        flowable.observeOn(AndroidSchedulers.mainThread());
        flowable.subscribe(new Subscriber<QQUser>() {
            @Override
            public void onSubscribe(Subscription s) {

            }

            @Override
            public void onNext(QQUser qqUser) {
                mLoginListener.loginSuccess(
                        new LoginResult(LoginPlatform.QQ, token, qqUser));
            }

            @Override
            public void onError(Throwable throwable) {
                mLoginListener.loginFailure(new Exception(throwable));
            }

            @Override
            public void onComplete() {

            }
        });


//        Observable.fromEmitter(new Action1<Emitter<QQUser>>() {
//            @Override
//            public void call(Emitter<QQUser> qqUserEmitter) {
//                OkHttpClient client = new OkHttpClient();
//                Request request = new Request.Builder().url(buildUserInfoUrl(token, URL)).build();
//
//                try {
//                    Response response = client.newCall(request).execute();
//                    JSONObject jsonObject = new JSONObject(response.body().string());
//                    QQUser user = QQUser.parse(token.getOpenid(), jsonObject);
//                    qqUserEmitter.onNext(user);
//                } catch (IOException | JSONException e) {
//                    ShareLogger.e(INFO.FETCH_USER_INOF_ERROR);
//                    qqUserEmitter.onError(e);
//                }
//            }
//        }, BackpressureStrategy.DROP)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Action1<QQUser>() {
//                    @Override
//                    public void call(QQUser qqUser) {
//                        mLoginListener.loginSuccess(
//                                new LoginResult(LoginPlatform.QQ, token, qqUser));
//                    }
//                }, new Action1<Throwable>() {
//                    @Override
//                    public void call(Throwable throwable) {
//                        mLoginListener.loginFailure(new Exception(throwable));
//                    }
//                });
    }

    private String buildUserInfoUrl(BaseToken token, String base) {
        return base
                + "?access_token="
                + token.getAccessToken()
                + "&oauth_consumer_key="
                + ShareManager.CONFIG.getQqId()
                + "&openid="
                + token.getOpenid();
    }

    @Override
    public void handleResult(int requestCode, int resultCode, Intent data) {
        Tencent.handleResultData(data, mIUiListener);
    }

    @Override
    public boolean isInstall(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return false;
        }

        List<PackageInfo> packageInfos = pm.getInstalledPackages(0);
        for (PackageInfo info : packageInfos) {
            if (TextUtils.equals(info.packageName.toLowerCase(), "com.tencent.mobileqq")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void recycle() {
        mTencent.releaseResource();
        mIUiListener = null;
        mLoginListener = null;
        mTencent = null;
    }
}
