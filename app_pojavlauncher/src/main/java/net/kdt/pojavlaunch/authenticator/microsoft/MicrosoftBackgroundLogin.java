package net.kdt.pojavlaunch.authenticator.microsoft;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/** Allow to perform a background login on a given account */
// TODO handle connection errors !
public class MicrosoftBackgroundLogin {
    private static final String authTokenUrl = "https://login.live.com/oauth20_token.srf";
    private static final String xblAuthUrl = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String xstsAuthUrl = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String mcLoginUrl = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String mcProfileUrl = "https://api.minecraftservices.com/minecraft/profile";
    private static final String mcStoreUrl = "https://api.minecraftservices.com/entitlements/mcstore";

    private final boolean mIsRefresh;
    private final String mAuthCode;
    private static final Map<Long, Integer> XSTS_ERRORS;
    static {
        XSTS_ERRORS = new ArrayMap<>();
        XSTS_ERRORS.put(2148916233L, R.string.xerr_no_account);
        XSTS_ERRORS.put(2148916235L, R.string.xerr_not_available);
        XSTS_ERRORS.put(2148916236L ,R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916237L ,R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916238L ,R.string.xerr_child);
    }

    /* Fields used to fill the account  */
    public String msRefreshToken;
    public String mcName;
    public String mcToken;
    public String mcUuid;
    public boolean doesOwnGame, hasProfile = false;
    public long expiresAt;

    public MicrosoftBackgroundLogin(boolean isRefresh, String authCode){
        mIsRefresh = isRefresh;
        mAuthCode = authCode;
    }

    /** Performs a full login, calling back listeners appropriately  */
    public void performLogin(@Nullable final ProgressListener progressListener,
                             @Nullable final DoneListener doneListener,
                             @Nullable final ErrorListener errorListener){
        sExecutorService.execute(() -> {
            try {
                notifyProgress(progressListener, 1);
                String accessToken = acquireAccessToken(mIsRefresh, mAuthCode);
                notifyProgress(progressListener, 2);
                String xboxLiveToken = acquireXBLToken(accessToken);
                notifyProgress(progressListener, 3);
                String[] xsts = acquireXsts(xboxLiveToken);
                notifyProgress(progressListener, 4);
                String mcToken = acquireMinecraftToken(xsts[0], xsts[1]);
                notifyProgress(progressListener, 5);
                checkMcProfile(mcToken);
                fetchOwnedItems(mcToken);

                if (!hasProfile && doesOwnGame) {
                    throw new PresentedException(R.string.minecraft_no_username_set);
                } else if (!doesOwnGame) {
                    mcName = "Demo.Player";
                    mcUuid = "00000000-0000-0000-0000-000000000000";
                } else if (mcName == null || mcUuid == null)
                    throw new IllegalStateException("This should never happen, please report this as a bug");

                MinecraftAccount acc = MinecraftAccount.load(mcName);
                if(acc == null) acc = new MinecraftAccount();
                acc.xuid = xsts[0];
                acc.clientToken = "0"; /* FIXME */
                acc.accessToken = mcToken;
                acc.username = mcName;
                acc.profileId = mcUuid;
                acc.isMicrosoft = true;
                acc.msaRefreshToken = msRefreshToken;
                acc.expiresAt = expiresAt;
                acc.updateSkinFace();
                acc.save();

                if(doneListener != null) {
                    MinecraftAccount finalAcc = acc;
                    Tools.runOnUiThread(() -> doneListener.onLoginDone(finalAcc));
                }

            }catch (Exception e){
                Log.e("MicroAuth", "Exception thrown during authentication", e);
                if(errorListener != null)
                    Tools.runOnUiThread(() -> errorListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
        });
    }

    public String acquireAccessToken(boolean isRefresh, String authcode) throws IOException, JSONException {
        URL url = new URL(authTokenUrl);
        Log.i("MicrosoftLogin", "isRefresh=" + isRefresh + ", authCode= "+authcode);

        String formData = convertToFormData(
                "client_id", "00000000402b5328",
                isRefresh ? "refresh_token" : "code", authcode,
                "grant_type", isRefresh ? "refresh_token" : "authorization_code",
                "redirect_url", "https://login.live.com/oauth20_desktop.srf",
                "scope", "service::user.auth.xboxlive.com::MBI_SSL"
        );

        Log.i("MicroAuth", formData);

        //да пошла yf[eq1 она ваша джава 11
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(formData.getBytes(StandardCharsets.UTF_8));
        }
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            msRefreshToken = jo.getString("refresh_token");
            conn.disconnect();
            Log.i("MicrosoftLogin","Acess Token = " + jo.getString("access_token"));
            return jo.getString("access_token");
            //acquireXBLToken(jo.getString("access_token"));
        }else{
            throw getResponseThrowable(conn);
        }
    }

