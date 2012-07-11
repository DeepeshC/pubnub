package pubnub;


import com.tinyline.util.GZIPInputStream;
import java.io.*;
import pubnub.crypto.PubnubCrypto;


import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

import javax.microedition.io.HttpsConnection;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.json.me.JSONArray;
import org.json.me.JSONException;
import org.json.me.JSONObject;
import pubnub.util.AsyncHttpManager;
import pubnub.util.HttpCallback;

public class Pubnub {

    private String ORIGIN = "pubsub.pubnub.com";
    private String PUBLISH_KEY = "";
    private String SUBSCRIBE_KEY = "";
    private String SECRET_KEY = "";
    private String CIPHER_KEY = "";
    private boolean SSL = false;
    private Callback _callback = null;
    private String current_timetoken = "0";

    private class ChannelStatus {

        String channel;
        boolean connected, first;
    }
    private Vector subscriptions;

    /**
     * PubNub 3.1 with Cipher Key
     *
     * Prepare PubNub State.
     *
     * @param String Publish Key.
     * @param String Subscribe Key.
     * @param String Secret Key.
     * @param String Cipher Key.
     * @param boolean SSL Enabled.
     */
    public Pubnub(String publish_key, String subscribe_key, String secret_key,
            String cipher_key, boolean ssl_on) {
        this.init(publish_key, subscribe_key, secret_key, cipher_key, ssl_on);
    }

    /**
     * PubNub 3.0 with SSL
     *
     * Prepare PubNub Class State.
     *
     * @param String Publish Key.
     * @param String Subscribe Key.
     * @param String Secret Key.
     * @param boolean SSL Enabled.
     */
    public Pubnub(String publish_key, String subscribe_key, String secret_key,
            boolean ssl_on) {
        this.init(publish_key, subscribe_key, secret_key, "", ssl_on);
    }

    /**
     * PubNub 2.0 without Secret Key and SSL
     *
     * Prepare PubNub Class State.
     *
     * @param String Publish Key.
     * @param String Subscribe Key.
     */
    public Pubnub(String publish_key, String subscribe_key) {
        this.init(publish_key, subscribe_key, "", "", false);
    }

    /**
     * PubNub 3.0 without SSL
     *
     * Prepare PubNub Class State.
     *
     * @param String Publish Key.
     * @param String Subscribe Key.
     * @param String Secret Key.
     */
    public Pubnub(String publish_key, String subscribe_key, String secret_key) {
        this.init(publish_key, subscribe_key, secret_key, "", false);
    }

    /**
     * Init
     *
     * Prepare PubNub Class State.
     *
     * @param String Publish Key.
     * @param String Subscribe Key.
     * @param String Secret Key.
     * @param String Cipher Key.
     * @param boolean SSL Enabled.
     */
    public void init(String publish_key, String subscribe_key,
            String secret_key, String cipher_key, boolean ssl_on) {
        this.PUBLISH_KEY = publish_key;
        this.SUBSCRIBE_KEY = subscribe_key;
        this.SECRET_KEY = secret_key;
        this.CIPHER_KEY = cipher_key;
        this.SSL = ssl_on;

        // SSL On?
        if (this.SSL) {
            this.ORIGIN = "https://" + this.ORIGIN;
        } else {
            this.ORIGIN = "http://" + this.ORIGIN;
        }
    }

    /**
     * getCallback
     *
     * get the callback.
     *
     * @return Callback.
     */
    public Callback getCallback() {
        return _callback;
    }

    /**
     * setCallback
     *
     * set Callback Interface.
     *
     * @param Callback Callback object.
     */
    public void setCallback(Callback _callback) {
        this._callback = _callback;
    }

    /**
     * Publish
     *
     * Send a message to a channel.
     *
     * @param String channel name.
     * @param JSONObject message.
     * @return JSONArray.
     */
    public void publish(String channel, JSONObject message) {
        Hashtable args = new Hashtable(2);
        args.put("channel", channel);
        args.put("message", message);
        publish(args);
    }

