package me.shaohui.shareutil.login.instance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.sina.weibo.sdk.api.share.IWeiboShareAPI;
import com.sina.weibo.sdk.api.share.WeiboShareSDK;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;

import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;

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
import me.shaohui.shareutil.login.result.WeiboToken;
import me.shaohui.shareutil.login.result.WeiboUser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static me.shaohui.shareutil.ShareLogger.INFO;

/**
 * Created by shaohui on 2016/12/1.
 */

public class WeiboLoginInstance extends LoginInstance {

    private static final String USER_INFO = "https://api.weibo.com/2/users/show.json";

    private SsoHandler mSsoHandler;

    private LoginListener mLoginListener;

    public WeiboLoginInstance(Activity activity, LoginListener listener, boolean fetchUserInfo) {
        super(activity, listener, fetchUserInfo);
        AuthInfo authInfo = new AuthInfo(activity, ShareManager.CONFIG.getWeiboId(),
                ShareManager.CONFIG.getWeiboRedirectUrl(), ShareManager.CONFIG.getWeiboScope());
        mSsoHandler = new SsoHandler(activity, authInfo);
        mLoginListener = listener;
    }

    @Override
    public void doLogin(Activity activity, final LoginListener listener,
            final boolean fetchUserInfo) {
        mSsoHandler.authorize(new WeiboAuthListener() {
            @Override
            public void onComplete(Bundle bundle) {
                Oauth2AccessToken accessToken = Oauth2AccessToken.parseAccessToken(bundle);
                WeiboToken weiboToken = WeiboToken.parse(accessToken);
                if (fetchUserInfo) {
                    listener.beforeFetchUserInfo(weiboToken);
                    fetchUserInfo(weiboToken);
                } else {
                    listener.loginSuccess(new LoginResult(LoginPlatform.WEIBO, weiboToken));
                }
            }

            @Override
            public void onWeiboException(WeiboException e) {
                ShareLogger.i(INFO.WEIBO_AUTH_ERROR);
                listener.loginFailure(e);
            }

            @Override
            public void onCancel() {
                ShareLogger.i(INFO.AUTH_CANCEL);
                listener.loginCancel();
            }
        });
    }

    @Override
    public void fetchUserInfo(final BaseToken token) {

        Flowable<WeiboUser> flowable = Flowable.create(new FlowableOnSubscribe<WeiboUser>() {
            @Override
            public void subscribe(FlowableEmitter<WeiboUser> weiboUserEmitter) throws Exception {
                OkHttpClient client = new OkHttpClient();
                Request request =
                        new Request.Builder().url(buildUserInfoUrl(token, USER_INFO)).build();
                try {
                    Response response = client.newCall(request).execute();
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    WeiboUser user = WeiboUser.parse(jsonObject);
                    weiboUserEmitter.onNext(user);
                } catch (IOException | JSONException e) {
                    ShareLogger.e(INFO.FETCH_USER_INOF_ERROR);
                    weiboUserEmitter.onError(e);
                }
            }
        }, BackpressureStrategy.DROP);

        flowable.subscribeOn(Schedulers.io());
        flowable.observeOn(AndroidSchedulers.mainThread());
        flowable.subscribe(new Subscriber<WeiboUser>() {
            @Override
            public void onSubscribe(Subscription s) {

            }

            @Override
            public void onNext(WeiboUser weiboUser) {
                mLoginListener.loginSuccess(
                        new LoginResult(LoginPlatform.WEIBO, token, weiboUser));
            }

            @Override
            public void onError(Throwable throwable) {
                mLoginListener.loginFailure(new Exception(throwable));
            }

            @Override
            public void onComplete() {

            }
        });



//        Observable.fromEmitter(new Action1<Emitter<WeiboUser>>() {
//            @Override
//            public void call(Emitter<WeiboUser> weiboUserEmitter) {
//                OkHttpClient client = new OkHttpClient();
//                Request request =
//                        new Request.Builder().url(buildUserInfoUrl(token, USER_INFO)).build();
//                try {
//                    Response response = client.newCall(request).execute();
//                    JSONObject jsonObject = new JSONObject(response.body().string());
//                    WeiboUser user = WeiboUser.parse(jsonObject);
//                    weiboUserEmitter.onNext(user);
//                } catch (IOException | JSONException e) {
//                    ShareLogger.e(INFO.FETCH_USER_INOF_ERROR);
//                    weiboUserEmitter.onError(e);
//                }
//            }
//        }, Emitter.BackpressureMode.DROP)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Action1<WeiboUser>() {
//                    @Override
//                    public void call(WeiboUser weiboUser) {
//                        mLoginListener.loginSuccess(
//                                new LoginResult(LoginPlatform.WEIBO, token, weiboUser));
//                    }
//                }, new Action1<Throwable>() {
//                    @Override
//                    public void call(Throwable throwable) {
//                        mLoginListener.loginFailure(new Exception(throwable));
//                    }
//                });
    }

    private String buildUserInfoUrl(BaseToken token, String baseUrl) {
        return baseUrl + "?access_token=" + token.getAccessToken() + "&uid=" + token.getOpenid();
    }

    @Override
    public void handleResult(int requestCode, int resultCode, Intent data) {
        mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
    }

    @Override
    public boolean isInstall(Context context) {
        IWeiboShareAPI shareAPI =
                WeiboShareSDK.createWeiboAPI(context, ShareManager.CONFIG.getWeiboId());
        return shareAPI.isWeiboAppInstalled();
    }

    @Override
    public void recycle() {
        mSsoHandler = null;
        mLoginListener = null;
    }
}