    private String acquireXBLToken(String accessToken) throws IOException, JSONException {
        URL url = new URL(xblAuthUrl);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", accessToken);
        data.put("Properties",properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            Log.i("MicrosoftLogin","Xbl Token = "+jo.getString("Token"));
            return jo.getString("Token");
            //acquireXsts(jo.getString("Token"));
        }else{
            throw getResponseThrowable(conn);
        }
    }

    /** @return [uhs, token]*/
    private @NonNull String[] acquireXsts(String xblToken) throws IOException, JSONException {
        URL url = new URL(xstsAuthUrl);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens", new JSONArray(Collections.singleton(xblToken)));
        data.put("Properties", properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        String req = data.toString();
        Log.i("MicroAuth", req);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        Log.i("MicroAuth", conn.getRequestMethod());
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            String token = jo.getString("Token");
            conn.disconnect();
            Log.i("MicrosoftLogin","Xbl Xsts = " + token + "; Uhs = " + uhs);
            return new String[]{uhs, token};
            //acquireMinecraftToken(uhs,jo.getString("Token"));
        }else if(conn.getResponseCode() == 401) {
            String responseContents = Tools.read(conn.getErrorStream());
            JSONObject jo = new JSONObject(responseContents);
            long xerr = jo.optLong("XErr", -1);
            Integer locale_id = XSTS_ERRORS.get(xerr);
            if(locale_id != null) {
                throw new PresentedException(new RuntimeException(responseContents), locale_id);
            }
            throw new PresentedException(new RuntimeException(responseContents), R.string.xerr_unknown, xerr);
        }else{
            throw getResponseThrowable(conn);
        }
    }