    /**
     * Publish
     *
     * Send a message to a channel.
     *
     * @param HashMap <String, Object> containing channel name, message.
     * @return JSONArray.
     */
    public void publish(Hashtable args) {

        String channel = (String) args.get("channel");
        Object message = args.get("message");

        if (message instanceof JSONObject) {
            JSONObject obj = (JSONObject) message;
            if (this.CIPHER_KEY.length() > 0) {
                // Encrypt Message
                PubnubCrypto pc = new PubnubCrypto(this.CIPHER_KEY);
                message = pc.encrypt(obj);
            } else {
                message = obj;
            }
        } else if (message instanceof String) {
            String obj = (String) message;
            if (this.CIPHER_KEY.length() > 0) {
                // Encrypt Message
                PubnubCrypto pc = new PubnubCrypto(this.CIPHER_KEY);
                try {
                    message = pc.encrypt(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                message = obj;
            }
            message = "\"" + message + "\"";

        } else if (message instanceof JSONArray) {
            JSONArray obj = (JSONArray) message;

            if (this.CIPHER_KEY.length() > 0) {
                // Encrypt Message
                PubnubCrypto pc = new PubnubCrypto(this.CIPHER_KEY);
                message = pc.encryptJSONArray(obj);
            } else {
                message = obj;
            }
            System.out.println();
        }

        // Generate String to Sign
        String signature = "0";

        if (this.SECRET_KEY.length() > 0) {
            StringBuffer string_to_sign = new StringBuffer();
            string_to_sign.append(this.PUBLISH_KEY).append('/').append(this.SUBSCRIBE_KEY).append('/').append(this.SECRET_KEY).append('/').append(channel).append('/').append(message.toString());

            // Sign Message
            signature = PubnubCrypto.getHMacSHA256(this.SECRET_KEY,
                    string_to_sign.toString());
        }

        // Build URL
        Vector url = new Vector();
        url.addElement("publish");
        url.addElement(this.PUBLISH_KEY);
        url.addElement(this.SUBSCRIBE_KEY);
        url.addElement(signature);
        url.addElement(channel);
        url.addElement("0");
        url.addElement(message.toString());

        _request(url, channel);
    }

    /**
     * Subscribe
     *
     * Listen for a message on a channel.
     *
     * @param HashMap <String, Object> containing channel name, function
     * callback.
     */
    public void subscribe(final Hashtable args) {
        args.put("timetoken", "0");
        _subscribe(args);


    }

    private void _subscribe_base(Hashtable args) {
        String channel = (String) args.get("channel");
        String timetoken = (String) args.get("timetoken");
        try {
            // Build URL
            Vector url = new Vector();
            url.addElement("subscribe");
            url.addElement(this.SUBSCRIBE_KEY);
            url.addElement(channel);
            url.addElement("0");
            url.addElement(timetoken);

            // Stop Connection?
            boolean is_disconnect = false;
            ChannelStatus it;
            for (int i = 0; i < subscriptions.size(); i++) {
                it = (ChannelStatus) subscriptions.elementAt(i);
                if (it.channel.equals(channel)) {
                    if (!it.connected) {
                        if (_callback != null) {
                            _callback.disconnectCallback(channel);
                        }

                        is_disconnect = true;
                        break;
                    }
                }
            }
            if (is_disconnect) {
                return;
            }

            _request(url, channel);


        } catch (Exception e) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
            }
        }
    }

