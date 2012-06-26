package com.sipgate.web.presence.client;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sipgate.web.presence.client.ssl.SimpleSSLSocketFactory;

/**
 * The Class SipgatePresenceClient.
 */
public class SipgatePresenceClient
{
	/** The Constant SIPGATE_LIVE_URL. */
	public static final String SIPGATE_LIVE_URL = "secure.live.sipgate.de";

	/** The Constant UTF_8. */
	private static final String UTF_8 = "UTF-8";

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(getClass());

	/** The list of callbacks. */
	private List<SipgatePresenceCallback> callbacks = new ArrayList<SipgatePresenceCallback>();

	/** The extension name map. */
	private HashMap<String, String> extensionNameMap = new HashMap<String, String>();

	/** The presence thread. */
	private Thread presenceThread;

	/** The current get request. */
	private HttpGet currentGetRequest = null;

	/** The http client. */
	private HttpClient httpClient;

	/** The base url. */
	private String baseUrl;

	/** The user. */
	private String username;

	/** The pass. */
	private String password;

	/** flag for stopRequest. */
	private volatile boolean stopRequested;

	/**
	 * Instantiates a new sipgate presence client.
	 *
	 * @param host
	 *            the host, e.g. see SIPGATE_LIVE_URL
	 * @param username
	 *            the username used for login
	 * @param password
	 *            the password used for login
	 */
	public SipgatePresenceClient(String host, String username, String password)
	{
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
		schemeRegistry.register(new Scheme("https", 443, new SimpleSSLSocketFactory()));
		ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);

		this.httpClient = new DefaultHttpClient(cm);