    private String acquireMinecraftToken(String xblUhs, String xblXsts) throws IOException, JSONException {
        URL url = new URL(mcLoginUrl);

        JSONObject data = new JSONObject();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            expiresAt = System.currentTimeMillis() + 86400000;
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            Log.i("MicrosoftLogin","MC token: "+jo.getString("access_token"));
            mcToken = jo.getString("access_token");
            //checkMcProfile(jo.getString("access_token"));
            return jo.getString("access_token");
        }else{
            throw getResponseThrowable(conn);
        }
    }

    private void fetchOwnedItems(String mcAccessToken) throws IOException {
        // We only need to do this if user does not have a profile/username yet
        if (hasProfile) return;
        URL url = new URL(mcStoreUrl);
        String s = "";

        // For some reason, minecraftservices APIs are significantly more unreliable
        // Automatically retry because the user gets annoyed when they have to log in again
        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.setUseCaches(false);
            conn.connect();
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                s = Tools.read(conn.getInputStream());
                conn.disconnect();
                break;
            } else if (retryCount == 4) {
                throw getResponseThrowable(conn);
            }
            try { Thread.sleep(500L * (1L << retryCount)); // 0.5s, 1s, 2s, 4s, 8s
            } catch (InterruptedException ignored) {}
        }
        try {
            String jwtSignature = new JSONObject(s).getString("signature");
            String jwtBody = jwtSignature.split("\\.")[1];

            String signature = new String(
                    Base64.decode(jwtBody, Base64.DEFAULT),
                    StandardCharsets.UTF_8
            );
            JSONObject jsonSignature = new JSONObject(signature);
            JSONArray entitlements = jsonSignature.getJSONArray("entitlements");
            for (int i = 0; i < entitlements.length(); ++i) {
                switch (entitlements.getString(i)) {
                    // These four are guaranteed to always be present because Java & Bedrock are 1 pack
                    case "product_minecraft":
                    case "game_minecraft":
                    case "product_minecraft_bedrock":
                    case "game_minecraft_bedrock":
                        doesOwnGame = true;
                        break;
                    case "product_game_pass_pc":
                    case "product_game_pass_ultimate":
                        // TODO: Implement gamepass detection
                        break;
                    // idk, pad the LoC or sm
                    case "product_dungeons":
                    case "game_dungeons":
                    case "product_legends":
                    case "game_legends":
                    default:
                        break;
                }
            }
        }
        catch (JSONException e){
            Log.w("MicrosoftLogin", "Either the Auth API was changed or this account does not own Minecraft! Assuming the latter.");
            doesOwnGame = false;
        }

    }

    private void checkMcProfile(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(mcProfileUrl);

        // For some reason, minecraftservices APIs are significantly more unreliable
        // Automatically retry because the user gets annoyed when they have to log in again
        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.setUseCaches(false);
            conn.connect();

            if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                String s= Tools.read(conn.getInputStream());
                conn.disconnect();
                Log.i("MicrosoftLogin","profile:" + s);
                JSONObject jsonObject = new JSONObject(s);
                String name = (String) jsonObject.get("name");
                String uuid = (String) jsonObject.get("id");
                String uuidDashes = uuid.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                );
                doesOwnGame = true;
                hasProfile = true;
                Log.i("MicrosoftLogin","UserName = " + name);
                Log.i("MicrosoftLogin","Uuid Minecraft = " + uuidDashes);
                mcName = name;
                mcUuid = uuidDashes;
                break;
            } else if (conn.getResponseCode() == 404){
                Log.i("MicrosoftLogin","It seems that this Microsoft Account does not have a Minecraft profile, checking for ownership.");
                hasProfile = false;
                break;
            } else if (conn.getResponseCode() == 401){
                Log.e("MicrosoftLogin", "You screwed up the auth code somewhere");
                throw getResponseThrowable(conn);
            } else if (retryCount == 4) {
                throw getResponseThrowable(conn);
            }
            try { Thread.sleep(500L * (1L << retryCount)); // 0.5s, 1s, 2s, 4s, 8s
            } catch (InterruptedException ignored) {}
        }
    }

    /** Wrapper to ease notifying the listener */
    private void notifyProgress(@Nullable ProgressListener listener, int step){
        if(listener != null){
            Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        }
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, step*20);
    }


    /** Set common properties for the connection. Given that all requests are POST, interactivity is always enabled */
    private static void setCommonProperties(HttpURLConnection conn, String formData) {
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        try {
            conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
            conn.setRequestMethod("POST");
        }catch (ProtocolException e) {
            Log.e("MicrosoftAuth", e.toString());
        }
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
    }

    /**
     * @param data A series a strings: key1, value1, key2, value2...
     * @return the data converted as a form string for a POST request
     */
    private static String convertToFormData(String... data) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<data.length; i+=2){
            if (builder.length() > 0) builder.append("&");
            builder.append(URLEncoder.encode(data[i], "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(data[i+1], "UTF-8"));
        }
        return builder.toString();
    }

    private RuntimeException getResponseThrowable(HttpURLConnection conn) throws IOException {
        Log.i("MicrosoftLogin", "Error code: " + conn.getResponseCode() + ": " + conn.getResponseMessage());
        if(conn.getResponseCode() == 429) {
            return new PresentedException(R.string.microsoft_login_retry_later);
        }
        return new RuntimeException(conn.getResponseMessage());
    }
}
