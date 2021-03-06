/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package de.tomgrill.gdxfacebook.html;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.TimeUtils;
import de.tomgrill.gdxfacebook.core.*;

public class HTMLGDXFacebook implements GDXFacebook {

    private GDXFacebookConfig config;
    private Preferences preferences;

    boolean loaded = false;

    private boolean isConnected;

    private GDXFacebookAccessToken accessToken;

    private Array<String> grantedPermissions = new Array<>();

    public HTMLGDXFacebook(GDXFacebookConfig config) {
        this.config = config;
        preferences = Gdx.app.getPreferences(config.PREF_FILENAME);
    }

    private void loadAccessToken() {
        String token = preferences.getString("access_token", null);
        long expiresAt = preferences.getLong("expires_at", 0);

        if (token != null && expiresAt > 0) {
            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Loaded existing accessToken: " + token);
            accessToken = new GDXFacebookAccessToken(token, expiresAt);
        } else {
            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "No accessToken found.");
        }
    }

    @Override
    public void signIn(final SignInMode mode, final Array<String> permissions, final GDXFacebookCallback<SignInResult> callback) {
        if (!loaded) {
            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Facebook SDK not yet loaded. Try again later.");
            return;
        }

        loadAccessToken();

        if (accessToken == null) {
            guiLogin(permissions, callback);
        } else {
            silentLogin(permissions, callback);
//            if(!areSamePermissionsOrMore(permissions, grantedPermissions)) {
//                guiLogin(permissions, callback);
//            }
        }
    }

    private boolean areSamePermissionsOrMore(Array<String> requiredPermissions, Array<String> allPermissions) {

        for (String s : requiredPermissions) {
            if (!allPermissions.contains(s, false)) {
                return false;
            }
        }
        return true;
    }


    private void silentLogin(final Array<String> permissions, final GDXFacebookCallback<SignInResult> callback) {
        Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Starting silent sign in.");

        signOut(false);

        JSNIFacebookSDK.FBLoginState(new StatusCallback() {
            @Override
            public void connected(String token, String expiresIn) {
                isConnected = true;
                long expiresInMillisFromNow = Long.valueOf(expiresIn) * 1000L;
                long expiresInMillisTimestamp = expiresInMillisFromNow + TimeUtils.millis();
                accessToken = new GDXFacebookAccessToken(token, expiresInMillisTimestamp);
                storeToken(accessToken);
//                callback.onSuccess(new SignInResult(accessToken, "Silently logged in. AccessToken and permissions are valid."));
                validatePermissions(permissions, callback);
            }

            @Override
            public void notAuthorized() {
                guiLogin(permissions, callback);
            }

            @Override
            public void disconnected() {
                guiLogin(permissions, callback);
            }
        });
    }

    private void validatePermissions(final Array<String> permissions, final GDXFacebookCallback<SignInResult> callback) {
        Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Checking for permissions");

        GDXFacebookGraphRequest request = new GDXFacebookGraphRequest();
        request.setMethod(Net.HttpMethods.GET);
        request.setNode("me/permissions");
        request.useCurrentAccessToken();

        graph(request, new GDXFacebookCallback<JsonResult>() {
            @Override
            public void onSuccess(JsonResult result) {

                JsonValue value = result.getJsonValue();
                if (value != null && value.has("data")) {
                    JsonValue dataValue = value.get("data");
                    if (dataValue != null && dataValue.isArray()) {

                        grantedPermissions.clear();
                        for (int i = 0; i < dataValue.size; i++) {
                            JsonValue permission = dataValue.get(i);

                            if (permission.getString("status").equals("granted")) {
                                grantedPermissions.add(permission.getString("permission").toLowerCase());
                            }
                        }

                        if (areSamePermissionsOrMore(permissions, grantedPermissions)) {
                            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "AccessToken and permissions are valid.");
                            callback.onSuccess(new SignInResult(accessToken, "AccessToken and permissions are valid."));
                            return;
                        }
                    }
                }
                guiLogin(permissions, callback);
            }

            @Override
            public void onError(GDXFacebookError error) {
                guiLogin(permissions, callback);
            }

            @Override
            public void onFail(Throwable t) {
                guiLogin(permissions, callback);
            }

            @Override
            public void onCancel() {
                guiLogin(permissions, callback);
            }
        });

    }

    private void guiLogin(final Array<String> permissions, final GDXFacebookCallback<SignInResult> callback) {
        signOut(false);

        Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Start GUI login.");
        // GUI LOGIN
        JSNIFacebookSDK.FBLogin(permissionsArrayToString(permissions), new LoginCallback() {

            @Override
            public void success(String token, String expiresIn, String gPermissions) {
                Gdx.app.debug(GDXFacebookVars.LOG_TAG, "success granted: " + gPermissions);

                String[] parts = gPermissions.split(",");

                grantedPermissions.clear();

                for (String s : parts) {
                    grantedPermissions.add(s);
                }

                if (areSamePermissionsOrMore(permissions, grantedPermissions)) {
                    isConnected = true;
                    long expiresInMillisFromNow = Long.valueOf(expiresIn) * 1000L;
                    long expiresInMillisTimestamp = expiresInMillisFromNow + TimeUtils.millis();
                    accessToken = new GDXFacebookAccessToken(token, expiresInMillisTimestamp);
                    storeToken(accessToken);
                    callback.onSuccess(new SignInResult(accessToken, "Login successful. AccessToken and permissions are valid."));
                } else {
                    callback.onError(new GDXFacebookError("User did not grant required permissions."));
                }


            }

            @Override
            public void fail() {
                callback.onError(new GDXFacebookError("Error while trying to login. User cancelled or did not authorize."));
            }

        });
    }

    @Override
    public void showGameRequest(GDXFacebookGameRequest request, GDXFacebookCallback<GameRequestResult> callback) {
        gameRequest(request, callback);
    }

    @Override
    public void gameRequest(GDXFacebookGameRequest request, GDXFacebookCallback<GameRequestResult> callback) {
        if (!loaded) {
            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Facebook SDK not yet loaded. Try again later.");
            return;
        }

        // TODO implement gameRequest
    }

    @Override
    public void newGraphRequest(Request request, GDXFacebookCallback<JsonResult> callback) {
        graph(request, callback);
    }

    @Override
    public void graph(final Request request, final GDXFacebookCallback<JsonResult> callback) {
        if (!loaded) {
            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Facebook SDK not yet loaded. Try again later.");
            return;
        }

        if (request instanceof GDXFacebookGraphRequest) {

            JSNIFacebookSDK.FBapi(request.getNode(), request.getMethod(), request.getJavascriptObjectString(), new JsonCallback() {
                @Override
                public void jsonResult(String jsonString) {
                    callback.onSuccess(new JsonResult(jsonString));
                }

                @Override
                public void error() {
                    callback.onError(new GDXFacebookError("graph API error. View Javascript log for further details."));
                }
            });
        }

        if (request instanceof GDXFacebookMultiPartRequest) {

            /*
            libGDX currently does not allow reading image (and audio) files. That is why a multi-part requests cannot be done currently.
             */

            Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Multipart requests are not supported with GWT backend. See: https://github.com/TomGrill/gdx-facebook/issues/32");

//            String accessToken = null;
//            if (getAccessToken() != null) {
//                accessToken = getAccessToken().getToken();
//            }
//
//            if (request.isUseCurrentAccessToken() && accessToken != null) {
//                request.putField("access_token", accessToken);
//            }
//
//            Net.HttpRequest httpRequest = new Net.HttpRequest(request.getMethod());
//
//            String url = request.getUrl() + config.GRAPH_API_VERSION + "/" + request.getNode();
//
//            httpRequest.setUrl(url);
//
//            httpRequest.setTimeOut(request.getTimeout());
//
//            if (request.getHeaders() != null) {
//                for (int i = 0; i < request.getHeaders().size; i++) {
//                    httpRequest.setHeader(request.getHeaders().getKeyAt(i), request.getHeaders().getValueAt(i));
//                }
//            }
//
//            if (request.getContent() != null) {
//                httpRequest.setContent(request.getContent());
//            } else {
//                try {
//                    InputStream stream = request.getContentStream();
////                System.out.println(getStringFromInputStream(stream));
//                    Gdx.app.debug("TR", StreamUtils.copyStreamToString(stream));
//                    httpRequest.setContent(stream, stream.available());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//
//
//            Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
//
//                @Override
//                public void handleHttpResponse(Net.HttpResponse httpResponse) {
//                    String resultString = httpResponse.getResultAsString();
//                    int statusCode = httpResponse.getStatus().getStatusCode();
//
//                    if (statusCode == -1) {
//                        GDXFacebookError error = new GDXFacebookError("Connection time out. Consider increasing timeout value by using setTimeout()");
//                        callback.onError(error);
//                    } else if (statusCode >= 200 && statusCode < 300) {
//                        callback.onSuccess(new JsonResult(resultString));
//                    } else {
//                        GDXFacebookError error = new GDXFacebookError("Error: " + resultString);
//                        callback.onError(error);
//                    }
//                }
//
//                @Override
//                public void failed(Throwable t) {
//                    t.printStackTrace();
//                    callback.onFail(t);
//                }
//
//                @Override
//                public void cancelled() {
//                    callback.onCancel();
//                }
//            });
        }
    }

    @Override
    public void signOut(boolean keepSessionData) {
        accessToken = null;
        isConnected = false;
        grantedPermissions.clear();
        if (!keepSessionData) {
            deleteTokenData();
        }
    }

    @Override
    public void signOut() {
        signOut(true);
    }

    @Override
    public boolean isSignedIn() {
        return isConnected;
    }

    @Override
    public GDXFacebookAccessToken getAccessToken() {
        return accessToken;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    private static String permissionsArrayToString(Array<String> permissions) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < permissions.size; i++) {

            stringBuilder.append(permissions.get(i));
            if (i + 1 < permissions.size) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }

    private void deleteTokenData() {
        preferences.remove("access_token");
        preferences.remove("expires_at");
        preferences.flush();

    }

    private void storeToken(GDXFacebookAccessToken token) {
        Gdx.app.debug(GDXFacebookVars.LOG_TAG, "Storing new accessToken: " + token.getToken() + "; expires at: " + +token.getExpiresAt());
        preferences.putString("access_token", token.getToken());
        preferences.putLong("expires_at", token.getExpiresAt());
        preferences.flush();
    }

}