    /**
     * Subscribe - Private Interface
     *
     * Patch provided by petereddy on GitHub
     *
     * @param HashMap <String, Object> containing channel name, function
     * callback, timetoken.
     */
    private void _subscribe(Hashtable args) {

        String channel = (String) args.get("channel");
        String timetoken = (String) args.get("timetoken");
        // Ensure Single Connection
        if (subscriptions != null && subscriptions.size() > 0) {
            boolean channel_exist = false;
            ChannelStatus it;
            for (int i = 0; i < subscriptions.size(); i++) {
                it = (ChannelStatus) subscriptions.elementAt(i);
                if (it.channel.equals(channel)) {
                    channel_exist = true;
                    break;
                }
            }
            if (!channel_exist) {
                ChannelStatus cs = new ChannelStatus();
                cs.channel = channel;
                cs.connected = true;
                subscriptions.addElement(cs);
            } else {
                if (_callback != null) {
                    JSONArray jsono = new JSONArray();
                    try {
                        jsono.put("Already Connected.");
                    } catch (Exception jsone) {
                    }
                    _callback.errorCallback(channel, jsono);
                }
                // return;
            }
        } else {
            // New Channel
            ChannelStatus cs = new ChannelStatus();
            cs.channel = channel;
            cs.connected = true;
            subscriptions = new Vector();
            subscriptions.addElement(cs);
        }
        _subscribe_base(args);

    }