		this.baseUrl = "https://" + host;
		this.username = username;
		this.password = password;
	}

	/**
	 * Fire update event.
	 *
	 * @param json
	 *            the json
	 */
	private void fireUpdateEvent(JSONObject json)
	{
		for (SipgatePresenceCallback callback : callbacks)
		{
			if (callback != null)
			{
				callback.presenceUpdateReceived(json);
			}
		}
	}

	/**
	 * Fire exception event.
	 *
	 * @param exception
	 *            the exception
	 */
	private void fireExceptionEvent(Exception exception)
	{
		for (SipgatePresenceCallback callback : callbacks)
		{
			if (callback != null)
			{
				callback.presenceErrorOccured(exception);
			}
		}
	}

	/**
	 * Register presence update callback, which will got notified if any updates
	 * occured.
	 *
	 * @param callback
	 *            the callback
	 */
	public void registerPresenceCallback(SipgatePresenceCallback callback)
	{
		synchronized (callbacks)
		{
			callbacks.add(callback);
		}
	}

	/**
	 * Unregister presence update callback.
	 *
	 * @param callback
	 *            the callback
	 */
	public void unregisterPresenceCallback(SipgatePresenceCallback callback)
	{
		synchronized (callbacks)
		{
			callbacks.remove(callback);
		}
	}

	/**
	 * Start presence client and receive updates through callback.
	 */
	public void start()
	{
		if (presenceThread == null)
		{
			stopRequested = false;
			presenceThread = new Thread(new Runnable()
			{
				public void run()
				{
					presenceHandler();
				}
			});
			presenceThread.setName("presenceThread");
			presenceThread.start();
		}
		else
		{
			throw new RuntimeException("PresenceThread is already running");
		}
	}

	/**
	 * Stop presence client.
	 */
	public void stop()
	{
		if (presenceThread != null)
		{
			stopRequested = true;
			if (currentGetRequest != null)
			{
				currentGetRequest.abort();
			}
		}
	}

	/**
	 * Presence handler.
	 */
	protected void presenceHandler()
	{
		logger.debug("PresenceHandler start");

		String url = "";
		String entityTag = "";
		String lastModified = "";

		try
		{
			login();
			url = subscribe();

			while (!stopRequested)
			{
				logger.debug("Waiting for Response [url: '{}' | Etag: '{}' | last modified: '{}']",
						new Object[] { url, entityTag, lastModified });

				currentGetRequest = new HttpGet(baseUrl + url);
				currentGetRequest.setHeader("If-None-Match", entityTag);
				currentGetRequest.setHeader("If-Modified-Since", lastModified);

				try
				{
					HttpResponse response = httpClient.execute(currentGetRequest);
					StatusLine statusLine = response.getStatusLine();
					if (statusLine.getStatusCode() != HttpStatus.SC_NOT_MODIFIED)
					{
						String json = EntityUtils.toString(response.getEntity(), UTF_8);
						if (json.contains("expired"))
						{
							// Reset EntityTag and LastModified
							entityTag = "";
							lastModified = "";

							logger.debug("Subscription is expired, renewing...");
							url = subscribe();
						}
						else
						{
							// Set EntityTag and LastModified for next
							// Request
							entityTag = response.getHeaders("ETag")[0].getValue();
							lastModified = response.getHeaders("Last-Modified")[0].getValue();

							JSONObject jsonObject = new JSONObject(json);
							jsonObject = mergeExtensionAlias(jsonObject);
							fireUpdateEvent(jsonObject);
						}
					}

					currentGetRequest.releaseConnection();
				}
				catch (SocketException ex)
				{
				}
			}
		}
		catch (Exception e)
		{
			fireExceptionEvent(e);
		}

		presenceThread = null;
		logger.debug("PresenceHandler finished");
	}

	/**
	 * Merge extension alias.
	 *
	 * @param json
	 *            the json
	 * @return the jSON object
	 * @throws JSONException
	 *             the jSON exception
	 */
	@SuppressWarnings("unchecked")
	private JSONObject mergeExtensionAlias(JSONObject json) throws JSONException
	{
		Iterator<String> iterator = json.keys();

		while (iterator.hasNext())
		{
			String key = iterator.next();
			String[] split = key.split("\\.");
			JSONObject jsonObject = json.getJSONObject(key);
			jsonObject.put("alias", extensionNameMap.get(split[1]));
		}

		return json;
	}

	/**
	 * Refresh extension name map.
	 *
	 * @throws ClientProtocolException
	 *             the client protocol exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws JSONException
	 *             the jSON exception
	 * @throws SipgatePresenceException
	 *             the sipgate presence exception
	 */
	private void refreshExtensionNameMap() throws ClientProtocolException, IOException, JSONException,
			SipgatePresenceException
	{
		HttpGet ddHttpGet = null;

		try
		{
			logger.debug("Refreshing extensionNameMap...");
			ddHttpGet = new HttpGet(baseUrl + "/ajax-fast.php/contacts/getdirectdialcontacts");
			HttpResponse response = httpClient.execute(ddHttpGet);
			StatusLine status = response.getStatusLine();

			if (status.getStatusCode() == HttpStatus.SC_OK)
			{
				String json = EntityUtils.toString(response.getEntity(), UTF_8);

				extensionNameMap.clear();
				JSONArray jsonArray = new JSONArray(json);
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String extensionSipId = jsonObject.getString("extensionSipId");
					String alias = jsonObject.getString("alias");
					extensionNameMap.put(extensionSipId, alias);
				}
			}
			else
			{
				throw new SipgatePresenceException("Received wrong Responsecode from Web");
			}
		}
		finally
		{
			if (ddHttpGet != null)
			{
				ddHttpGet.releaseConnection();
			}
		}

		logger.debug("Refresh was successful. Count: {}", extensionNameMap.size());
	}

	/**
	 * Login.
	 *
	 * @throws ClientProtocolException
	 *             the client protocol exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws SipgatePresenceException
	 *             the sipgate presence exception
	 */
	private void login() throws ClientProtocolException, IOException, SipgatePresenceException
	{
		HttpPost loginPost = null;
		try
		{
			logger.debug("Logging in user: {}", username);
			List<NameValuePair> formparams = new ArrayList<NameValuePair>();
			formparams.add(new BasicNameValuePair("username", username));
			formparams.add(new BasicNameValuePair("password", password));
			UrlEncodedFormEntity paramEntity = new UrlEncodedFormEntity(formparams, UTF_8);

			loginPost = new HttpPost(baseUrl + "/auth/login");
			loginPost.setEntity(paramEntity);

			HttpResponse response = httpClient.execute(loginPost);
			StatusLine statusLine = response.getStatusLine();
			if (statusLine.getStatusCode() != 302)
			{
				throw new SipgatePresenceException("Not authorized");
			}
		}
		finally
		{
			if (loginPost != null)
			{
				loginPost.releaseConnection();
			}
		}

		logger.debug("Login was successful for user: {}", username);
	}

	/**
	 * Subscribe.
	 *
	 * @return the string
	 * @throws ParseException
	 *             the parse exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws JSONException
	 *             the jSON exception
	 * @throws SipgatePresenceException
	 *             the sipgate presence exception
	 */
	private String subscribe() throws ParseException, IOException, JSONException, SipgatePresenceException
	{
		String url = null;

		HttpPost httpPost = null;

		try
		{
			logger.debug("Subscribing...");
			httpPost = new HttpPost(baseUrl + "/ajax-fast.php/switchboard/subscribe");
			HttpResponse response = httpClient.execute(httpPost);
			StatusLine status = response.getStatusLine();

			if (status.getStatusCode() == HttpStatus.SC_OK)
			{
				String raw = EntityUtils.toString(response.getEntity(), UTF_8);
				JSONObject json = new JSONObject(raw);
				url = json.getString("subscriptionUri");
			}
			else
			{
				throw new SipgatePresenceException("Received wrong Responsecode from Web");
			}
		}
		finally
		{
			if (httpPost != null)
			{
				httpPost.releaseConnection();
			}
		}

		refreshExtensionNameMap();

		logger.debug("Subscribing was successful, got url: {}", url);
		return url;
	}
}