    /**
     * Time
     *
     * Timestamp from PubNub Cloud.
     *
     * @return double timestamp.
     */
    public double time() {
        try {
            Vector url = new Vector();

            url.addElement("time");
            url.addElement("0");

            String res = getViaHttpsConnection(getURL(url));
            JSONArray response = new JSONArray(res);
            try {
                return (Double.parseDouble(response.get(0).toString()));
            } catch (JSONException ex) {
                ex.printStackTrace();
            }

        } catch (JSONException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ServiceProviderException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * UUID
     *
     * 32 digit UUID generation at client side.
     *
     * @return String uuid.
     */
    public static String uuid() {

        int count, length = 32;
        Random random = new Random();
        StringBuffer buf = new StringBuffer();
        for (count = 0; count < length;) {
            buf.append((char) ((random.nextInt(9) % 26) + 97)).append("");
            buf.append((char) ((random.nextInt(9) % 10) + 48)).append("");
            if (buf.length() < 32) {
                buf.append((char) ((random.nextInt(9) % 26) + 65)).append("");
            }
            count = buf.length();
        }
        return buf.toString();
    }

    /**
     * History
     *
     * Load history from a channel.
     *
     * @param String channel name.
     * @param int limit history count response.
     * @return JSONArray of history.
     */
    public void history(String channel, int limit) {
        Hashtable args = new Hashtable(2);
        args.put("channel", channel);
        args.put("limit", new Integer(limit));
        history(args);
    }

    /**
     * History
     *
     * Load history from a channel.
     *
     * @param HashMap <String, Object> containing channel name, limit history
     * count response.
     * @return JSONArray of history.
     */
    public void history(Hashtable args) {

        String channel = (String) args.get("channel");
        Integer limit = (Integer) args.get("limit");

        Vector url = new Vector();
        url.addElement("history");
        url.addElement(this.SUBSCRIBE_KEY);
        url.addElement(channel);
        url.addElement("0");
        url.addElement(limit.toString());

        _request(url, channel);
    }

    /**
     * Unsubscribe
     *
     * Unsubscribe/Disconnect to channel.
     *
     * @param HashMap <String, Object> containing channel name.
     */
    public void unsubscribe(Hashtable args) {
        String channel = (String) args.get("channel");
        ChannelStatus it;
        for (int i = 0; i < subscriptions.size(); i++) {
            it = (ChannelStatus) subscriptions.elementAt(i);
            if (it.channel.equals(channel) && it.connected) {
                it.connected = false;
                it.first = false;
                break;
            }
        }
    }

    private String getURL(Vector url_components) {
        StringBuffer url = new StringBuffer();
        Enumeration url_iterator = url_components.elements();
        url.append(this.ORIGIN);

        // Generate URL with UTF-8 Encoding
        while (url_iterator.hasMoreElements()) {
            try {
                String url_bit = (String) url_iterator.nextElement();
                // url.append("/").append(_encodeURIcomponent(url_bit));
                url.append("/").append(encode(url_bit, "UTF-8"));
            } catch (Exception e) {
                // e.printStackTrace();
                JSONArray jsono = new JSONArray();
                try {
                    jsono.put("Failed UTF-8 Encoding URL.");
                } catch (Exception jsone) {
                }
                //  return jsono;
                if (_callback != null) {
                    _callback.errorCallback("No Channel Neme", jsono);
                }
            }
        }
        return url.toString();
    }

    /**
     * Request URL
     *
     * @param List <String> request of url directories.
     * @return JSONArray from JSON response.
     */
    private void _request(Vector url_components, final String channel) {

        String request_for = (String) url_components.elementAt(0);

        if (request_for.equals("subscribe")) {
            current_timetoken = (String) url_components.elementAt(4);
        }
        String url = getURL(url_components);
        Hashtable _headers = new Hashtable();
        _headers.put("V", "3.1");
        _headers.put("User-Agent", "J2ME");
        _headers.put("Accept-Encoding", "gzip");

        

        HttpCallback callback = new HttpCallback(url.toString(), _headers, request_for) {

            public void processResponse(HttpConnection conn, Object cookie) throws IOException {
               
            }

            public void OnComplet(HttpConnection hc, String response, String req_for) throws IOException {
                try {
                    String timetoken = "0";
                    //response=response.replace('+', ' ');
                   
                    JSONArray out = null;
                    if (response != null) {
                        out = new JSONArray(response);
                    } else {
                        out = null;
                    }
                    if (_callback != null) {
                        if (req_for != null) {
                            if (req_for.equals("time")) {
                            } else if (req_for.equals("history")) {

                                if (CIPHER_KEY.length() > 0) {
                                    try {
                                        // Decrpyt Messages
                                        PubnubCrypto pc = new PubnubCrypto(CIPHER_KEY);
                                        out = pc.decryptJSONArray(out);
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                }
                                if (_callback != null) {
                                    _callback.historyCallback(channel, out);
                                }

                            } else if (req_for.equals("publish")) {

                                if (out == null) {
                                    JSONArray arr = new JSONArray();
                                    arr.put("0");
                                    arr.put("Error: Failed JSONP HTTP Request.");
                                    out = arr;
                                }
                                _callback.publishCallback(channel, out);
                            } else if (req_for.equals("subscribe")) {

                                ChannelStatus it;
                                boolean is_disconnect = false;
                                // Stop Connection?
                                for (int i = 0; i < subscriptions.size(); i++) {
                                    it = (ChannelStatus) subscriptions.elementAt(i);
                                    if (it.channel.equals(channel)) {
                                        if (!it.connected) {
                                            if (_callback != null) {
                                                _callback.disconnectCallback(channel);
                                            }
                                            is_disconnect = true;
                                            break;
                                        }
                                    }
                                }

                                if (is_disconnect) {
                                    return;
                                }


                                // Problem?
                                if (response == null || out.optInt(1) == 0) {

                                    for (int i = 0; i < subscriptions.size(); i++) {
                                        it = (ChannelStatus) subscriptions.elementAt(i);
                                        if (it.channel.equals(channel)) {
                                            subscriptions.removeElement(it);
                                            if (_callback != null) {
                                                _callback.disconnectCallback(channel);
                                            }
                                        }
                                    }
                                    // Ensure Connected (Call Time Function)
                                    boolean is_reconnected = false;
                                    while (true) {
                                        double time_token = time();
                                        if (time_token == 0) {
                                            // Reconnect Callback
                                            if (_callback != null) {
                                                _callback.reconnectCallback(channel);
                                            }
                                            Thread.sleep(5000);
                                        } else {
                                            Hashtable args = new Hashtable();
                                            args.put("channel", channel);

                                            if (current_timetoken.equals("0")) {
                                                args.put("timetoken", time_token + "");
                                            } else {
                                                args.put("timetoken", current_timetoken + "");

                                            }

                                            _subscribe(args);
                                            is_reconnected = true;
                                            break;
                                        }
                                    }
                                } else {

                                    for (int i = 0; i < subscriptions.size(); i++) {
                                        it = (ChannelStatus) subscriptions.elementAt(i);
                                        if (it.channel.equals(channel)) {
                                            // Connect Callback
                                            if (!it.first) {
                                                it.first = true;

                                                if (_callback != null) {
                                                    _callback.connectCallback(channel);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }

                                JSONArray messages = out.optJSONArray(0);


                                // Update TimeToken
                                if (out.optString(1).length() > 0) {
                                    timetoken = out.optString(1);
                                }

                                for (int i = 0; messages.length() > i; i++) {
                                    JSONObject message = messages.optJSONObject(i);
                                    if (message != null) {

                                        if (CIPHER_KEY.length() > 0) {
                                            // Decrypt Message
                                            PubnubCrypto pc = new PubnubCrypto(CIPHER_KEY);
                                            message = pc.decrypt(message);
                                        }
                                        if (_callback != null) {
                                            _callback.subscribeCallback(channel, message);
                                        }
                                    } else {
                                        JSONArray arr = messages.optJSONArray(i);
                                        if (arr != null) {
                                            if (CIPHER_KEY.length() > 0) {
                                                PubnubCrypto pc = new PubnubCrypto(
                                                        CIPHER_KEY);
                                                arr = pc.decryptJSONArray(arr);
                                            }
                                            if (_callback != null) {
                                                _callback.subscribeCallback(channel, arr);
                                            }
                                        } else {
                                            String msgs = messages.getString(0);
                                            if (CIPHER_KEY.length() > 0) {
                                                PubnubCrypto pc = new PubnubCrypto(
                                                        CIPHER_KEY);
                                                msgs = pc.decrypt(msgs);
                                            }
                                            if (_callback != null) {
                                                _callback.subscribeCallback(channel, msgs);
                                            }
                                        }
                                    }
                                }


                                Hashtable args = new Hashtable();
                                args.put("channel", channel);
                                args.put("timetoken", timetoken + "");
                                _subscribe_base(args);

                            }
                        }
                    }
                } catch (ShortBufferException ex) {
                    ex.printStackTrace();
                } catch (IllegalBlockSizeException ex) {
                    ex.printStackTrace();
                } catch (BadPaddingException ex) {
                    ex.printStackTrace();
                } catch (DataLengthException ex) {
                    ex.printStackTrace();
                } catch (IllegalStateException ex) {
                    ex.printStackTrace();
                } catch (InvalidCipherTextException ex) {
                    ex.printStackTrace();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (JSONException ex) {
                    ex.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        AsyncHttpManager.getInstance().queue(callback);
    }

    private char toHex(int ch) {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    public final String getViaHttpsConnection(String url)
            throws IOException, ServiceProviderException {
        HttpConnection c = null;
        InputStream dis = null;
        OutputStream os = null;
        int rc;
        String respBody = new String(""); // return empty string on bad things
        try {
            if (SSL) {
                c = (HttpsConnection) Connector.open(url, Connector.READ_WRITE,
                        false);
            } else {
                c = (HttpConnection) Connector.open(url, Connector.READ_WRITE,
                        false);
            }
            c.setRequestMethod(HttpConnection.GET);
            c.setRequestProperty("V", "3.1");
            c.setRequestProperty("User-Agent", "Java");
            c.setRequestProperty("Accept-Encoding", "gzip");
            rc = c.getResponseCode();
            int len = c.getHeaderFieldInt("Content-Length", 0);
            dis = c.openInputStream();

            if ("gzip".equals(c.getEncoding())) 
                    dis = new GZIPInputStream(dis);
            
            byte[] data = null;
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            int ch;
            while ((ch = dis.read()) != -1) {
                tmp.write(ch);
            }
            data = tmp.toByteArray();
            respBody = new String(data, "UTF-8");
           
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Not an HTTP URL");
        } finally {
            if (dis != null) {
                dis.close();
            }
            if (c != null) {
                c.close();
            }
        }
        if (rc != HttpConnection.HTTP_OK) {
            throw new ServiceProviderException(
                    "HTTP response code: " + rc, rc, respBody);
        }
        return respBody;
    }

    public String encode(String s, String enc)
            throws UnsupportedEncodingException {

        boolean needToChange = false;
        boolean wroteUnencodedChar = false;
        int maxBytesPerChar = 10; // rather arbitrary limit, but safe for now
        StringBuffer out = new StringBuffer(s.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);

        OutputStreamWriter writer = new OutputStreamWriter(buf, enc);

        for (int i = 0; i < s.length(); i++) {
            int c = (int) s.charAt(i);
            //System.out.println("Examining character: " + c);
            if (dontNeedEncoding(c)) {
                if (c == ' ') {
                    out.append('%');
                    out.append(toHex(c / 16));
                    out.append(toHex(c % 16));

                    needToChange = true;
                } else {
                    //System.out.println("Storing: " + c);
                    out.append((char) c);
                    wroteUnencodedChar = true;
                }
            } else {
                // convert to external encoding before hex conversion
                try {
                    if (wroteUnencodedChar) { // Fix for 4407610
                        writer = new OutputStreamWriter(buf, enc);
                        wroteUnencodedChar = false;
                    }
                    writer.write(c);
                    /*
                     * If this character represents the start of a Unicode
                     * surrogate pair, then pass in two characters. It's not
                     * clear what should be done if a bytes reserved in the
                     * surrogate pairs range occurs outside of a legal surrogate
                     * pair. For now, just treat it as if it were any other
                     * character.
                     */
                    if (c >= 0xD800 && c <= 0xDBFF) {
                        /*
                         * System.out.println(Integer.toHexString(c) + " is high
                         * surrogate");
                         */
                        if ((i + 1) < s.length()) {
                            int d = (int) s.charAt(i + 1);
                            /*
                             * System.out.println("\tExamining " +
                             * Integer.toHexString(d));
                             */
                            if (d >= 0xDC00 && d <= 0xDFFF) {
                                /*
                                 * System.out.println("\t" +
                                 * Integer.toHexString(d) + " is low
                                 * surrogate");
                                 */
                                writer.write(d);
                                i++;
                            }
                        }
                    }
                    writer.flush();
                } catch (IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (int j = 0; j < ba.length; j++) {
                    out.append('%');
                    char ch = CCharacter.forDigit((ba[j] >> 4) & 0xF, 16);
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    //            if (Character.isLetter(ch)) {
                    //            ch -= caseDiff;
                    //            }
                    out.append(ch);
                    ch = CCharacter.forDigit(ba[j] & 0xF, 16);
                    //            if (Character.isLetter(ch)) {
                    //            ch -= caseDiff;
                    //            }
                    out.append(ch);
                }
                buf.reset();
                needToChange = true;
            }
        }

        return (needToChange ? out.toString() : s);
    }

    static class CCharacter {

        public static char forDigit(int digit, int radix) {
            if ((digit >= radix) || (digit < 0)) {
                return '\0';
            }
            if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX)) {
                return '\0';
            }
            if (digit < 10) {
                return (char) ('0' + digit);
            }
            return (char) ('a' - 10 + digit);
        }
    }

    public static boolean dontNeedEncoding(int ch) {
        int len = _dontNeedEncoding.length();
        boolean en = false;
        for (int i = 0; i < len; i++) {
            if (_dontNeedEncoding.charAt(i) == ch) {
                en = true;
                break;
            }
        }

        return en;
    }
    //private static final int caseDiff = ('a' - 'A');
    private static String _dontNeedEncoding = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ -_.*";
}
